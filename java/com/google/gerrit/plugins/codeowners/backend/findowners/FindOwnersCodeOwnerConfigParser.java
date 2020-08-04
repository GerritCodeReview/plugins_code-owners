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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigImportMode;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parser and formatter for the syntax that is used to store {@link CodeOwnerConfig}s in {@code
 * OWNERS} files as they are used by the {@code find-owners} plugin.
 *
 * <p>The syntax is described at in the {@code find-owners} plugin documentation at:
 * https://gerrit.googlesource.com/plugins/find-owners/+/master/src/main/resources/Documentation/syntax.md
 *
 * <p><strong>Note:</strong> Currently this class only supports a subset of the syntax. Only the
 * following syntax elements are supported:
 *
 * <ul>
 *   <li>comment: a line can be a comment (comments must start with '#')
 *   <li>code owner emails: a line can be the email of a code owner
 * </ul>
 *
 * <p>Comment lines and invalid lines silently ignored.
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

  @Override
  public CodeOwnerConfig parse(
      CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) {
    return Parser.parse(
        requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey"),
        Strings.nullToEmpty(codeOwnerConfigAsString));
  }

  @Override
  public String formatAsString(CodeOwnerConfig codeOwnerConfig) {
    return Formatter.formatAsString(requireNonNull(codeOwnerConfig, "codeOwnerConfig"));
  }

  private static class Parser {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private static final String COMMA = "[\\s]*,[\\s]*";

    // Separator for project and file paths in an include line.
    private static final String COLON = "[\\s]*:[\\s]*"; // project:file

    private static final String BOL = "^[\\s]*"; // begin-of-line
    private static final String EOL = "[\\s]*(#.*)?$"; // end-of-line
    private static final String GLOB = "[^\\s,=]+"; // a file glob

    private static final String EMAIL = "([^\\s<>@,]+@[^\\s<>@#,]+)";
    private static final String EMAIL_LIST = "(" + EMAIL + "(" + COMMA + EMAIL + ")*)";

    // A Gerrit project name followed by a colon and optional spaces.
    private static final String PROJECT_NAME = "([^\\s:]+" + COLON + ")?";

    // A relative or absolute file path name without any colon or space character.
    private static final String FILE_PATH = "([^\\s:#]+)";

    private static final String PROJECT_AND_FILE = PROJECT_NAME + FILE_PATH;

    private static final String SET_NOPARENT = "set[\\s]+noparent";

    private static final String INCLUDE_OR_FILE = "(file:[\\s]*|include[\\s]+)";

    // Simple input lines with 0 or 1 sub-pattern.
    private static final Pattern PAT_COMMENT = Pattern.compile(BOL + EOL);
    private static final Pattern PAT_EMAIL = Pattern.compile(BOL + EMAIL + EOL);
    private static final Pattern PAT_INCLUDE =
        Pattern.compile(BOL + INCLUDE_OR_FILE + PROJECT_AND_FILE + EOL);
    private static final Pattern PAT_NO_PARENT = Pattern.compile(BOL + SET_NOPARENT + EOL);

    private static final Pattern PAT_PER_FILE_OWNERS =
        Pattern.compile("^(" + EMAIL_LIST + "|" + SET_NOPARENT + ")$");
    private static final Pattern PAT_GLOBS =
        Pattern.compile("^(" + GLOB + "(" + COMMA + GLOB + ")*)$");

    // PAT_PER_FILE matches a line to two groups: (1) globs, (2) emails
    // Trimmed 1st group should match PAT_GLOBS;
    // trimmed 2nd group should match PAT_PER_FILE_OWNERS.
    private static final Pattern PAT_PER_FILE =
        Pattern.compile(BOL + "per-file[\\s]+([^=#]+)=[\\s]*([^#]+)" + EOL);

    static CodeOwnerConfig parse(
        CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) {
      CodeOwnerConfig.Builder codeOwnerConfigBuilder = CodeOwnerConfig.builder(codeOwnerConfigKey);
      CodeOwnerSet.Builder globalCodeOwnerSetBuilder = CodeOwnerSet.builder();
      List<CodeOwnerSet> perFileCodeOwnerSet = new ArrayList<>();

      for (String line : Splitter.onPattern("\\R").split(codeOwnerConfigAsString)) {
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

    private static void parseLine(
        CodeOwnerConfig.Builder codeOwnerConfigBuilder,
        CodeOwnerSet.Builder globalCodeOwnerSetBuilder,
        List<CodeOwnerSet> perFileCodeOwnerSets,
        String line) {
      String email;
      CodeOwnerSet codeOwnerSet;
      CodeOwnerConfigReference codeOwnerConfigReference;
      if (isNoParent(line)) {
        codeOwnerConfigBuilder.setIgnoreParentCodeOwners();
      } else if (isComment(line)) {
        // ignore comment lines and empty lines
      } else if ((email = parseEmail(line)) != null) {
        globalCodeOwnerSetBuilder.addCodeOwner(CodeOwnerReference.create(email));
      } else if ((codeOwnerSet = parsePerFile(line)) != null) {
        perFileCodeOwnerSets.add(codeOwnerSet);
      } else if ((codeOwnerConfigReference = parseInclude(line)) != null) {
        codeOwnerConfigBuilder.addImport(codeOwnerConfigReference);
      } else {
        logger.atInfo().log("Skipping unknown line: %s", line);
      }
    }

    private static CodeOwnerSet parsePerFile(String line) {
      Matcher m = PAT_PER_FILE.matcher(line);
      if (!m.matches()
          || !isGlobs(m.group(1).trim())
          || !PAT_PER_FILE_OWNERS.matcher(m.group(2).trim()).matches()) {
        return null;
      }

      String[] globsAndOwners =
          new String[] {removeExtraSpaces(m.group(1)), removeExtraSpaces(m.group(2))};
      String[] dirGlobs = globsAndOwners[0].split(COMMA, -1);
      String directive = globsAndOwners[1];
      if (directive.equals(TOK_SET_NOPARENT)) {
        return CodeOwnerSet.builder()
            .setIgnoreGlobalAndParentCodeOwners()
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

    private static boolean isComment(String line) {
      return PAT_COMMENT.matcher(line).matches();
    }

    private static boolean isNoParent(String line) {
      return PAT_NO_PARENT.matcher(line).matches();
    }

    private static String parseEmail(String line) {
      Matcher m = PAT_EMAIL.matcher(line);
      return m.matches() ? m.group(1).trim() : null;
    }

    private static CodeOwnerConfigReference parseInclude(String line) {
      Matcher m = Parser.PAT_INCLUDE.matcher(line);
      if (!m.matches()) {
        return null;
      }

      CodeOwnerConfigReference.Builder builder =
          CodeOwnerConfigReference.builder(m.group(3).trim());

      String keyword = m.group(1).trim();
      if (keyword.equals("include")) {
        builder.setImportMode(CodeOwnerConfigImportMode.ALL);
      }

      String projectName = m.group(2);
      if (projectName != null && projectName.length() > 1) {
        // PROJECT_NAME ends with ':'
        projectName = projectName.split(COLON, -1)[0].trim();
        builder.setProject(Project.nameKey(projectName));
      }

      return builder.build();
    }

    private static boolean isGlobs(String line) {
      return PAT_GLOBS.matcher(line).matches();
    }

    private static String removeExtraSpaces(String s) {
      return s.trim().replaceAll("[\\s]+", " ").replaceAll("[\\s]*:[\\s]*", ":");
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
          + formatGlobalCodeOwners(codeOwnerConfig)
          + formatPerFileCodeOwners(codeOwnerConfig);
    }

    private static String formatIgnoreParentCodeOwners(CodeOwnerConfig codeOwnerConfig) {
      return codeOwnerConfig.ignoreParentCodeOwners() ? SET_NOPARENT_LINE : "";
    }

    private static String formatGlobalCodeOwners(CodeOwnerConfig codeOwnerConfig) {
      String emails =
          codeOwnerConfig.codeOwnerSets().stream()
              // Filter out code owner sets with path expressions. If path expressions are present
              // the code owner set defines per-file code owners and is handled in
              // formatPerFileCodeOwners(CodeOwnerConfig).
              .filter(codeOwnerSet -> codeOwnerSet.pathExpressions().isEmpty())
              .flatMap(codeOwnerSet -> codeOwnerSet.codeOwners().stream())
              .map(CodeOwnerReference::email)
              .sorted()
              .distinct()
              .collect(joining("\n"));
      if (!emails.isEmpty()) {
        return emails + "\n";
      }
      return emails;
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
          .forEach(codeOwnerConfigReference -> b.append(formatImport(codeOwnerConfigReference)));
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

      // write the file path
      b.append(codeOwnerConfigReference.filePath());

      return b.toString();
    }
  }
}
