// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.codeowners.backend.findowners;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParseException;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.server.git.ValidationError;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Parser and formatter for the syntax that is used to store {@link CodeOwnerConfig}s in {@code
 * OWNERS} files as they are used by the {@code find-owners} plugin.
 *
 * <p>The syntax is described at in the {@code find-owners} plugin documentation at:
 * https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/resources/Documentation/syntax.md
 *
 * <p>Comment lines are silently ignored.
 *
 * <p>Invalid lines cause the parsing to fail and trigger a {@link CodeOwnerConfigParseException}.
 *
 * <p>Comments can appear as separate lines and as appendix for email lines (e.g. using
 * 'foo.bar@example.com # Foo Bar' would be a valid email line).
 *
 * <p>Most of the code in this class was copied from the {@code
 * com.googlesource.gerrit.plugins.findowners.Parser} class from the {@code find-owners} plugin. The
 * original parsing code is used to be as backwards-compatible as possible and to avoid spending
 * time on reimplementing a parser for a deprecated syntax. We have only done a minimal amount of
 * adaption so that the parser produces a {@link CodeOwnerConfig} as result, instead of the
 * abstraction that is used in the {@code find-owners} plugin.
 */
@Singleton
@VisibleForTesting
public class FindOwnersCodeOwnerConfigParser implements CodeOwnerConfigParser {
  // Artifical owner token for "set noparent" when used in per-file.
  private static final String TOK_SET_NOPARENT = "set noparent";

  /**
   * Any Unicode linebreak sequence, is equivalent to {@code
   * \u000D\u000A|[\u000A\u000B\u000C\u000D\u0085\u2028\u2029]}.
   */
  private static final String LINEBREAK_MATCHER = "\\R";

  @Override
  public CodeOwnerConfig parse(
      ObjectId revision, CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString)
      throws CodeOwnerConfigParseException {
    requireNonNull(revision, "revision");
    requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey");

    Parser parser = new Parser();
    CodeOwnerConfig codeOwnerConfig =
        parser.parse(revision, codeOwnerConfigKey, Strings.nullToEmpty(codeOwnerConfigAsString));
    if (!parser.getValidationErrors().isEmpty()) {
      throw new CodeOwnerConfigParseException(codeOwnerConfigKey, parser.getValidationErrors());
    }
    return codeOwnerConfig;
  }

  @Override
  public String formatAsString(CodeOwnerConfig codeOwnerConfig) {
    return Formatter.formatAsString(requireNonNull(codeOwnerConfig, "codeOwnerConfig"));
  }

  public static String replaceEmail(
      String codeOwnerConfigFileContent, String oldEmail, String newEmail) {
    requireNonNull(codeOwnerConfigFileContent, "codeOwnerConfigFileContent");
    requireNonNull(oldEmail, "oldEmail");
    requireNonNull(newEmail, "newEmail");

    String charsThatCanAppearBeforeOrAfterEmail = "[\\s=,#]";
    Pattern pattern =
        Pattern.compile(
            "(^|.*"
                + charsThatCanAppearBeforeOrAfterEmail
                + "+)"
                + "("
                + Pattern.quote(oldEmail)
                + ")"
                + "($|"
                + charsThatCanAppearBeforeOrAfterEmail
                + "+.*)");

    List<String> updatedLines = new ArrayList<>();
    for (String line : Splitter.onPattern(LINEBREAK_MATCHER).split(codeOwnerConfigFileContent)) {
      while (pattern.matcher(line).matches()) {
        line = pattern.matcher(line).replaceFirst("$1" + newEmail + "$3");
      }
      updatedLines.add(line);
    }
    return Joiner.on("\n").join(updatedLines);
  }

  @VisibleForTesting
  static class Parser implements ValidationError.Sink {
    private static final String COMMA = "[\\s]*,[\\s]*";

    // Separator for project and file paths in an include line.
    private static final String COLON = "[\\s]*:[\\s]*"; // project:file

    private static final String BOL = "^[\\s]*"; // begin-of-line
    private static final String EOL = "[\\s]*(#.*)?$"; // end-of-line
    private static final String GLOB = "[^\\s,=]+"; // a file glob

    private static final String EMAIL_OR_STAR = "([^\\s<>@,]+@[^\\s<>@#,]+|\\*)";
    private static final String EMAIL_LIST =
        "(" + EMAIL_OR_STAR + "(" + COMMA + EMAIL_OR_STAR + ")*)";

    // Optional name of a Gerrit project followed by a colon and optional spaces.
    private static final String PROJECT_NAME = "([^\\s:]+" + COLON + ")?";

    // Optional name of a branch followed by a colon and optional spaces.
    private static final String BRANCH_NAME = "([^\\s:]+" + COLON + ")?";

    // A relative or absolute file path name without any colon or space character.
    private static final String FILE_PATH = "([^\\s:#]+)";

    private static final String PROJECT_BRANCH_AND_FILE = PROJECT_NAME + BRANCH_NAME + FILE_PATH;

    private static final String SET_NOPARENT = "set[\\s]+noparent";

    private static final String FILE_DIRECTIVE = "file:[\\s]*" + PROJECT_BRANCH_AND_FILE;
    private static final String INCLUDE_DIRECTIVE = "include[\\s]+" + PROJECT_BRANCH_AND_FILE;
    private static final String INCLUDE_OR_FILE = "(file:[\\s]*|include[\\s]+)";

    // Simple input lines with 0 or 1 sub-pattern.
    private static final Pattern PAT_COMMENT = Pattern.compile(BOL + EOL);
    private static final Pattern PAT_EMAIL = Pattern.compile(BOL + EMAIL_OR_STAR + EOL);
    private static final Pattern PAT_ANNOTATION = Pattern.compile("#\\{([A-Za-z_]+)\\}");
    private static final Pattern PAT_INCLUDE =
        Pattern.compile(BOL + INCLUDE_OR_FILE + PROJECT_BRANCH_AND_FILE + EOL);
    private static final Pattern PAT_NO_PARENT = Pattern.compile(BOL + SET_NOPARENT + EOL);

    private static final Pattern PAT_PER_FILE_OWNERS =
        Pattern.compile("^(" + EMAIL_LIST + "|" + SET_NOPARENT + "|" + FILE_DIRECTIVE + ")$");
    private static final Pattern PAT_PER_FILE_INCLUDE =
        Pattern.compile("^(" + INCLUDE_DIRECTIVE + ")$");
    private static final Pattern PAT_GLOBS =
        Pattern.compile("^(" + GLOB + "(" + COMMA + GLOB + ")*)$");

    // PAT_PER_FILE matches a line to two groups: (1) globs, (2) emails
    // Trimmed 1st group should match PAT_GLOBS;
    // trimmed 2nd group should match PAT_PER_FILE_OWNERS.
    private static final Pattern PAT_PER_FILE =
        Pattern.compile(BOL + "per-file[\\s]+([^=#]+)=[\\s]*([^#]+)" + EOL);

    private List<ValidationError> validationErrors;

    CodeOwnerConfig parse(
        ObjectId revision, CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) {
      CodeOwnerConfig.Builder codeOwnerConfigBuilder =
          CodeOwnerConfig.builder(codeOwnerConfigKey, revision);
      CodeOwnerSet.Builder globalCodeOwnerSetBuilder = CodeOwnerSet.builder();
      List<CodeOwnerSet> perFileCodeOwnerSet = new ArrayList<>();

      for (String line : Splitter.onPattern(LINEBREAK_MATCHER).split(codeOwnerConfigAsString)) {
        parseLine(codeOwnerConfigBuilder, globalCodeOwnerSetBuilder, perFileCodeOwnerSet, line);
      }

      // Make the code owners sets with the global code owners the first one in the list.
      CodeOwnerSet globalCodeOwnersSet = globalCodeOwnerSetBuilder.build();
      if (!globalCodeOwnersSet.codeOwners().isEmpty()) {
        codeOwnerConfigBuilder.addCodeOwnerSet(globalCodeOwnersSet);
      }
      perFileCodeOwnerSet.forEach(codeOwnerConfigBuilder::addCodeOwnerSet);

      return codeOwnerConfigBuilder.build();
    }

    private void parseLine(
        CodeOwnerConfig.Builder codeOwnerConfigBuilder,
        CodeOwnerSet.Builder globalCodeOwnerSetBuilder,
        List<CodeOwnerSet> perFileCodeOwnerSets,
        String line) {
      ParsedEmailLine parsedEmailLine;
      CodeOwnerSet codeOwnerSet;
      CodeOwnerConfigReference codeOwnerConfigReference;
      if (isNoParent(line)) {
        codeOwnerConfigBuilder.setIgnoreParentCodeOwners();
      } else if (isComment(line)) {
        // ignore comment lines and empty lines
      } else if ((parsedEmailLine = parseEmailLine(line)) != null) {
        globalCodeOwnerSetBuilder.addCodeOwner(parsedEmailLine.codeOwnerReference());
        globalCodeOwnerSetBuilder.addAnnotations(
            parsedEmailLine.codeOwnerReference(), parsedEmailLine.annotations());
      } else if ((codeOwnerSet = parsePerFile(line)) != null) {
        perFileCodeOwnerSets.add(codeOwnerSet);
      } else if ((codeOwnerConfigReference = parseInclude(line)) != null) {
        codeOwnerConfigBuilder.addImport(codeOwnerConfigReference);
      } else {
        error(ValidationError.create(String.format("invalid line: %s", line)));
      }
    }

    private static CodeOwnerSet parsePerFile(String line) {
      Matcher m = PAT_PER_FILE.matcher(line);
      if (!m.matches() || !isGlobs(m.group(1).trim())) {
        return null;
      }

      String matchedGroup2 = m.group(2).trim();
      if (!PAT_PER_FILE_OWNERS.matcher(matchedGroup2).matches()) {
        checkState(
            !PAT_PER_FILE_INCLUDE.matcher(matchedGroup2).matches(),
            "import mode %s is unsupported for per file import: %s",
            CodeOwnerConfigImportMode.ALL.name(),
            line);
        return null;
      }

      String[] globsAndOwners =
          new String[] {removeExtraSpaces(m.group(1)), removeExtraSpaces(m.group(2))};
      String[] dirGlobs = splitGlobs(globsAndOwners[0]);
      String directive = globsAndOwners[1];
      if (directive.equals(TOK_SET_NOPARENT)) {
        return CodeOwnerSet.builder()
            .setIgnoreGlobalAndParentCodeOwners()
            .setPathExpressions(ImmutableSet.copyOf(dirGlobs))
            .build();
      }

      CodeOwnerConfigReference codeOwnerConfigReference;
      if ((codeOwnerConfigReference = parseInclude(directive)) != null) {
        return CodeOwnerSet.builder()
            .addImport(codeOwnerConfigReference)
            .setPathExpressions(ImmutableSet.copyOf(dirGlobs))
            .build();
      }

      List<String> ownerEmails = Arrays.asList(directive.split(COMMA, -1));

      return CodeOwnerSet.builder()
          .setPathExpressions(ImmutableSet.copyOf(dirGlobs))
          .setCodeOwners(
              ownerEmails.stream().map(CodeOwnerReference::create).collect(toImmutableSet()))
          .build();
    }

    /**
     * Splits the given glob string by the commas that separate the globs.
     *
     * <p>Commas that appear within a glob do not cause the string to be split at this position:
     *
     * <ul>
     *   <li>commas that are used as separator when matching choices via {@code {choice1,choice2}}
     *   <li>commas that appears as part of a character class via {@code
     *       [<any-chars-including-comma>]}
     * </ul>
     *
     * @param commaSeparatedGlobs globs as comma-separated list
     * @return the globs as array
     */
    @VisibleForTesting
    static String[] splitGlobs(String commaSeparatedGlobs) {
      ArrayList<String> globList = new ArrayList<>();
      StringBuilder nextGlob = new StringBuilder();
      int curlyBracesIndentionLevel = 0;
      int squareBracesIndentionLevel = 0;
      for (int i = 0; i < commaSeparatedGlobs.length(); i++) {
        char c = commaSeparatedGlobs.charAt(i);
        if (c == ',') {
          if (curlyBracesIndentionLevel == 0 && squareBracesIndentionLevel == 0) {
            globList.add(nextGlob.toString());
            nextGlob = new StringBuilder();
          } else {
            nextGlob.append(c);
          }
        } else {
          nextGlob.append(c);
          if (c == '{') {
            curlyBracesIndentionLevel++;
          } else if (c == '}') {
            if (curlyBracesIndentionLevel > 0) {
              curlyBracesIndentionLevel--;
            }
          } else if (c == '[') {
            squareBracesIndentionLevel++;
          } else if (c == ']') {
            if (squareBracesIndentionLevel > 0) {
              squareBracesIndentionLevel--;
            }
          }
        }
      }
      if (nextGlob.length() > 0) {
        globList.add(nextGlob.toString());
      }
      return globList.toArray(new String[globList.size()]);
    }

    private static boolean isComment(String line) {
      return PAT_COMMENT.matcher(line).matches();
    }

    private static boolean isNoParent(String line) {
      return PAT_NO_PARENT.matcher(line).matches();
    }

    private static ParsedEmailLine parseEmailLine(String line) {
      Matcher emailMatcher = PAT_EMAIL.matcher(line);
      if (!emailMatcher.matches()) {
        return null;
      }
      String email = emailMatcher.group(1).trim();
      ParsedEmailLine.Builder parsedEmailLine = ParsedEmailLine.builder(email);

      // Get the comment part of the line (the first '#' and everything that follows).
      String comment = emailMatcher.group(2);
      if (comment != null) {
        Matcher annotationMatcher = PAT_ANNOTATION.matcher(comment);
        while (annotationMatcher.find()) {
          String annotation = annotationMatcher.group(1);
          parsedEmailLine.addAnnotation(annotation);
        }
      }

      return parsedEmailLine.build();
    }

    private static CodeOwnerConfigReference parseInclude(String line) {
      Matcher m = Parser.PAT_INCLUDE.matcher(line);
      if (!m.matches()) {
        return null;
      }

      String keyword = m.group(1).trim();
      CodeOwnerConfigImportMode importMode =
          keyword.equals("include")
              ? CodeOwnerConfigImportMode.ALL
              : CodeOwnerConfigImportMode.GLOBAL_CODE_OWNER_SETS_ONLY;

      CodeOwnerConfigReference.Builder builder =
          CodeOwnerConfigReference.builder(importMode, m.group(4).trim());

      String projectName = m.group(2);
      if (projectName != null && projectName.length() > 1) {
        // PROJECT_NAME ends with ':'
        projectName = projectName.split(COLON, -1)[0].trim();
        builder.setProject(Project.nameKey(projectName));

        String branchName = m.group(3);
        if (branchName != null && branchName.length() > 1) {
          // BRANCH_NAME ends with ':'
          branchName = branchName.split(COLON, -1)[0].trim();
          builder.setBranch(branchName);
        }
      }

      return builder.build();
    }

    private static boolean isGlobs(String line) {
      return PAT_GLOBS.matcher(line).matches();
    }

    private static String removeExtraSpaces(String s) {
      return s.trim().replaceAll("[\\s]+", " ").replaceAll("[\\s]*:[\\s]*", ":");
    }

    /**
     * Get the validation errors, if any were discovered during parsing the code owner config file.
     *
     * @return list of errors; empty list if there are no errors.
     */
    public ImmutableList<ValidationError> getValidationErrors() {
      if (validationErrors != null) {
        return ImmutableList.copyOf(validationErrors);
      }
      return ImmutableList.of();
    }

    @Override
    public void error(ValidationError error) {
      if (validationErrors == null) {
        validationErrors = new ArrayList<>(4);
      }
      validationErrors.add(error);
    }
  }

  private static class Formatter {
    private static final String SET_NOPARENT_LINE = "set noparent\n";

    // String format for a "per-file" line. The first placeholder is for the comma-separated list of
    // path expressions, the second placeholder is for the comma-separated list of emails.
    private static final String PER_FILE_LINE_FORMAT = "per-file %s=%s\n";

    static String formatAsString(CodeOwnerConfig codeOwnerConfig) {
      return formatIgnoreParentCodeOwners(codeOwnerConfig)
          + formatImports(codeOwnerConfig)
          + formatFolderCodeOwners(codeOwnerConfig)
          + formatPerFileCodeOwners(codeOwnerConfig);
    }

    private static String formatIgnoreParentCodeOwners(CodeOwnerConfig codeOwnerConfig) {
      return codeOwnerConfig.ignoreParentCodeOwners() ? SET_NOPARENT_LINE : "";
    }

    private static String formatFolderCodeOwners(CodeOwnerConfig codeOwnerConfig) {
      ImmutableSet<CodeOwnerSet> folderCodeOwnerSets =
          codeOwnerConfig.codeOwnerSets().stream()
              // Filter out code owner sets with path expressions. If path expressions are present
              // the code owner set defines per-file code owners and is handled in
              // formatPerFileCodeOwners(CodeOwnerConfig).
              .filter(codeOwnerSet -> codeOwnerSet.pathExpressions().isEmpty())
              .collect(toImmutableSet());
      ImmutableList<String> emails =
          folderCodeOwnerSets.stream()
              .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
              .map(CodeOwnerReference::email)
              .sorted()
              .distinct()
              .collect(toImmutableList());
      SortedSetMultimap<String, String> annotations = TreeMultimap.create();
      folderCodeOwnerSets.forEach(
          codeOwnerSet ->
              codeOwnerSet
                  .annotations()
                  .forEach(
                      (codeOwnerReference, annotation) ->
                          annotations.put(codeOwnerReference.email(), annotation.key())));

      StringBuilder b = new StringBuilder();
      for (String email : emails) {
        b.append(email);
        annotations.get(email).forEach(annotation -> b.append(" #{" + annotation + "}"));
        b.append('\n');
      }
      return b.toString();
    }

    private static String formatPerFileCodeOwners(CodeOwnerConfig codeOwnerConfig) {
      StringBuilder b = new StringBuilder();
      codeOwnerConfig.codeOwnerSets().stream()
          // Filter out code owner sets without path expressions. If path expressions are absent the
          // code owner set defines global code owners and is handled in
          // formatGlobalCodeOwners(CodeOwnerConfig).
          .filter(codeOwnerSet -> !codeOwnerSet.pathExpressions().isEmpty())
          .map(Formatter::formatCodeOwnerSet)
          .forEach(b::append);
      return b.toString();
    }

    private static String formatCodeOwnerSet(CodeOwnerSet codeOwnerSet) {
      String formattedPathExpressions = formatValuesAsList(codeOwnerSet.pathExpressions());

      StringBuilder b = new StringBuilder();
      if (codeOwnerSet.ignoreGlobalAndParentCodeOwners()) {
        b.append(String.format(PER_FILE_LINE_FORMAT, formattedPathExpressions, TOK_SET_NOPARENT));
      }

      for (CodeOwnerConfigReference codeOwnerConfigReference : codeOwnerSet.imports()) {
        b.append(
            String.format(
                PER_FILE_LINE_FORMAT,
                formattedPathExpressions,
                formatImport(codeOwnerConfigReference)));
      }

      if (!codeOwnerSet.codeOwners().isEmpty()) {
        b.append(
            String.format(
                PER_FILE_LINE_FORMAT,
                formattedPathExpressions,
                formatCodeOwnerReferencesAsList(codeOwnerSet.codeOwners())));
      }
      return b.toString();
    }

    private static String formatCodeOwnerReferencesAsList(
        ImmutableSet<CodeOwnerReference> codeOwnerReferences) {
      return formatValuesAsList(codeOwnerReferences.stream().map(CodeOwnerReference::email));
    }

    private static String formatValuesAsList(ImmutableSet<String> values) {
      return formatValuesAsList(values.stream());
    }

    private static String formatValuesAsList(Stream<String> stream) {
      return stream.sorted().distinct().collect(joining(","));
    }

    private static String formatImports(CodeOwnerConfig codeOwnerConfig) {
      StringBuilder b = new StringBuilder();
      codeOwnerConfig
          .imports()
          .forEach(
              codeOwnerConfigReference ->
                  b.append(formatImport(codeOwnerConfigReference)).append('\n'));
      return b.toString();
    }

    private static String formatImport(CodeOwnerConfigReference codeOwnerConfigReference) {
      StringBuilder b = new StringBuilder();

      // write the keyword
      switch (codeOwnerConfigReference.importMode()) {
        case ALL:
          b.append("include ");
          break;
        case GLOBAL_CODE_OWNER_SETS_ONLY:
          b.append("file: ");
          break;
        default:
          throw new IllegalStateException(
              String.format("unknown import mode: %s", codeOwnerConfigReference.importMode()));
      }

      // write the project
      if (codeOwnerConfigReference.project().isPresent()) {
        b.append(codeOwnerConfigReference.project().get()).append(':');
      }

      // write the branch
      if (codeOwnerConfigReference.branch().isPresent()) {
        checkState(
            codeOwnerConfigReference.project().isPresent(),
            "project is required if branch is specified: %s",
            codeOwnerConfigReference);
        b.append(codeOwnerConfigReference.branch().get()).append(':');
      }

      // write the file path
      b.append(codeOwnerConfigReference.filePath());

      return b.toString();
    }
  }
}
