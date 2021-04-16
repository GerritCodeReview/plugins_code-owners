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

package com.google.gerrit.plugins.codeowners.acceptance.restapi;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.joining;

import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

/**
 * Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.GetOwnedPaths} REST
 * endpoint. that require using via REST.
 *
 * <p>Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.GetOwnedPaths}
 * REST endpoint that can use the Java API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.api.GetOwnedPathsIT}.
 */
public class GetOwnedPathRestIT extends AbstractCodeOwnersIT {
  private String changeId;

  @Before
  public void createTestChange() throws Exception {
    changeId = createChange().getChangeId();
  }

  @Test
  public void cannotGetOwnedPathsWithInvalidStart() throws Exception {
    RestResponse r = adminRestSession.get(getUrl("user=" + user.email(), "start=invalid"));
    r.assertBadRequest();
    assertThat(r.getEntityContent()).contains("\"invalid\" is not a valid value for \"--start\"");
  }

  @Test
  public void cannotGetOwnedPathsWithInvalidLimit() throws Exception {
    RestResponse r = adminRestSession.get(getUrl("user=" + user.email(), "limit=invalid"));
    r.assertBadRequest();
    assertThat(r.getEntityContent()).contains("\"invalid\" is not a valid value for \"--limit\"");
  }

  private String getUrl(String... parameters) {
    StringBuilder b = new StringBuilder();
    b.append(getUrl());
    String paramaterString = Arrays.stream(parameters).collect(joining("&"));
    if (!paramaterString.isEmpty()) {
      b.append('?').append(paramaterString);
    }
    return b.toString();
  }

  private String getUrl() {
    return String.format(
        "/changes/%s/revisions/%s/owned_paths",
        IdString.fromDecoded(changeId), IdString.fromDecoded("current"));
  }
}
