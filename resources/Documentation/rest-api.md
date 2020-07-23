# @PLUGIN@ - REST API

This page describes the code owners REST endpoints that are added by the
@PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

## <a id="project-endpoints">Project Endpoints

### <a id="get-code-owner-project-config">Get Code Owner Project Config
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/code_owners.project_config'_

Gets the code owner project configuration.

As a response a [CodeOwnerProjectConfigInfo](#code-owner-project-config-info)
entity is returned that describes the code owner project configuration.

#### Request

```
  GET /projects/foo%2Fbar/code_owners.project_config HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "backend": {
      "id": "find-owners",
      "ids_by_branch": {
        "refs/heads/experimental": "proto"
      }
    },
    "required_approval": {
      "label": "Code-Review",
      "value": 1
    }
  }
```

## <a id="branch-endpoints">Branch Endpoints

### <a id="get-code-owner-config">Get Code Owner Config
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners.config/[\{path\}](#path)'_

Gets a code owner config for a path in a branch.

The code owner config is returned as
[CodeOwnerConfigInfo](#code-owner-config-info) entity

#### Request

```
  GET /projects/foo%2Fbar/branches/master/code_owners.config/%2Fdocs%2Fconfig HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "code_sets": [
      "code_owners" [
        {
          "email": "jane.roe@example.com"
        },
        {
          "email": "john.doe@example.com"
        }
      ]
    ]
  }
```

If the path does not exist or if no code owner config exists for the path
'`204 No Content`' is returned.

### <a id="list-code-owners-for-path-in-branch"> List Code Owners for path in branch
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners/[\{path\}](#path)'_

Lists the accounts that are code owners of a file or folder in a branch.

The code owners are computed from the owner configuration at the tip of the
specified branch.

Code owners that are

* not visible to the calling user (according to
[accounts.visibility](../../../Documentation/config-gerrit.html#accounts.visibility)
setting),
* are referenced by non-visible secondary emails
* not resolvable (emails for which no Gerrit account exists) or
* ambiguous (the same email is assigned to multiple accounts)

are omitted from the result.

The following request parameters can be specified:

| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `o`         | optional | [Account option](../../../Documentation/rest-api-accounts.html#query-options) that controls which fields in the returned accounts should be populated. Can be specified multiple times. If not given, only the `_account_id` field for the account ID is populated.
| `O`         | optional | [Account option](../../../Documentation/rest-api-accounts.html#query-options) in hex. For the explanation see `o` parameter.
| `limit`\|`n` | optional | Limit defining how many code owners should be returned at most. By default 10.

As a response a list of [CodeOwnerInfo](#code-owner-info) entities is returned.
The returned code owners are sorted by an internal score that expresses how good
the code owners are considered as reviewers/approvers for the path. Code owners
with higher scores are returned first. If code owners have the same score the
order is random.

#### Request

```
  GET /projects/foo%2Fbar/branches/master/code_owners/docs%2Findex.md HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    {
      "account": {
        "_account_id": 1000096
      }
    },
    {
      "account": {
        "_account_id": 1001439
      },
    }
  ]
```

If the code owners functionality is disabled for the branch '405 Method Not
Allowed' is returned.

## <a id="change-endpoints"> Change Endpoints

### <a id="get-code-owner-status"> Get Code Owner Status
_'GET /changes/[\{change-id}](../../../Documentation/rest-api-changes.html#change-id)/code_owners.status'_

Gets the code owner statuses for the files in a change.

The code owner statuses are always listed for the files in the current revision
of the change (latest patch set).

The code owner statuses are returned as a
[CodeOwnerStatusInfo](#code-owner-status-info) entity.

#### Request

```
  GET /changes/275378/code_owners.status HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "patch_set_number": 2,
    "file_code_owner_statuses": [
      {
        "change_type": "ADDED",
        "new_path_status" {
          "path": "docs/readme.md",
          "status": "APPROVED"
        }
      },
      {
        "change_type": "DELETED",
        "old_path_status" {
          "path": "docs/todo.txt",
          "status": "PENDING"
        }
      },
      {
        "change_type": "RENAMED",
        "old_path_status" {
          "path": "user-introduction.txt",
          "status": "INSUFFICIENT_REVIEWERS"
        },
        "new_path_status" {
          "path": "docs/user-intro.md",
          "status": "APPROVED"
        }
      }
    ]
  }
```

If the code owners functionality is disabled for the branch '405 Method Not
Allowed' is returned.

## <a id="revision-endpoints"> Revision Endpoints

### <a id="list-code-owners-for-path-in-change"> List Code Owners for path in change
_'GET /changes/[\{change-id}](../../../Documentation/rest-api-changes.html#change-id)/revisions/[\{revison-id\}](../../../Documentation/rest-api-changes.html#revision-id)/code_owners/[\{path\}](#path)'_

Lists the accounts that are code owners of a file in a change revision.

The code owners are computed from the owner configuration at the tip of the
change's destination branch.

This REST endpoint has the exact same request and response format as the
[REST endpoint to list code owners for a path in a branch](#list-code-owners-for-path-in-branch).

## <a id="ids"> IDs

### <a id="path"> \{path\}

An arbitrary absolute path.

The leading'/' can be omitted.

The path may or may not exist in the branch.

## <a id="json-entities"> JSON Entities

### <a id="backend-info"> BackendInfo
The `BackendInfo` entity describes the code owner backend configuration.

| Field Name      |          | Description |
| --------------- | -------- | ----------- |
| `id`            || ID of the code owner backend that is configured for the project.
| `ids_by_branch` | optional | IDs of the code owner backends that are configured for individual branches as map of full branch names to code owner backend IDs. Only contains entries for branches for which a code owner backend is configured that differs from the backend that is configured for the project (see `id` field). Configurations for non-existing and non-visible branches are omitted. Not set if no branch specific backend configuration is returned.

---

### <a id="code-owner-config-info"> CodeOwnerConfigInfo
The `CodeOwnerConfigInfo` entity contains information about a code owner config
for a path.


| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `ignore_parent_code_owners` | optional, not set if `false` | Whether code owners from parent code owner configs (code owner configs in parent folders) should be ignored.
| `code_owner_sets` | optional | A list of code owner sets as [CodeOwnerSetInfo](#code-owner-set-info) entities.

---

### <a id="code-owner-info"> CodeOwnerInfo
The `CodeOwnerInfo` entity contains information about a code owner.

| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `account`   | optional | The account of the code owner as an [AccountInfo](../../../Documentation/rest-api-accounts.html#account-info) entity. At the moment the `account` field is always set, but it's marked as optional as in the future we may also return groups as code owner and then the `account` field would be unset.

---

### <a id="code-owner-project-config-info"> CodeOwnerProjectConfigInfo
The `CodeOwnerProjectConfigInfo` entity describes the code owner project
configuration.

| Field Name |          | Description |
| ---------- | -------- | ----------- |
| `status`   | optional | The code owner status configuration as [CodeOwnersStatusInfo](#code-owners-status-info) entity. Contains information about whether the code owners functionality is disabled for the project or for any branch. Not set if the code owners functionality is neither disabled for the project nor for any branch.
| `backend`  || The code owner backend configuration as [BackendInfo](#backend-info) entity.
| `required_approval` || The approval that is required from code owners to approve the files in a change as [RequiredApprovalInfo](#required-approval-info) entity. The required approval defines which approval counts as code owner approval.

---

### <a id="code-owner-reference-info"> CodeOwnerReferenceInfo
The `CodeOwnerReferenceInfo` entity contains information about a code owner
reference in a code owner config.

| Field Name | Description |
| ---------- | ----------- |
| `email`    | The email of the code owner.

---

### <a id="code-owner-set-info"> CodeOwnerSetInfo
The `CodeOwnerSetInfo` entity defines a set of code owners.

| Field Name    |          | Description |
| ------------- | -------- | ----------- |
| `code_owners` | optional | The list of code owners as [CodeOwnerReferenceInfo](#code-owner-reference-info) entities.

### <a id="code-owner-status-info"> CodeOwnerStatusInfo
The `CodeOwnerStatusInfo` entity describes the code owner statuses for the files
in a change.

| Field Name         | Description |
| ------------------ | ----------- |
| `patch_set_number` | The number of the patch set for which the code owner statuses are returned.
| `file_code_owner_statuses` | List of the code owner statuses for the files in the change as [FileCodeOwnerStatusInfo](#file-code-owner-status-info) entities, sorted by new path, then old path.

### <a id="code-owners-status-info"> CodeOwnersStatusInfo
The `CodeOwnersStatusInfo` contains information about whether the code owners
functionality is disabled for a project or for any branch.

| Field Name |         | Description |
| ---------- | ------- | ----------- |
| `disabled` | optinal | Whether the code owners functionality is disabled for the project. If `true` the code owners API is disabled and submitting changes doesn't require code owner approvals. Not set if `false`.
| `disabled_branches` | optional | Branches for which the code owners functionality is disabled. Configurations for non-existing and non-visible branches are omitted. Not set if the `disabled` field is `true` or if no branch specific status configuration is returned.

### <a id="file-code-owner-status-info"> FileCodeOwnerStatusInfo
The `FileCodeOwnerStatusInfo` entity describes the code owner statuses for a
file in a change.

| Field Name    |          | Description |
| ------------- | -------- | ----------- |
| `change_type` | optional | The type of the file modification. Can be `ADDED`, `MODIFIED`, `DELETED`, `RENAMED` or `COPIED`. Not set if `MODIFIED`.
| `old_path_status` | optional | The code owner status for the old path as [PathCodeOwnerStatusInfo](#path-code-owner-status-info) entity. Only set if `change_type` is `DELETED` or `RENAMED`.
| `new_path_status` | optional | The code owner status for the new path as [PathCodeOwnerStatusInfo](#path-code-owner-status-info) entity. Not set if `change_type` is `DELETED`.

### <a id="path-code-owner-status-info"> PathCodeOwnerStatusInfo
The `PathCodeOwnerStatusInfo` entity describes the code owner status for a path
in a change.

| Field Name         | Description |
| ------------------ | ----------- |
| `path` | The path to which the code owner status applies.
| `status` | The code owner status for the path. Can be 'INSUFFICIENT_REVIEWERS' (the path needs a code owner approval, but none of its code owners is currently a reviewer of the change), `PENDING` (a code owner of this path has been added as reviewer, but no code owner approval for this path has been given yet) or `APPROVED` (the path has been approved by a code owner or a code owners override is present).

---

### <a id="required-approval-info"> RequiredApprovalInfo
The `RequiredApprovalInfo` entity describes the approval that is required from
code owners to approve the files in a change. The required approval defines
which approval counts as code owner approval.

| Field Name | Description |
| ---------- | ----------- |
| `label`    | The name of label on which an approval from a code owner is required.
| `value`    | The voting value that is required on the label.

---

Part of [Gerrit Code Review](../../../Documentation/index.html)

