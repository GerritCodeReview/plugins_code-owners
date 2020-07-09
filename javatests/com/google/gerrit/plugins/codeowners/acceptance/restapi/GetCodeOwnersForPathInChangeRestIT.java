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

package com.google.gerrit.plugins.codeowners.acceptance.restapi;

import com.google.gerrit.extensions.restapi.IdString;
import org.junit.Before;

/**
 * Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInChange} REST endpoint. that
 * require using via REST.
 *
 * <p>Acceptance test for the {@link
 * com.google.gerrit.plugins.codeowners.restapi.GetCodeOwnersForPathInChange} REST endpoint that can
 * use the Java API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.api.GetCodeOwnersForPathInChangeIT}.
 */
public class GetCodeOwnersForPathInChangeRestIT extends AbstractGetCodeOwnersForPathRestIT {
  private String changeId;

  @Before
  public void createTestChange() throws Exception {
    changeId = createChange().getChangeId();
  }

  @Override
  protected String getUrl(String path) {
    return String.format(
        "/changes/%s/revisions/%s/code_owners/%s",
        IdString.fromDecoded(changeId),
        IdString.fromDecoded("current"),
        IdString.fromDecoded(path));
  }
}
