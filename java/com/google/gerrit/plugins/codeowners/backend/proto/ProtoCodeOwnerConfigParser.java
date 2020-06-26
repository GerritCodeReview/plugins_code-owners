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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerSet;
import com.google.inject.Singleton;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.util.Comparator;

/**
 * Parser and formatter for the proto syntax that is used to store {@link CodeOwnerConfig}s in
 * {@code OWNER_METADATA} files.
 *
 * <p>The proto format is defined in {@code proto/owners_metadata.proto}.
 *
 * <p>The parsing fails if the provided string is not a valid text representation of an {@code
 * owners_metadata.proto}.
 */
@Singleton
class ProtoCodeOwnerConfigParser implements CodeOwnerConfigParser {
  @Override
  public CodeOwnerConfig parse(
      CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) throws IOException {
    return Parser.parse(
        requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey"),
        Strings.nullToEmpty(codeOwnerConfigAsString));
  }

  @Override
  public String formatAsString(CodeOwnerConfig codeOwnerConfig) throws IOException {
    return Formatter.formatAsString(requireNonNull(codeOwnerConfig, "codeOwnerConfig"));
  }

  private static class Parser {
    static CodeOwnerConfig parse(
        CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) throws IOException {
      OwnersMetadata.OwnersConfig ownersConfig = parseProto(codeOwnerConfigAsString);
      return CodeOwnerConfig.builder(codeOwnerConfigKey)
          .setIgnoreParentCodeOwners(ownersConfig.getIgnoreParentOwners())
          .setCodeOwnerSets(getCodeOwnerSets(ownersConfig))
          .build();
    }

    private static OwnersMetadata.OwnersConfig parseProto(String codeOwnerConfigAsString)
        throws IOException {
      return TextFormat.parse(codeOwnerConfigAsString, OwnersMetadata.OwnersMetadataFile.class)
          .getOwnersConfig();
    }

    private static ImmutableList<CodeOwnerSet> getCodeOwnerSets(
        OwnersMetadata.OwnersConfig ownersConfig) {
      return ownersConfig.getOwnerSetsList().stream()
          .map(
              ownerSetProto ->
                  CodeOwnerSet.createWithoutPathExpressions(
                      ownerSetProto.getOwnersList().stream()
                          .map(ownerSet -> CodeOwnerReference.create(ownerSet.getEmail()))
                          .collect(toImmutableSet())))
          .filter(codeOwnerSet -> !codeOwnerSet.codeOwners().isEmpty())
          .collect(toImmutableList());
    }
  }

  private static class Formatter {
    static String formatAsString(CodeOwnerConfig codeOwnerConfig) {
      if (codeOwnerConfig.ignoreParentCodeOwners() == false
          && codeOwnerConfig.codeOwnerSets().isEmpty()) {
        return "";
      }

      OwnersMetadata.OwnersConfig.Builder ownersConfigProtoBuilder =
          OwnersMetadata.OwnersConfig.newBuilder();
      setIgnoreParentCodeOwners(ownersConfigProtoBuilder, codeOwnerConfig);
      setCodeOwnerSets(ownersConfigProtoBuilder, codeOwnerConfig);
      return formatAsTextProto(ownersConfigProtoBuilder.build());
    }

    private static void setIgnoreParentCodeOwners(
        OwnersMetadata.OwnersConfig.Builder ownersConfigProtoBuilder,
        CodeOwnerConfig codeOwnerConfig) {
      if (codeOwnerConfig.ignoreParentCodeOwners()) {
        ownersConfigProtoBuilder.setIgnoreParentOwners(true);
      }
    }

    private static void setCodeOwnerSets(
        OwnersMetadata.OwnersConfig.Builder ownersConfigProtoBuilder,
        CodeOwnerConfig codeOwnerConfig) {
      for (CodeOwnerSet codeOwnerSet : codeOwnerConfig.codeOwnerSets()) {
        OwnersMetadata.OwnerSet.Builder ownerSetProtoBuilder = OwnersMetadata.OwnerSet.newBuilder();
        codeOwnerSet.codeOwners().stream()
            .sorted(Comparator.comparing(CodeOwnerReference::email))
            .forEach(
                codeOwnerReference ->
                    ownerSetProtoBuilder.addOwners(
                        OwnersMetadata.Owner.newBuilder()
                            .setEmail(codeOwnerReference.email())
                            .build()));
        ownersConfigProtoBuilder.addOwnerSets(ownerSetProtoBuilder.build());
      }
    }

    private static String formatAsTextProto(OwnersMetadata.OwnersConfig ownersConfigProto) {
      return TextFormat.printer()
          .printToString(
              OwnersMetadata.OwnersMetadataFile.newBuilder()
                  .setOwnersConfig(ownersConfigProto)
                  .build());
    }
  }
}
