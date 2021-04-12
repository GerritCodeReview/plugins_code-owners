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

package com.google.gerrit.plugins.codeowners.acceptance.api;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.plugins.codeowners.testing.OwnedPathsInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.api.OwnedPathsInfo;
import com.google.gerrit.plugins.codeowners.restapi.GetOwnedPaths;
import com.google.gerrit.plugins.codeowners.util.JgitPath;
import com.google.inject.Inject;
import org.junit.Test;

/**
 * Acceptance test for the {@link com.google.gerrit.plugins.codeowners.restapi.GetOwnedPaths} REST
 * endpoint.
 *
 * <p>Further tests for the {@link com.google.gerrit.plugins.codeowners.restapi.GetOwnedPaths} REST
 * endpoint that require using the REST API are implemented in {@link
 * com.google.gerrit.plugins.codeowners.acceptance.restapi.GetOwnedPathRestIT}.
 */
public class GetOwnedPathsIT extends AbstractCodeOwnersIT {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void getOwnedPathRequiresUser() throws Exception {
    String changeId = createChange().getChangeId();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                changeCodeOwnersApiFactory
                    .change(changeId)
                    .current()
                    .getOwnedPaths()
                    .forUser(/* user= */ null)
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("--user required");
  }

  @Test
  public void cannotGetOwnedPathForNonExistingUser() throws Exception {
    String nonExistingUser = "non-existing";
    String changeId = createChange().getChangeId();
    UnprocessableEntityException exception =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                changeCodeOwnersApiFactory
                    .change(changeId)
                    .current()
                    .getOwnedPaths()
                    .forUser(nonExistingUser)
                    .get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("Account '%s' not found", nonExistingUser));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void cannotGetOwnedPathForNonVisibleUser() throws Exception {
    TestAccount nonVisibleUser =
        accountCreator.create(
            "nonVisibleUser",
            "nonVisibleUser@example.com",
            "Non-Visible User",
            /* displayName= */ null);
    String changeId = createChange().getChangeId();
    requestScopeOperations.setApiUser(user.id());
    UnprocessableEntityException exception =
        assertThrows(
            UnprocessableEntityException.class,
            () ->
                changeCodeOwnersApiFactory
                    .change(changeId)
                    .current()
                    .getOwnedPaths()
                    .forUser(nonVisibleUser.email())
                    .get());
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(String.format("Account '%s' not found", nonVisibleUser.email()));
  }

  @Test
  public void getOwnedPaths() throws Exception {
    setAsCodeOwners("/foo/", user);

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";
    String path3 = "/bar/foo.md";

    String changeId =
        createChange(
                "test change",
                ImmutableMap.of(
                    JgitPath.of(path1).get(),
                    "file content",
                    JgitPath.of(path2).get(),
                    "file content",
                    JgitPath.of(path3).get(),
                    "file content"))
            .getChangeId();

    OwnedPathsInfo ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path1, path2).inOrder();
    assertThat(ownedPathsInfo).hasMoreThat().isNull();
  }

  @Test
  public void getOwnedPathForOwnUser() throws Exception {
    setAsRootCodeOwners(admin);

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";

    String changeId =
        createChange(
                "test change",
                ImmutableMap.of(
                    JgitPath.of(path1).get(),
                    "file content",
                    JgitPath.of(path2).get(),
                    "file content"))
            .getChangeId();

    OwnedPathsInfo ownedPathsInfo =
        changeCodeOwnersApiFactory.change(changeId).current().getOwnedPaths().forUser("self").get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path1, path2).inOrder();
  }

  @Test
  public void getOwnedPathsForNonCodeOwner() throws Exception {
    setAsCodeOwners("/foo/", admin);

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";
    String path3 = "/bar/foo.md";

    String changeId =
        createChange(
                "test change",
                ImmutableMap.of(
                    JgitPath.of(path1).get(),
                    "file content",
                    JgitPath.of(path2).get(),
                    "file content",
                    JgitPath.of(path3).get(),
                    "file content"))
            .getChangeId();

    OwnedPathsInfo ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().isEmpty();
  }

  @Test
  public void getOwnedPathsWithStart() throws Exception {
    setAsRootCodeOwners(user);

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";
    String path3 = "/bar/foo.md";
    String path4 = "/bar/baz.md";

    String changeId =
        createChange(
                "test change",
                ImmutableMap.of(
                    JgitPath.of(path1).get(),
                    "file content",
                    JgitPath.of(path2).get(),
                    "file content",
                    JgitPath.of(path3).get(),
                    "file content",
                    JgitPath.of(path4).get(),
                    "file content"))
            .getChangeId();

    OwnedPathsInfo ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withStart(0)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo)
        .hasOwnedPathsThat()
        .containsExactly(path4, path3, path1, path2)
        .inOrder();

    ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withStart(1)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path3, path1, path2).inOrder();

    ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withStart(2)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path1, path2).inOrder();

    ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withStart(3)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path2);

    ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withStart(4)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().isEmpty();
  }

  @Test
  public void getOwnedPathsWithLimit() throws Exception {
    setAsRootCodeOwners(user);

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";
    String path3 = "/bar/foo.md";
    String path4 = "/bar/baz.md";

    String changeId =
        createChange(
                "test change",
                ImmutableMap.of(
                    JgitPath.of(path1).get(),
                    "file content",
                    JgitPath.of(path2).get(),
                    "file content",
                    JgitPath.of(path3).get(),
                    "file content",
                    JgitPath.of(path4).get(),
                    "file content"))
            .getChangeId();

    OwnedPathsInfo ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withLimit(1)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path4);
    assertThat(ownedPathsInfo).hasMoreThat().isTrue();

    ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withLimit(2)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path4, path3).inOrder();
    assertThat(ownedPathsInfo).hasMoreThat().isTrue();

    ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withLimit(3)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path4, path3, path1).inOrder();
    assertThat(ownedPathsInfo).hasMoreThat().isTrue();

    ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withLimit(4)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo)
        .hasOwnedPathsThat()
        .containsExactly(path4, path3, path1, path2)
        .inOrder();
    assertThat(ownedPathsInfo).hasMoreThat().isNull();
  }

  @Test
  public void getOwnedPathsWithStartAndLimit() throws Exception {
    setAsRootCodeOwners(user);

    String path1 = "/foo/bar/baz.md";
    String path2 = "/foo/baz/bar.md";
    String path3 = "/bar/foo.md";
    String path4 = "/bar/baz.md";

    String changeId =
        createChange(
                "test change",
                ImmutableMap.of(
                    JgitPath.of(path1).get(),
                    "file content",
                    JgitPath.of(path2).get(),
                    "file content",
                    JgitPath.of(path3).get(),
                    "file content",
                    JgitPath.of(path4).get(),
                    "file content"))
            .getChangeId();

    OwnedPathsInfo ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .withStart(1)
            .withLimit(2)
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().containsExactly(path3, path1).inOrder();
    assertThat(ownedPathsInfo).hasMoreThat().isTrue();
  }

  @Test
  public void getOwnedPathsLimitedByDefault() throws Exception {
    setAsRootCodeOwners(user);

    ImmutableMap.Builder<String, String> files = ImmutableMap.builder();
    for (int i = 1; i <= GetOwnedPaths.DEFAULT_LIMIT + 1; i++) {
      files.put(String.format("foo-%d.txt", i), "file content");
    }

    String changeId = createChange("test change", files.build()).getChangeId();

    OwnedPathsInfo ownedPathsInfo =
        changeCodeOwnersApiFactory
            .change(changeId)
            .current()
            .getOwnedPaths()
            .forUser(user.email())
            .get();
    assertThat(ownedPathsInfo).hasOwnedPathsThat().hasSize(GetOwnedPaths.DEFAULT_LIMIT);
  }

  @Test
  public void startCannotBeNegative() throws Exception {
    String changeId = createChange().getChangeId();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                changeCodeOwnersApiFactory
                    .change(changeId)
                    .current()
                    .getOwnedPaths()
                    .withStart(-1)
                    .forUser(user.email())
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("start cannot be negative");
  }

  @Test
  public void limitCannotBeNegative() throws Exception {
    String changeId = createChange().getChangeId();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                changeCodeOwnersApiFactory
                    .change(changeId)
                    .current()
                    .getOwnedPaths()
                    .withLimit(-1)
                    .forUser(user.email())
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("limit must be positive");
  }

  @Test
  public void cannotGetOwnedPathWithoutLimit() throws Exception {
    String changeId = createChange().getChangeId();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                changeCodeOwnersApiFactory
                    .change(changeId)
                    .current()
                    .getOwnedPaths()
                    .withLimit(0)
                    .forUser(user.email())
                    .get());
    assertThat(exception).hasMessageThat().isEqualTo("limit must be positive");
  }
}
