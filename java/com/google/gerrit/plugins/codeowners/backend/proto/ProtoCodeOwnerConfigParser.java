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

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfig;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerReference;
import com.google.inject.Singleton;
import com.google.protobuf.TextFormat;
import java.io.IOException;
import java.util.Comparator;

/**
 * Parser and formatter for the proto syntax that is used to store {@link CodeOwnerConfig}s in
 * {@code OWNER_METADATA} files.
 */
@Singleton
class ProtoCodeOwnerConfigParser implements CodeOwnerConfigParser {
  @Override
  public CodeOwnerConfig parse(
      CodeOwnerConfig.Key codeOwnerConfigKey, String codeOwnerConfigAsString) throws IOException {
    CodeOwnerConfig.Builder codeOwnerConfig =
        CodeOwnerConfig.builder(requireNonNull(codeOwnerConfigKey, "codeOwnerConfigKey"));
    OwnerMetadataProto.OwnerMetadata ownerMetadataProto =
        TextFormat.parse(
            Strings.nullToEmpty(codeOwnerConfigAsString), OwnerMetadataProto.OwnerMetadata.class);
    ownerMetadataProto
        .getOwnersConfig()
        .getOwnerSetsList()
        .forEach(
            ownerSetProto ->
                ownerSetProto
                    .getOwnersList()
                    .forEach(
                        ownersProto -> codeOwnerConfig.addCodeOwnerEmail(ownersProto.getEmail())));
    return codeOwnerConfig.build();
  }

  @Override
  public String formatAsString(CodeOwnerConfig codeOwnerConfig) throws IOException {
    if (requireNonNull(codeOwnerConfig, "codeOwnerConfig").codeOwners().isEmpty()) {
      return "";
    }

    OwnerMetadataProto.OwnersConfig.Builder ownersConfigProtoBuilder =
        OwnerMetadataProto.OwnersConfig.newBuilder();
    OwnerMetadataProto.OwnerSet.Builder ownersSetProtoBuilder =
        OwnerMetadataProto.OwnerSet.newBuilder();
    codeOwnerConfig.codeOwners().stream()
        .sorted(Comparator.comparing(CodeOwnerReference::email))
        .forEach(
            codeOwnerReference ->
                ownersSetProtoBuilder.addOwners(
                    OwnerMetadataProto.Owner.newBuilder()
                        .setEmail(codeOwnerReference.email())
                        .build()));
    ownersConfigProtoBuilder.addOwnerSets(ownersSetProtoBuilder.build()).build();
    return TextFormat.printer()
        .printToString(
            OwnerMetadataProto.OwnerMetadata.newBuilder()
                .setOwnersConfig(ownersConfigProtoBuilder.build())
                .build());
  }
}
