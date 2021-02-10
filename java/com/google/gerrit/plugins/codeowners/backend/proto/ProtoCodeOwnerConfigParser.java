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

package com.google.gerrit.plugins.codeowners.backend.proto;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParseException;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.gerrit.plugins.codeowners.backend.proto.OwnersMetadata.Owner;
import com.google.gerrit.plugins.codeowners.backend.proto.OwnersMetadata.OwnerSet;
import com.google.gerrit.plugins.codeowners.backend.proto.OwnersMetadata.OwnersConfig;
import com.google.gerrit.plugins.codeowners.backend.proto.OwnersMetadata.OwnersMetadataFile;
import com.google.inject.Singleton;
import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;
import java.util.Comparator;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Parser and formatter for the proto syntax that is used to store {@link CodeOwnerConfig}s in
 * {@code OWNER_METADATA} files.
 *
 * <p>The proto format is defined in {@code proto/owners_metadata.proto}.
 *
 * <p>The parsing fails with a {@link CodeOwnerConfigParseException} if the provided string is not a
 * valid text representation of an {@code owners_metadata.proto}.
 */
@Singleton
@VisibleForTesting
public class ProtoCodeOwnerConfigParser implements CodeOwnerConfigParser {
  @Override
  public CodeOwnerConfig parse(
      ObjectId revision, CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString)
      throws CodeOwnerConfigParseException {
    requireNonNull(revision, "revision");
    requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey");

    try {
      return Parser.parse(
          revision, codeOwnerConfigKey, Strings.nullToEmpty(codeOwnerConfigAsString));
    } catch (ParseException e) {
      throw new CodeOwnerConfigParseException(codeOwnerConfigKey, e.getMessage());
    }
  }

  @Override
  public String formatAsString(CodeOwnerConfig codeOwnerConfig) throws ParseException {
    return Formatter.formatAsString(requireNonNull(codeOwnerConfig, "codeOwnerConfig"));
  }

  private static class Parser {
    static CodeOwnerConfig parse(
        ObjectId revision, CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString)
        throws ParseException {
      OwnersConfig ownersConfig = parseProto(codeOwnerConfigAsString);
      return CodeOwnerConfig.builder(codeOwnerConfigKey, revision)
          .setIgnoreParentCodeOwners(ownersConfig.getIgnoreParentOwners())
          .setCodeOwnerSets(getCodeOwnerSets(ownersConfig))
          .build();
    }

    private static OwnersConfig parseProto(String codeOwnerConfigAsString) throws ParseException {
      return TextFormat.parse(codeOwnerConfigAsString, OwnersMetadataFile.class).getOwnersConfig();
    }

    private static ImmutableList<CodeOwnerSet> getCodeOwnerSets(OwnersConfig ownersConfig) {
      return ownersConfig.getOwnerSetsList().stream()
          .map(
              ownerSetProto ->
                  CodeOwnerSet.builder()
                      .setPathExpressions(
                          ImmutableSet.copyOf(ownerSetProto.getPathExpressionsList()))
                      .setCodeOwners(
                          ownerSetProto.getOwnersList().stream()
                              .map(ownerSet -> CodeOwnerReference.create(ownerSet.getEmail()))
                              .collect(toImmutableSet()))
                      .build())
          .filter(codeOwnerSet -> !codeOwnerSet.codeOwners().isEmpty())
          .collect(toImmutableList());
    }
  }

  private static class Formatter {
    static String formatAsString(CodeOwnerConfig codeOwnerConfig) {
      checkState(codeOwnerConfig.imports().isEmpty(), "imports are not supported");
      checkState(
          codeOwnerConfig.codeOwnerSets().stream()
              .allMatch(codeOwnerSet -> codeOwnerSet.imports().isEmpty()),
          "per file imports are not supported");

      if (codeOwnerConfig.ignoreParentCodeOwners() == false
          && codeOwnerConfig.codeOwnerSets().isEmpty()) {
        return "";
      }

      OwnersConfig.Builder ownersConfigProtoBuilder = OwnersConfig.newBuilder();
      setIgnoreParentCodeOwners(ownersConfigProtoBuilder, codeOwnerConfig);
      setCodeOwnerSets(ownersConfigProtoBuilder, codeOwnerConfig);
      return formatAsTextProto(ownersConfigProtoBuilder.build());
    }

    private static void setIgnoreParentCodeOwners(
        OwnersConfig.Builder ownersConfigProtoBuilder, CodeOwnerConfig codeOwnerConfig) {
      if (codeOwnerConfig.ignoreParentCodeOwners()) {
        ownersConfigProtoBuilder.setIgnoreParentOwners(true);
      }
    }

    private static void setCodeOwnerSets(
        OwnersConfig.Builder ownersConfigProtoBuilder, CodeOwnerConfig codeOwnerConfig) {
      for (CodeOwnerSet codeOwnerSet : codeOwnerConfig.codeOwnerSets()) {
        checkState(
            !codeOwnerSet.ignoreGlobalAndParentCodeOwners(),
            "ignoreGlobalAndParentCodeOwners is not supported");
        OwnerSet.Builder ownerSetProtoBuilder = ownersConfigProtoBuilder.addOwnerSetsBuilder();
        ownerSetProtoBuilder.addAllPathExpressions(codeOwnerSet.pathExpressions());
        codeOwnerSet.codeOwners().stream()
            .sorted(Comparator.comparing(CodeOwnerReference::email))
            .forEach(
                codeOwnerReference ->
                    ownerSetProtoBuilder.addOwners(
                        Owner.newBuilder().setEmail(codeOwnerReference.email()).build()));
      }
    }

    private static String formatAsTextProto(OwnersConfig ownersConfigProto) {
      return TextFormat.printer()
          .printToString(
              OwnersMetadataFile.newBuilder().setOwnersConfig(ownersConfigProto).build());
    }
  }
}
