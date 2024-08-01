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

package com.google.gerrit.plugins.codeowners.backend;

import java.util.HashMap;
import java.util.Map;

/**
 * Container with scoring factors for a code owner.
 */
public class CodeOwnerScoringFactors {

  private final Map<CodeOwnerScore, Integer> scoringFactors;

  public CodeOwnerScoringFactors() {
    scoringFactors = new HashMap<>();
  }

  public void put(CodeOwnerScore codeOwnerScore, Integer value) {
    if (codeOwnerScore != null && value != null) {
      scoringFactors.put(codeOwnerScore, value);
    }
  }

  public Map<CodeOwnerScore, Integer> getScoringFactors() {
    return scoringFactors;
  }
}
