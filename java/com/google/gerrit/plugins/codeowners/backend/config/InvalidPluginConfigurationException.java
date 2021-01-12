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

package com.google.gerrit.plugins.codeowners.backend.config;

/**
 * Exception that is thrown if a configuration parameter of the code-owners plugin has an invalid
 * value.
 */
public class InvalidPluginConfigurationException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  /**
   * Constructor.
   *
   * @param message message explaining which configuration parameter has an invalid value, may be
   *     exposed to end users
   */
  public InvalidPluginConfigurationException(String pluginName, String message) {
    super(String.format("Invalid configuration of the %s plugin. %s", pluginName, message));
  }
}
