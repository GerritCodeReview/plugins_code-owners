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

package com.google.gerrit.plugins.codeowners.restapi;

import com.google.gerrit.plugins.codeowners.api.CheckedCodeOwnerConfigFileInfo;
import com.google.gerrit.plugins.codeowners.api.CodeOwnerConfigFileInfo;

/** Collection of routines to populate {@link CheckedCodeOwnerConfigFileInfo}. */
public class CheckedCodeOwnerConfigFileJson {
  public static CheckedCodeOwnerConfigFileInfo format(
      CodeOwnerConfigFileInfo codeOwnerConfigFileInfo,
      boolean assignsCodeOwnershipToUser,
      boolean areParentCodeOwnersIgnored,
      boolean areFolderCodeOwnersIgnored) {
    CheckedCodeOwnerConfigFileInfo info = new CheckedCodeOwnerConfigFileInfo();
    info.codeOwnerConfigFileInfo = codeOwnerConfigFileInfo;
    info.assignsCodeOwnershipToUser = assignsCodeOwnershipToUser;
    info.areParentCodeOwnersIgnored = areParentCodeOwnersIgnored;
    info.areFolderCodeOwnersIgnored = areFolderCodeOwnersIgnored;
    return info;
  }
}
