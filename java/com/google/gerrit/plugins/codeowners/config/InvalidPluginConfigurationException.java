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

package com.google.gerrit.plugins.codeowners.config;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

/**
 * Exception that is thrown if a configuration parameter of the code-owners plugin has an invalid
 * value.
 */
public class InvalidPluginConfigurationException extends RuntimeException {
  public static class ExceptionHook implements com.google.gerrit.server.ExceptionHook {
    @Override
    public boolean skipRetryWithTrace(String actionType, String actionName, Throwable throwable) {
      return isInvalidPluginConfigurationException(throwable);
    }

    @Override
    public ImmutableList<String> getUserMessages(Throwable throwable, @Nullable String traceId) {
      if (isInvalidPluginConfigurationException(throwable)) {
        return ImmutableList.of(throwable.getMessage());
      }
      return ImmutableList.of();
    }

    @Override
    public Optional<Status> getStatus(Throwable throwable) {
      if (isInvalidPluginConfigurationException(throwable)) {
        return Optional.of(Status.create(409, "Conflict"));
      }
      return Optional.empty();
    }

    private static boolean isInvalidPluginConfigurationException(Throwable throwable) {
      return Throwables.getCausalChain(throwable).stream()
          .anyMatch(t -> t instanceof InvalidPluginConfigurationException);
    }
  }

  private static final long serialVersionUID = 1L;

  private static final String MESSAGE_FORMAT = "Invalid configuration of the %s plugin. %s";

  /**
   * Constructor.
   *
   * @param message message explaining which configuration parameter has an invalid value, may be
   *     exposed to end users
   */
  public InvalidPluginConfigurationException(String pluginName, String message) {
    super(String.format(MESSAGE_FORMAT, pluginName, message));
  }
}
