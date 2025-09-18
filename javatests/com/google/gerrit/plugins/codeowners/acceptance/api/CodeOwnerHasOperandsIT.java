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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.NOT_APPLICABLE;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.SATISFIED;
import static com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status.UNSATISFIED;
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
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.Changes.QueryRequest;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo.Status;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersIT;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerApprovalHasOperand;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
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

    configSubmitRequirement(
        project,
        SubmitRequirement.builder()
            .setName("Code-Owner-Approval")
            .setApplicabilityExpression(SubmitRequirementExpression.of("has:enabled_code-owners"))
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create("has:approval_code-owners"))
            .setAllowOverrideInChildProjects(false)
            .build());
  }

  @Test
  public void hasApproval_notSupportedInSearchQueries() throws Exception {
    Exception thrown =
        assertThrows(BadRequestException.class, () -> assertQuery("has:approval_code-owners"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Operator 'has:approval_code-owners' cannot be used in queries");
  }

  @Test
  public void hasEnabled_notSupportedInSearchQueries() throws Exception {
    Exception thrown =
        assertThrows(BadRequestException.class, () -> assertQuery("has:enabled_code-owners"));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo("Operator 'has:enabled_code-owners' cannot be used in queries");
  }

  @Test
  public void hasApproval_satisfied() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    ChangeData changeData = createChange("Change Adding A File", path, "file content").getChange();
    int changeId = changeData.change().getChangeId();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
    assertSubmitRequirement(changeInfo.submitRequirements, "Code-Owner-Approval", UNSATISFIED);

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeData.change().getKey().get());
    changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
    assertSubmitRequirement(changeInfo.submitRequirements, "Code-Owner-Approval", SATISFIED);
  }

  @Test
  public void hasApproval_unsatisfiedIfChangeIsClosed() throws Exception {
    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch("master")
        .folderPath("/foo/")
        .addCodeOwnerEmail(user.email())
        .create();

    String path = "foo/bar.baz";
    ChangeData changeData = createChange("Change Adding A File", path, "file content").getChange();
    int changeId = changeData.change().getChangeId();

    // Add a Code-Review+1 from a code owner (by default this counts as code owner approval).
    requestScopeOperations.setApiUser(user.id());
    recommend(changeData.change().getKey().get());
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
    assertSubmitRequirement(changeInfo.submitRequirements, "Code-Owner-Approval", SATISFIED);

    // Approve and submit.
    requestScopeOperations.setApiUser(admin.id());
    approve(changeData.change().getKey().get());
    gApi.changes().id(changeId).current().submit();
    changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
    // When the change is merged, submit requirement results are persisted in NoteDb. Later lookups
    // return the persisted snapshot.
    assertSubmitRequirement(changeInfo.submitRequirements, "Code-Owner-Approval", SATISFIED);
  }

  @Test
  public void hasApproval_internalServerError() throws Exception {
    ChangeData changeData = createChange().getChange();

    // Create a ChangeData without change notes to trigger an error.
    // Set change and current patch set, so that this info can be included into the error message.
    ChangeData changeDataWithoutChangeNotes = mock(ChangeData.class);
    when(changeDataWithoutChangeNotes.change()).thenReturn(changeData.change());
    when(changeDataWithoutChangeNotes.currentPatchSet()).thenReturn(changeData.currentPatchSet());

    StorageException exception =
        assertThrows(
            StorageException.class,
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
    ChangeInfo changeInfo =
        gApi.changes()
            .id(changeData.change().getChangeId())
            .get(ListChangesOption.SUBMIT_REQUIREMENTS);
    // Requirement is unsatisfied if a relevant code owner config file is not parseable and hence
    // the submit rule cannot be evaluated.
    assertSubmitRequirement(changeInfo.submitRequirements, "Code-Owner-Approval", UNSATISFIED);
  }

  @Test
  @GerritConfig(name = "plugin.code-owners.disabled", value = "true")
  public void hasEnabled_notMatchingWhenCodeOwnersIsDisabledForTheChange() throws Exception {
    Change change =
        createChange("Change Adding A File", "foo/bar.baz", "file content").getChange().change();
    String changeId = change.getKey().get();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
    assertSubmitRequirement(changeInfo.submitRequirements, "Code-Owner-Approval", NOT_APPLICABLE);

    requestScopeOperations.setApiUser(user.id());
    recommend(changeId);
    changeInfo = gApi.changes().id(changeId).get(ListChangesOption.SUBMIT_REQUIREMENTS);
    assertSubmitRequirement(changeInfo.submitRequirements, "Code-Owner-Approval", NOT_APPLICABLE);
  }

  private List<ChangeInfo> assertQuery(Object query, Change... changes) throws Exception {
    QueryRequest queryRequest = newQuery(query);
    Change.Id[] changeArray = Arrays.stream(changes).map(Change::getId).toArray(Change.Id[]::new);
    List<ChangeInfo> result = queryRequest.get();
    Iterable<Change.Id> ids = ids(result);
    assertWithMessage(format(queryRequest.getQuery(), ids, changeArray))
        .that(ids)
        .containsExactlyElementsIn(Arrays.asList(changeArray))
        .inOrder();
    return result;
  }

  private static Iterable<Change.Id> ids(Iterable<ChangeInfo> changes) {
    return Streams.stream(changes).map(c -> Change.id(c._number)).collect(toList());
  }

  private QueryRequest newQuery(Object query) {
    return gApi.changes().query(query.toString());
  }

  private void assertSubmitRequirement(
      Collection<SubmitRequirementResultInfo> requirements, String name, Status status) {
    for (SubmitRequirementResultInfo requirement : requirements) {
      if (requirement.name.equals(name) && requirement.status == status) {
        return;
      }
    }
    throw new AssertionError(
        String.format(
            "Could not find submit requirement %s with status %s (results = %s)",
            name,
            status,
            requirements.stream()
                .map(r -> String.format("%s=%s", r.name, r.status))
                .collect(toImmutableList())));
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
    Iterator<Change.Id> changeIdsItr = changeIds.iterator();
    StringBuilder b = new StringBuilder();
    b.append("[");
    while (changeIdsItr.hasNext()) {
      Change.Id id = changeIdsItr.next();
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
      if (changeIdsItr.hasNext()) {
        b.append(", ");
      }
    }
    b.append("]");
    return b.toString();
  }
}
