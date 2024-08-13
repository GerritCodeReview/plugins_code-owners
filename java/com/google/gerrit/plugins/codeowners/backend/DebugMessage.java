// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.plugins.codeowners.backend;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** A message to return debug information to callers. */
@AutoValue
public abstract class DebugMessage {
  /**
   * The debug message with all information.
   *
   * <p>Must be shown only to admin users (users with the 'Administrate Server' capability or the
   * 'Check Code Owner' capability).
   */
  public abstract String adminMessage();

  /**
   * The debug message without information that require admin permissions.
   *
   * <p>Can be shown to the calling user.
   *
   * <p>Some messages are not available for the calling user. In this case {@link Optional#empty()}
   * is returned.
   */
  public abstract Optional<String> userMessage();

  /**
   * Creates a debug message.
   *
   * @param adminMessage message that can only be shown to admins
   */
  public static DebugMessage createAdminOnlyMessage(String adminMessage) {
    return new AutoValue_DebugMessage(adminMessage, Optional.empty());
  }

  /**
   * Creates a debug message.
   *
   * @param adminMessage message that can only be shown to admins
   * @param userMessage message that can be shown to the calling user
   */
  public static DebugMessage createMessage(String adminMessage, String userMessage) {
    return new AutoValue_DebugMessage(adminMessage, Optional.of(userMessage));
  }

  /**
   * Creates a debug message.
   *
   * @param userMessage message that can be shown to the calling user
   */
  public static DebugMessage createMessage(String userMessage) {
    return new AutoValue_DebugMessage(userMessage, Optional.of(userMessage));
  }
}
