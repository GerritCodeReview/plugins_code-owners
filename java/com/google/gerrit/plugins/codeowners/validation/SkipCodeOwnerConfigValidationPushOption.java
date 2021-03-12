// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.validation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersInternalServerErrorException;
import com.google.gerrit.server.git.receive.PluginPushOption;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Push option that allows to skip the code owner config validation. */
@Singleton
public class SkipCodeOwnerConfigValidationPushOption implements PluginPushOption {
  public static final String NAME = "skip-code-owners-validation";

  private static final String DESCRIPTION = "skips the code owner config validation";

  private final String pluginName;
  private final PermissionBackend permissionBackend;
  private final SkipCodeOwnerConfigValidationCapability skipCodeOwnerConfigValidationCapability;

  @Inject
  SkipCodeOwnerConfigValidationPushOption(
      @PluginName String pluginName,
      PermissionBackend permissionBackend,
      SkipCodeOwnerConfigValidationCapability skipCodeOwnerConfigValidationCapability) {
    this.pluginName = pluginName;
    this.permissionBackend = permissionBackend;
    this.skipCodeOwnerConfigValidationCapability = skipCodeOwnerConfigValidationCapability;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return DESCRIPTION;
  }

  /**
   * Whether the code owner config validation should be skipped.
   *
   * <p>Only returns {@code true} if the {@code --code-owners~skip-code-owners-validation} push
   * option was specified and the calling user is allowed to skip the code owner config validation
   * (requires the {@link SkipCodeOwnerConfigValidationCapability}).
   *
   * @param pushOptions the push options that have been specified on the push
   * @return {@code true} if the {@code --code-owners~skip-code-owners-validation} push option was
   *     specified and the calling user is allowed to skip the code owner config validation
   * @throws InvalidValueException if the {@code --code-owners~skip-code-owners-validation} push
   *     option was specified with an invalid value or if the {@code
   *     --code-owners~skip-code-owners-validation} push option was specified multiple times
   * @throws AuthException thrown if the {@code --code-owners~skip-code-owners-validation} push
   *     option was specified, but the calling user is not allowed to skip the code owner config
   *     validation
   */
  public boolean skipValidation(ImmutableListMultimap<String, String> pushOptions)
      throws InvalidValueException, AuthException {
    String qualifiedName = pluginName + "~" + NAME;
    if (!pushOptions.containsKey(qualifiedName)) {
      return false;
    }
    ImmutableList<String> values = pushOptions.get(qualifiedName);
    if (values.size() == 1) {
      String value = values.get(0);
      if (Boolean.parseBoolean(value) || value.isEmpty()) {
        canSkipCodeOwnerConfigValidation();
        return true;
      }
      if (value.equalsIgnoreCase(Boolean.FALSE.toString())) {
        return false;
      }
    }
    throw new InvalidValueException(values);
  }

  private void canSkipCodeOwnerConfigValidation() throws AuthException {
    try {
      permissionBackend
          .currentUser()
          .check(skipCodeOwnerConfigValidationCapability.getPermission());
    } catch (PermissionBackendException e) {
      throw new CodeOwnersInternalServerErrorException(
          String.format(
              "Failed to check %s capability", SkipCodeOwnerConfigValidationCapability.ID),
          e);
    }
  }

  public class InvalidValueException extends Exception {
    private static final long serialVersionUID = 1L;

    InvalidValueException(ImmutableList<String> invalidValues) {
      super(
          invalidValues.size() == 1
              ? String.format(
                  "Invalid value for --%s~%s push option: %s",
                  pluginName, NAME, invalidValues.get(0))
              : String.format(
                  "--%s~%s push option can be specified only once, received multiple values: %s",
                  pluginName, NAME, invalidValues));
    }
  }
}
