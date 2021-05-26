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

package com.google.gerrit.plugins.codeowners.backend;

import java.util.ArrayList;
import java.util.List;

/** Class that defines all known/supported {@link CodeOwnerAnnotation}s on code owners. */
public class CodeOwnerAnnotations {
  /**
   * Code owners with this annotation are omitted when suggesting code owners (see {@link
   * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInChange}).
   */
  public static final CodeOwnerAnnotation NEVER_SUGGEST_ANNOTATION =
      CodeOwnerAnnotation.create("NEVER_SUGGEST");

  private static final List<String> KEYS_ALL;

  static {
    KEYS_ALL = new ArrayList<>();
    KEYS_ALL.add(NEVER_SUGGEST_ANNOTATION.key());
  }

  /** Whether the given annotation is known and supported. */
  public static boolean isSupported(String annotation) {
    return KEYS_ALL.contains(annotation);
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>This class contains only static method and hence never needs to be instantiated.
   */
  private CodeOwnerAnnotations() {}
}
