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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Streams;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.Changes.QueryRequest;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerApprovalHasOperand;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnersInternalServerErrorException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

@Sandboxed
public class CodeOwnerHasOperandsIT extends AbstractCodeOwnersIT {
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ChangeQueryBuilder changeQueryBuilder;
  private CodeOwnerApprovalHasOperand codeOwnerApprovalHasOperand;

  @Before
  public void setup() throws Exception {
    codeOwnerApprovalHasOperand =
        plugin.getSysInjector().getInstance(CodeOwnerApprovalHasOperand.class);
  }

  @Test
  public void hasApproval_ok() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    ChangeData changeData = createChange("Change Adding A File", path, "file content").getChange();
    assertQuery("has:approval_code-owners");

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeData.change().getKey().get());
    assertQuery("has:approval_code-owners", changeData.change());
  }

  @Test
  public void hasApproval_notMatchingIfChangeIsClosed() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    Change change = createChange("Change Adding A File", path, "file content").getChange().change();
    String changeId = change.getKey().get();

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);
    assertQuery("has:approval_code-owners", change);

    // Approve and submit.
    requestScopeOperations.setApiUser(admin.id());
    approve(changeId);
    gApi.changes().id(changeId).current().submit();
    assertQuery("has:approval_code-owners");
  }

  @Test
  public void hasApproval_internalServerError() throws Exception {
    ChangeData changeData = createChange().getChange();

    // Create a ChangeData without change notes to trigger an error.
    // Set change and current patch set, so that this info can be included into the error message.
    ChangeData changeDataWithoutChangeNotes = mock(ChangeData.class);
    when(changeDataWithoutChangeNotes.change()).thenReturn(changeData.change());
    when(changeDataWithoutChangeNotes.currentPatchSet()).thenReturn(changeData.currentPatchSet());

    CodeOwnersInternalServerErrorException exception =
        assertThrows(
            CodeOwnersInternalServerErrorException.class,
            () ->
                codeOwnerApprovalHasOperand
                    .create(changeQueryBuilder)
                    .asMatchable()
                    .match(changeDataWithoutChangeNotes));
    assertThat(exception)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Failed to evaluate code owner statuses for patch set %d of change %d.",
                changeData.change().currentPatchSetId().get(), changeData.change().getId().get()));
  }

  @Test
  public void hasApproval_ruleErrorForNonParsableCodeOwnerConfig() throws Exception {
    String nameOfInvalidCodeOwnerConfigFile = getCodeOwnerConfigFileName();
    createNonParseableCodeOwnerConfig(nameOfInvalidCodeOwnerConfigFile);

    ChangeData changeData = createChange().getChange();

    assertThat(
            codeOwnerApprovalHasOperand.create(changeQueryBuilder).asMatchable().match(changeData))
        .isFalse();
  }

  @Test
  public void hasEnabled_matchingWhenCodeOwnersIsEnabledForTheChange() throws Exception {
    Change change =
        createChange("Change Adding A File", "foo/bar.baz", "file content").getChange().change();
    String changeId = change.getKey().get();
    assertQuery("has:enabled_code-owners", change);
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);
    assertQuery("has:enabled_code-owners", change);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  public void hasEnabled_notMatchingWhenCodeOwnersIsDisabledForTheChange() throws Exception {
    Change change =
        createChange("Change Adding A File", "foo/bar.baz", "file content").getChange().change();
    String changeId = change.getKey().get();
    assertQuery("has:enabled_code-owners");
    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);
    assertQuery("has:enabled_code-owners");
  }

  protected List<ChangeInfo> assertQuery(Object query, Change... changes) throws Exception {
    return assertQuery(newQuery(query), changes);
  }

  protected List<ChangeInfo> assertQuery(QueryRequest query, Change... changes) throws Exception {
    return assertQueryByIds(
        query, Arrays.stream(changes).map(Change::getId).toArray(Change.Id[]::new));
  }

  protected List<ChangeInfo> assertQueryByIds(QueryRequest query, Change.Id... changes)
      throws Exception {
    List<ChangeInfo> result = query.get();
    Iterable<Change.Id> ids = ids(result);
    assertWithMessage(format(query.getQuery(), ids, changes))
        .that(ids)
        .containsExactlyElementsIn(Arrays.asList(changes))
        .inOrder();
    return result;
  }

  protected static Iterable<Change.Id> ids(Change... changes) {
    return Arrays.stream(changes).map(Change::getId).collect(toList());
  }

  protected static Iterable<Change.Id> ids(Iterable<ChangeInfo> changes) {
    return Streams.stream(changes).map(c -> Change.id(c._number)).collect(toList());
  }

  protected QueryRequest newQuery(Object query) {
    return gApi.changes().query(query.toString());
  }

  private String format(String query, Iterable<Change.Id> actualIds, Change.Id... expectedChanges)
      throws RestApiException {
    return "query '"
        + query
        + "' with expected changes "
        + format(Arrays.asList(expectedChanges))
        + " and result "
        + format(actualIds);
  }

  private String format(Iterable<Change.Id> changeIds) throws RestApiException {
    return format(changeIds.iterator());
  }

  private String format(Iterator<Change.Id> changeIds) throws RestApiException {
    StringBuilder b = new StringBuilder();
    b.append("[");
    while (changeIds.hasNext()) {
      Change.Id id = changeIds.next();
      ChangeInfo c = gApi.changes().id(id.get()).get();
      b.append("{")
          .append(id)
          .append(" (")
          .append(c.changeId)
          .append("), ")
          .append("dest=")
          .append(BranchNameKey.create(Project.nameKey(c.project), c.branch))
          .append(", ")
          .append("status=")
          .append(c.status)
          .append(", ")
          .append("lastUpdated=")
          .append(c.updated.getTime())
          .append("}");
      if (changeIds.hasNext()) {
        b.append(", ");
      }
    }
    b.append("]");
    return b.toString();
  }
}
