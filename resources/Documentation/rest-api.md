# REST API

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

The response includes the configuration of all branches. If a caller is
interested in a particular branch only, the [Get Code Owner Branch
Config](#get-code-owner-branch-config) REST endpoint should be used instead, as
that REST endpoint is much faster if the project contains many branches.

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
    "general": {
      "merge_commit_strategy": "ALL_CHANGED_FILES"
    },
    "status": {
      "disabled_branches": [
        "refs/meta/config"
      ]
    },
    "backend": {
      "id": "find-owners",
      "ids_by_branch": {
        "refs/heads/experimental": "proto"
      }
    },
    "required_approval": {
      "label": "Code-Review",
      "value": 1
    },
    "override_approval": [
      {
        "label": "Owners-Override",
        "value": 1
      }
    ]
  }
```

### <a id="update-code-owner-project-config">Update Code Owner Project Config
_'PUT /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/code_owners.project_config'_

Updates the code owner project configuration.

The configuration parameters that should be updated must be specified in the
request body in a [CodeOwnerProjectConfigInput](#code-owner-project-config-info)
entity.

#### Request

```
  PUT /projects/foo%2Fbar/code_owners.project_config HTTP/1.0
  Content-Type: application/json; charset=UTF-8

  {
    "disabled": true
  }
```

#### Response

As a response the updated code owner project config is returned as
[CodeOwnerProjectConfigInfo](#code-owner-project-config-info) entity.

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "status": {
      "disabled": true
    }
  }
```

### <a id="check-code-owner-config-files">Check Code Owner Config Files
_'POST /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/code_owners.check_config'_

Checks/validates the code owner config files in a project.

Requires that the caller is an owner of the project.

Input options can be set in the request body as a
[CheckCodeOwnerConfigFilesInput](#check-code-owner-config-files-input) entity.

No validation is done for branches for which the code owner functionality is
[disabled](config.html#codeOwnersDisabledBranch), unless
`validate_disabled_branches` is set to `true` in the
[input](#check-code-owner-config-files-input).

As a response a map is returned that maps a branch name to a map that maps an
owner configuration file path to a list of
[ConsistencyProblemInfo](../../../Documentation/rest-api-config.html#consistency-problem-info)
entities.

Code owner config files that have no issues are omitted from the response.

#### Request

```
  POST /projects/foo%2Fbar/code_owners.check_config HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "refs/heads/master": {
      "/OWNERS": [
        {
          "status": "ERROR",
          "message": "code owner email 'foo@example.com' in '/OWNERS' cannot be resolved for John Doe"
        },
        {
          "status": "ERROR",
          "message": "code owner email 'bar@example.com' in '/OWNERS' cannot be resolved for John Doe"
        }
      ],
      "/foo/OWNERS": [
        {
          "status": "ERROR",
          "message": "invalid global import in '/foo/OWNERS': '/not-a-code-owner-config' is not a code owner config file"
        }
      ]
    },
    "refs/heads/stable-1.0" {},
    "refs/heads/stable-1.1" {
      "/foo/OWNERS": [
        {
          "status": "ERROR",
          "message": "invalid global import in '/foo/OWNERS': '/not-a-code-owner-config' is not a code owner config file"
        }
      ]
    }
  }
```

## <a id="branch-endpoints">Branch Endpoints

### <a id="get-code-owner-branch-config">Get Code Owner Branch Config
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners.branch_config'_

Gets the code owner branch configuration.

As a response a [CodeOwnerBranchConfigInfo](#code-owner-branch-config-info)
entity is returned that describes the code owner branch configuration.

#### Request

```
  GET /projects/foo%2Fbar/branches/master/code_owners.branch_config HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "general": {
      "merge_commit_strategy": "ALL_CHANGED_FILES"
    },
    "backend_id": "find-owners",
    "required_approval": {
      "label": "Code-Review",
      "value": 1
    },
    "override_approval": [
      {
        "label": "Owners-Override",
        "value": 1
      }
    ]
  }
```

### <a id="list-code-owner-config-files">List Code Owner Config Files
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners.config_files/'_

Lists the code owner config files in a branch.

This REST endpoint scans all code owner config files in the branch and it is
expected that it can take a long time if the branch contains many files. This is
why this REST endpoint must not be used in any critical paths where performance
matters.

The following request parameters can be specified:

| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `include-non-parsable-files` | optional | Includes non-parseable code owner config files in the response. By default `false`. Cannot be used in combination with the `email` option.
| `email`     | optional | Code owner email that must appear in the returned code owner config files.
| `path`      | optional | Path glob that must be matched by the returned code owner config files.

#### Request

```
  GET /projects/foo%2Fbar/branches/master/code_owners.config_files HTTP/1.0
```

As response the paths of the code owner config files are returned as a list. The
result also includes code owner config that use name prefixes
('\<prefix\>_OWNERS') or name extensions ('OWNERS_\<extension\>').

Non-parseable code owner config files are omitted from the response, unless the
`include-non-parsable-files` option was set.

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  [
    "/OWNERS",
    "/foo/OWNERS",
    "/foo/bar/baz/OWNERS",
    "/foo/bar/baz/OWNERS_BUILD"
  ]
```

### <a id="check-code-owner">Check Code Owner
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners.check/'_

Checks the code ownership of a user for a path in a branch.

The following request parameters can be specified:

| Field Name  |           | Description |
| ----------- | --------- | ----------- |
| `email`     | mandatory | Email for which the code ownership should be checked.
| `path`      | mandatory | Path for which the code ownership should be checked.
| `change`    | optional  | Change for which permissions should be checked. If not specified change permissions are not checked.
| `user`      | optional  | User for which the code owner visibility should be checked. If not specified the code owner visibility is not checked. Can be used to investigate why a code owner is not shown/suggested to this user.

Requires that the caller has the [Check Code Owner](#checkCodeOwner) or the
[Administrate Server](../../../Documentation/access-control.html#capability_administrateServer)
global capability.

This REST endpoint is intended to investigate code owner configurations that do
not work as intended. The response contains debug logs that may point out issues
with the code owner configuration. For example, with this REST endpoint it is
possible to find out why a certain email that is listed as code owner in a code
owner config file is ignored (e.g. because it is ambiguous or because it belongs
to an inactive account).

#### Request

```
  GET /projects/foo%2Fbar/branches/master/code_owners.check?email=xyz@example.com&path=/foo/bar/baz.md HTTP/1.0
```

#### Response

As response a [CodeOwnerCheckInfo](#code-owner-check-info) entity is returned.

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "is_code_owner": false,
    "is_resolvable": false,
    "can_read_ref": true,
    "code_owner_config_file_paths": [
      "/OWNERS",
    ],
    "is_fallback_code_owner": false,
    "is_default_code_owner": false,
    "is_global_code_owner": false,
    "debug_logs": [
      "checking code owner config file foo/bar:master:/OWNERS",
      "found email xyz@example.com as code owner in /OWNERS",
      "trying to resolve email xyz@example.com",
      "resolving code owner reference CodeOwnerReference{email=xyz@example.com}",
      "all domains are allowed",
      "cannot resolve code owner email xyz@example.com: email is ambiguous",
      "email xyz@example.com is not a code owner of path '/foo/bar/baz.md'"
    ]
  }
```

### <a id="rename-email-in-code-owner-config-files">Rename Email In Code Owner Config Files
_'POST /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners.rename/'_

Renames an email in all code owner config files in the branch.

The old and new email must be specified in the request body as
[RenameEmailInput](#rename-email-input).

The old and new email must both belong to the same Gerrit account.

All updates are done atomically within one commit. The calling user will be the
author of this commit.

Requires that the calling user is a project owner
([Owner](../../../Documentation/access-control.html#category_owner) permission
on ‘refs/*’) or has
[direct push](../../../Documentation/access-control.html#category_push)
permissions for the branch.

#### Request

```
  POST /projects/foo%2Fbar/branches/master/code_owners.rename HTTP/1.0
```

#### Response

As response a [RenameEmailResultInfo](#rename-email-result-info) entity is
returned.

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "commit": {
      "commit": "",
      "parents": [
        {
          "commit": "1efe2c9d8f352483781e772f35dc586a69ff5646",
          "subject": "Fix Foo Bar"
        }
      ],
      "author": {
        "name": "John Doe",
        "email": "john.doe@example.com",
        "date": "2020-03-30 18:08:08.000000000",
        "tz": -420
      },
      "committer": {
        "name": "Gerrit Code Review",
        "email": "no-reply@gerritcodereview.com",
        "date": "2020-03-30 18:08:08.000000000",
        "tz": -420
      },
      "subject": "Rename email in code owner config files",
      "message": "Rename email in code owner config files"
    }
  }
```


### <a id="get-code-owner-config">[EXPERIMENTAL] Get Code Owner Config
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners.config/[\{path\}](#path)'_

Gets a code owner config for a path in a branch.

The code owner config is returned as
[CodeOwnerConfigInfo](#code-owner-config-info) entity

This REST endpoint is experimental which means that the response format is
likely still going to be changed. It is only available if
[experimental REST endpoints are enabled](config.html#pluginCodeOwnersEnableExperimentalRestEndpoints)
in `gerrit.config`.

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

Code owners that

* are inactive
* are not visible to the calling user (according to
[accounts.visibility](../../../Documentation/config-gerrit.html#accounts.visibility)
setting)
* are referenced by non-visible secondary emails
* are not resolvable (emails for which no Gerrit account exists)
* are ambiguous (the same email is assigned to multiple accounts)
* are referenced by an email with a disallowed domain (see
  [allowedEmailDomain configuration](config.html#pluginCodeOwnersAllowedEmailDomain))
* do not have read access to the branch
* [fallback code owners](config.html#pluginCodeOwnersFallbackCodeOwners)

are omitted from the result.

The following request parameters can be specified:

| Field Name   |          | Description |
| ------------ | -------- | ----------- |
| `o`          | optional | [Account option](../../../Documentation/rest-api-accounts.html#query-options) that controls which fields in the returned accounts should be populated. Can be specified multiple times. If not given, only the `_account_id` field for the account ID is populated.
| `O`          | optional | [Account option](../../../Documentation/rest-api-accounts.html#query-options) in hex. For the explanation see `o` parameter.
| `limit`\|`n` | optional | Limit defining how many code owners should be returned at most. By default 10.
| `seed`       | optional | Seed, as a long value, that should be used to shuffle code owners that have the same score. Can be used to make the sort order stable across several requests, e.g. to get the same set of random code owners for different file paths that have the same code owners. Important: the sort order is only stable if the requests use the same seed **and** the same limit. In addition, the sort order is not guaranteed to be stable if new accounts are created in between the requests, or if the account visibility is changed.
| `resolve-all-users` | optional | Whether code ownerships that are assigned to all users should be resolved to random users. If not set, `false` by default. Also see the [sorting example](#sortingExample) below to see how this parameter affects the returned code owners.
| `highest-score-only` | optional | Whether only code owners with the highest score should be returned. If not set, `false` by default.
| `debug`      | optional | Whether debug logs should be included into the response. Requires the [Check Code Owner](#checkCodeOwner) global capability.
| `revision`   | optional | Revision from which the code owner configs should be read as commit SHA1. Can be used to read historic code owners from this branch, but imports from other branches or repositories as well as default and global code owners from `refs/meta/config` are still read from the current revisions. If not specified the code owner configs are read from the HEAD revision of the branch. Not supported for getting code owners for a path in a change.

As a response a [CodeOwnersInfo](#code-owners-info) entity is returned that
contains a list of code owners as [CodeOwnerInfo](#code-owner-info) entities.
The returned code owners are sorted by an internal score that expresses how good
the code owners are considered as reviewers/approvers for the path. Code owners
with higher scores are returned first. If code owners have the same score the
order is random. If the path is owned by all users (e.g. the code ownership is
assigned to '*') and `resolve-all-users` is set to `true` a random set of
(visible) users is returned, as many as are needed to fill up the requested
limit.

#### <a id="scoringFactors">Scoring Factors

The following factors are taken into account for computing the scores of the
listed code owners:

* distance of the code owner config file that defines the code owner to the
  path for which code owners are listed (the lower the distance the better the
  code owner)
* whether the user is explicitly mentioned as a code owner in the code owner
  config file vs. the user being a code owner only because the code ownership
  has been assigned to all users (aka `*`)
* whether the code owner is a reviewer of the change (only when listing code
  owners for a change)

The distance score has a lower weight than the is-reviewer score, hence when
listing code owners for a change, code owners that are reviewers are always
returned first.

Other factors like OOO state, recent review activity or code authorship are not
considered.

**NOTE:** The scores for the code owners are not exposed via the REST API but
only influence the sort order.

#### <a id="sortingExample">Sorting Example

The returned code owners are sorted by an internal score that is computed from
multiple [scoring factors](#scoringFactors) (the higher the score the better).
Code owners that have the same score are ordered randomly.

E.g. lets’s say there are the following code owners and scores:

- User A -> score 0
- User B -> score 0
- User C -> score 1
- `*` (aka all users) -> score 1
- User D -> score 2
- User E -> score 3
- User F -> score 4

If the request is done with `resolve-all-users=true` and `limit=5` the following
code owners are returned in this order:

1\. + 2. [score=0] User A and User B (random order since they have the same score)\
3\. [score=1] User C\
4\. + 5. [score=1] 2 Random Users (because `*` is resolved to random users since `resolve-all-users` is `true`)\
* `owned_by_all_users` in the response is `true`

If the request is done with `resolve-all-users=false` and `limit=5` the following
code owners are returned in this order:

1\. + 2. [score=0] User A and User B (random order since they have the same score)\
3\. [score=1] User C\
4\. [score=2] User D\
5\. [score=3] User E\
* `owned_by_all_users` in the response is `true`

#### <a id="rootOwnersFaq">Why are root code owners not suggested first?

Root code owners can normally approve all files in a repository. Due to this
change owners often want to add them as reviewers to their changes, since they
find it desirable to add as few code owners as possible. This is problematic
since it means that the root code owners would receive all reviews which likely
overloads them.

To avoid that the root code owners become the bottleneck, the @PLUGIN@ plugin
prefers local code owners and suggests them first (also see distant score
[above](#scoringFactors)). This means that root code owners are ranked lower and
often don't appear amongst the top suggestions.

Local code owners are also preferred because it is more likely that they are
experts of the modified code.

The same applies for [default code owners](config-guide.html#codeOwners).

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
  {
    "code_owners": [
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
  }
```

#### <a id="batch-list-code-owners"> Batch Request

There is no REST endpoint that allows to retrieve code owners for multiple
paths/files at once with a single batch request, but callers are expected to
send one request per path/file and do any necessary grouping of results (e.g.
grouping of files with the same code owners) on their own.

To ensure a stable sort order across requests for different paths/files it's
possible to set a seed on the requests that should be used to shuffle code
owners that have the same score (see `seed` request parameter above).

To speed up getting code owners for multiple paths/files callers are advised to
send batches of list code owners requests in parallel (e.g. 10) and start
processing the results as soon as they come in (this approach is faster than
having a batch REST endpoint, as the batch REST endpoint could only return
results after the server has computed code owners for all paths).

## <a id="change-endpoints"> Change Endpoints

### <a id="get-code-owner-status"> Get Code Owner Status
_'GET /changes/[\{change-id}](../../../Documentation/rest-api-changes.html#change-id)/code_owners.status'_

Gets the code owner statuses for the files in a change.

The code owner statuses are always listed for the files in the current revision
of the change (latest patch set).

The following request parameters can be specified:

| Field Name   |           | Description |
| ------------ | --------- | ----------- |
| `start`\|`S` | optional  | Number of file code owner statuses to skip. Allows to page over the file code owner statuses. By default 0.
| `limit`\|`n` | optional  | Limit defining how many file code owner statuses should be returned at most. By default 0 (= unlimited).

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
      }
    ]
  }
```

If the destination branch of a change no longer exists (e.g. because it was
deleted), `409 Conflict` is returned. Since the code owners are retrieved from
the destination branch, computing the code owner status is not possible, if the
destination branch is missing.

## <a id="revision-endpoints"> Revision Endpoints

### <a id="list-code-owners-for-path-in-change"> Suggest Code Owners for path in change
_'GET /changes/[\{change-id}](../../../Documentation/rest-api-changes.html#change-id)/revisions/[\{revison-id\}](../../../Documentation/rest-api-changes.html#revision-id)/code_owners/[\{path\}](#path)'_

Suggests accounts that are code owners of a file in a change revision.

The code owners are computed from the owner configuration at the tip of the
change's destination branch.

This REST endpoint has the exact same request and response format as the
[REST endpoint to list code owners for a path in a branch](#list-code-owners-for-path-in-branch),
but filters out code owners that which should be omitted from the code owner
suggestion.

The following code owners are filtered out additionally:

* [service users](#serviceUsers) (members of the `Service Users` group)
* the change owner (since the change owner cannot be added as reviewer)
* code owners that are annotated with
  [NEVER_SUGGEST](backend-find-owners.html#neverSuggest)

In addition, by default the change number is used as seed if none was specified.
This way the sort order on a change is always the same for files that have the
exact same code owners (requires that the limit is the same on all requests).

### <a id="get-owned-files">Get Owned Files
_'GET /changes/[\{change-id}](../../../Documentation/rest-api-changes.html#change-id)/revisions/[\{revison-id\}](../../../Documentation/rest-api-changes.html#revision-id)/owned_paths'_

Lists the files of the revision that are owned by the specified user (see `user`
request parameter below).

The following request parameters can be specified:

| Field Name   |           | Description |
| ------------ | --------- | ----------- |
| `start`\|`S` | optional  | Number of owned paths to skip. Allows to page over the owned files. By default 0.
| `limit`\|`n` | optional  | Limit defining how many owned files should be returned at most. By default 50.
| `user`       | mandatory | user for which the owned paths should be returned

#### Request

```
  GET /changes/20187/revisions/current/owned_paths?user=foo.bar@example.com HTTP/1.0
```

#### Response

As a response a [OwnedPathsInfo](#owned-paths-info) entity is returned.

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "owned_paths": [
      "/foo/bar/baz.md",
      "/foo/baz/bar.md",
    ]
  }
```

### <a id="check-code-owner-config-files-in-revision">Check Code Owner Config Files In Revision
_'POST /changes/[\{change-id}](../../../Documentation/rest-api-changes.html#change-id)/revisions/[\{revison-id\}](../../../Documentation/rest-api-changes.html#revision-id)/code_owners.check_config'_

Checks/validates the code owner config files in a revision that have been
modified.

The validation is performed from the perspective of the uploader, so that the
validation is exactly the same as the validation that will be done on submit.

Input options can be set in the request body as a
[CheckCodeOwnerConfigFilesInRevisionInput](#check-code-owner-config-files-in-revision-input)
entity.

As a response a map is returned that that maps an owner configuration file path
to a list of
[ConsistencyProblemInfo](../../../Documentation/rest-api-config.html#consistency-problem-info)
entities.

Code owner config files that were not modified in the revision are omitted from
the response.

#### Request

```
  POST /changes/20187/revisions/current/code_owners.check_config HTTP/1.0
```

#### Response

```
  HTTP/1.1 200 OK
  Content-Disposition: attachment
  Content-Type: application/json; charset=UTF-8

  )]}'
  {
    "/OWNERS": [
      {
        "status": "ERROR",
        "message": "code owner email 'foo@example.com' in '/OWNERS' cannot be resolved for John Doe"
      },
      {
        "status": "ERROR",
        "message": "code owner email 'bar@example.com' in '/OWNERS' cannot be resolved for John Doe"
      }
    ],
    "/foo/OWNERS": [
      {
        "status": "ERROR",
        "message": "invalid global import in '/foo/OWNERS': '/not-a-code-owner-config' is not a code owner config file"
      }
    ]
  }
```

## <a id="general-responses"> General Responses

All REST endpoints may return the following responses:

* `409 Conflict` is returned if a request cannot be executed due to:
    * an non-parseable code owner config file (in this case the project owners
      need to fix the code owner config file)
    * an invalid plugin configuration (in this case the project owners need to
      fix the code-owners plugin configuration)

## <a id="ids"> IDs

### <a id="path"> \{path\}

An arbitrary absolute path.

The leading '/' can be omitted.

The path may or may not exist in the branch.

## <a id="json-entities"> JSON Entities

### <a id="backend-info"> BackendInfo
The `BackendInfo` entity describes the code owner backend configuration.

| Field Name      |          | Description |
| --------------- | -------- | ----------- |
| `id`            || ID of the code owner backend that is configured for the project.
| `ids_by_branch` | optional | IDs of the code owner backends that are configured for individual branches as map of full branch names to code owner backend IDs. Only contains entries for branches for which a code owner backend is configured that differs from the backend that is configured for the project (see `id` field). Configurations for non-existing and non-visible branches are omitted. Not set if no branch specific backend configuration is returned.

---

### <a id="check-code-owner-config-files-input"> CheckCodeOwnerConfigFilesInput
The `CheckCodeOwnerConfigFilesInput` allows to set options for the [Check Code
Owner Config Files REST endpoint](#check-code-owner-config-files).

| Field Name                   |          | Description |
| ---------------------------- | -------- | ----------- |
| `validate_disabled_branches` | optional | Whether code owner config files in branches for which the code owners functionality is disabled should be validated too. By default unset, `false`.
| `branches`                   | optional | List of branches for which code owner config files should be validated. The `refs/heads/` prefix may be omitted. By default unset, which means that code owner config files in all branches should be validated.
| `path`                       | optional | Glob that limits the validation to code owner config files that have a path that matches this glob. By default unset, which means that all code owner config files should be validated.

---

### <a id="check-code-owner-config-files-in-revision-input"> CheckCodeOwnerConfigFilesInRevisionInput
The `CheckCodeOwnerConfigFilesInRevisionInput` allows to set options for the
[Check Code Owner Config Files In Revision REST endpoint](#check-code-owner-config-files-in-revision).

| Field Name                   |          | Description |
| ---------------------------- | -------- | ----------- |
| `path`                       | optional | Glob that limits the validation to code owner config files that have a path that matches this glob. By default unset, which means that all modified code owner config files should be validated.

---

### <a id="code-owner-check-info"> CodeOwnerCheckInfo
The `CodeOwnerCheckInfo` entity contains the result of checking the code
ownership of a user for a path in a branch.

| Field Name      | Description |
| --------------- | ----------- |
| `is_code_owner` | Whether the given email owns the specified path in the branch. True if: a) the given email is resolvable (see field `is_resolvable') and b) any code owner config file assigns codeownership to the email for the path (see field `code_owner_config_file_paths`) or the email is configured as default code owner (see field `is_default_code_owner` or the email is configured as global code owner (see field `is_global_code_owner`) or the user is a fallback code owner (see field `is_fallback_code_owner`).
| `is_resolvable` | Whether the given email is resolvable for the specified user or the calling user if no user was specified.
| `can_read_ref` | Whether the user to which the given email was resolved has read permissions on the branch. Not set if the given email is not resolvable or if the given email is the all users wildcard (aka '*').
| `can_see_change`| Whether the user to which the given email was resolved can see the specified change. Not set if the given email is not resolvable, if the given email is the all users wildcard (aka '*') or if no change was specified.
| `can_approve_change`| Whether the user to which the given email was resolved can code-owner approve the specified change. Being able to code-owner approve the change means that the user has permissions to vote on the label that is [required as code owner approval](config.html#pluginCodeOwnersRequiredApproval). Other permissions are not considered for computing this flag. In particular missing read permissions on the change don't have any effect on this flag. Whether the user misses read permissions on the change (and hence cannot apply the code owner approval) can be seen from the `can_see_change` flag. Not set if the given email is not resolvable, if the given email is the all users wildcard (aka '*') or if no change was specified.
| `code_owner_config_file_paths` | Paths of the code owner config files that assign code ownership to the specified email and path as a list. Note that if code ownership is assigned to the email via a code owner config files, but the email is not resolvable (see field `is_resolvable` field), the user is not a code owner.
| `is_fallback_code_owner` | Whether the given email is a fallback code owner of the specified path in the branch. True if: a) the given email is resolvable (see field `is_resolvable') and b) no code owners are defined for the specified path in the branch and c) parent code owners are not ignored and d) the user is a fallback code owner according to the [configured fallback code owner policy](config.html#pluginCodeOwnersFallbackCodeOwners)
| `is_default_code_owner` | Whether the given email is configured as a default code owner in the code owner config file in `refs/meta/config`. Note that if the email is configured as default code owner, but the email is not resolvable (see `is_resolvable` field), the user is not a code owner.
| `is_global_code_owner` | Whether the given email is configured as a global
code owner. Note that if the email is configured as global code owner, but the email is not resolvable (see `is_resolvable` field), the user is not a code owner.
| `is_owned_by_all_users` | Whether the the specified path in the branch is owned by all users (aka `*`).
| `annotation` | Annotations that were set for the user. Sorted alphabetically.
| `debug_logs` | List of debug logs that may help to understand why the user is or isn't a code owner.

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

### <a id="code-owner-branch-config-info"> CodeOwnerBranchConfigInfo
The `CodeOwnerBranchConfigInfo` entity describes the code owner branch
configuration.

| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `general`   | optional | The general code owners configuration as [GeneralInfo](#general-info) entity. Not set if `disabled` is `true`.
| `disabled`  | optional | Whether the code owners functionality is disabled for the branch. If `true` the code owners API is disabled and submitting changes doesn't require code owner approvals. Not set if `false`.
| `backend_id`| optional | ID of the code owner backend that is configured for the branch. Not set if `disabled` is `true`.
| `required_approval` | optional | The approval that is required from code owners to approve the files in a change as [RequiredApprovalInfo](#required-approval-info) entity. The required approval defines which approval counts as code owner approval. Any approval on this label with a value >= the given value is considered as code owner approval. Not set if `disabled` is `true`.
| `override_approval` | optional | Approvals that count as override for the code owners submit check as a list of [RequiredApprovalInfo](#required-approval-info) entities (sorted alphabetically). If multiple approvals are returned, any of them is sufficient to override the code owners submit check. All returned override approvals are guarenteed to have distinct label names. Any approval on these labels with a value >= the given values is considered as code owner override. If unset, overriding the code owners submit check is disabled. Not set if `disabled` is `true`.

---

### <a id="code-owner-project-config-info"> CodeOwnerProjectConfigInfo
The `CodeOwnerProjectConfigInfo` entity describes the code owner project
configuration.

| Field Name |          | Description |
| ---------- | -------- | ----------- |
| `general`  | optional | The general code owners configuration as [GeneralInfo](#general-info) entity. Not set if `status.disabled` is `true`.
| `status`   | optional | The code owner status configuration as [CodeOwnersStatusInfo](#code-owners-status-info) entity. Contains information about whether the code owners functionality is disabled for the project or for any branch.
| `backend`  | optional | The code owner backend configuration as [BackendInfo](#backend-info) entity. Not set if `status.disabled` is `true`.
| `required_approval` | optional | The approval that is required from code owners to approve the files in a change as [RequiredApprovalInfo](#required-approval-info) entity. The required approval defines which approval counts as code owner approval. Not set if `status.disabled` is `true`.
| `override_approval` | optional | Approvals that count as override for the code owners submit check as a list of [RequiredApprovalInfo](#required-approval-info) entities. If multiple approvals are returned, any of them is sufficient to override the code owners submit check. All returned override approvals are guarenteed to have distinct label names. If unset, overriding the code owners submit check is disabled. Not set if `disabled` is `true`.

---

### <a id="code-owner-project-config-input"> CodeOwnerProjectConfigInput
The `CodeOwnerProjectConfigInput` entity specifies which parameters in the
`code-owner.project` file in `refs/meta/config` should be updated.

If a field in this input is not set, the corresponding parameter in the
`code-owners.config` file is not updated.

| Field Name |          | Description |
| ---------- | -------- | ----------- |
| `disabled` | optional | Whether the code owners functionality should be disabled/enabled for the project.
| `disabled_branch` | optional | List of branches for which the code owners functionality is disabled. Can be exact refs, ref patterns or regular expressions. Overrides any existing disabled branch configuration.
| `file_extension` | optional | The file extension that should be used for code owner config files in this project.
| `required_approval` | optional | The approval that is required from code owners. Must be specified in the format "\<label-name\>+\<label-value\>". If an empty string is provided the required approval configuration is unset. Unsetting the required approval means that the inherited required approval configuration or the default required approval (`Code-Review+1`) will apply. In contrast to providing an empty string, providing `null` (or not setting the value) means that the required approval configuration is not updated.
| `override_approvals` | optional | The approvals that count as override for the code owners submit check. Must be specified in the format "\<label-name\>+\<label-value\>".
| `fallback_code_owners` | optional | Policy that controls who should own paths that have no code owners defined. Possible values are: `NONE`: Paths for which no code owners are defined are owned by no one. `PROJECT_OWNERS`: Paths for which no code owners are defined are owned by the project owners. `ALL_USERS`: Paths for which no code owners are defined are owned by all users.
| `global_code_owners` | optional | List of emails of users that should be code owners globally across all branches.
| `exempted_users` | optional | List of emails of users that should be exempted from requiring code owners approvals.
| `merge_commit_strategy` | optional | Strategy that defines for merge commits which files require code owner approvals. Can be `ALL_CHANGED_FILES` or `FILES_WITH_CONFLICT_RESOLUTION` (see [mergeCommitStrategy](config.html#pluginCodeOwnersMergeCommitStrategy) for an explanation of these values).
| `implicit_approvals` | optional | Whether an implicit code owner approval from the last uploader is assumed.
| `override_info_url` | optional | URL for a page that provides project/host-specific information about how to request a code owner override.
| `invalid_code_owner_config_info_url` | optional | URL for a page that provides project/host-specific information about how to deal with invalid code owner config files.
| `read_only` | optional | Whether code owner config files are read-only.
| `exempt_pure_reverts` | optional | Whether pure revert changes are exempted from needing code owner approvals for submit.
| `enable_validation_on_commit_received` | optional | Policy for validating code owner config files when a commit is received. Allowed values are `true` (the code owner config file validation is enabled and the upload of invalid code owner config files is rejected), `false` (the code owner config file validation is disabled, invalid code owner config files are not rejected) and `dry_run` (code owner config files are validated, but invalid code owner config files are not rejected).
| `enable_validation_on_submit` | optional | Policy for validating code owner config files when a change is submitted. Allowed values are `true` (the code owner config file validation is enabled and the submission of invalid code owner config files is rejected), `false` (the code owner config file validation is disabled, invalid code owner config files are not rejected) and `dry_run` (code owner config files are validated, but invalid code owner config files are not rejected).
| `reject_non_resolvable_code_owners` | optional | Whether modifications of code owner config files that newly add non-resolvable code owners should be rejected on commit received and submit.
| `reject_non_resolvable_imports` | optional | Whether modifications of code owner config files that newly add non-resolvable imports should be rejected on commit received an submit.
| `max_paths_in_change_messages` | optional | The maximum number of paths that are included in change messages. Setting the value to `0` disables including owned paths into change messages.

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

| Field Name         |          | Description |
| ------------------ | -------- | ----------- |
| `patch_set_number` |          | The number of the patch set for which the code owner statuses are returned.
| `file_code_owner_statuses` |  | List of the code owner statuses for the files in the change as [FileCodeOwnerStatusInfo](#file-code-owner-status-info) entities, sorted by new path, then old path.
| `more`             | optional | Whether the request would deliver more results if not limited. Not set if `false`.

### <a id="code-owners-status-info"> CodeOwnersStatusInfo
The `CodeOwnersStatusInfo` contains information about whether the code owners
functionality is disabled for a project or for any branch.

| Field Name |         | Description |
| ---------- | ------- | ----------- |
| `disabled` | optional | Whether the code owners functionality is disabled for the project. If `true` the code owners API is disabled and submitting changes doesn't require code owner approvals. Not set if `false`.
| `disabled_branches` | optional | Branches for which the code owners functionality is disabled. Configurations for non-existing and non-visible branches are omitted. Not set if the `disabled` field is `true` or if no branch specific status configuration is returned.

### <a id="code-owners-info"> CodeOwnersInfo
The `CodeOwnersInfo` entity contains information about a list of code owners.

| Field Name    |          | Description |
| ------------- | -------- | ----------- |
| `code_owners` |          | List of code owners as [CodeOwnerInfo](#code-owner-info) entities. The code owners are sorted by a score that is computed from mutliple [scoring factors](#scoringFactors).
| `owned_by_all_users` | optional | Whether the path is owned by all users. Not set if `false`.
| `debug_logs`  | optional | Debug logs that may help to understand why a user is or isn't suggested as a code owner. Only set if requested via `--debug`.

### <a id="file-code-owner-status-info"> FileCodeOwnerStatusInfo
The `FileCodeOwnerStatusInfo` entity describes the code owner statuses for a
file in a change.

| Field Name    |          | Description |
| ------------- | -------- | ----------- |
| `change_type` | optional | The type of the file modification. Can be `ADDED`, `MODIFIED`, `DELETED`, `RENAMED` or `COPIED`. Not set if `MODIFIED`. Renamed files might appear as separate addition and deletion or with type=RENAMED. Copied files might appear as addition or with type=COPIED.
| `old_path_status` | optional | The code owner status for the old path as [PathCodeOwnerStatusInfo](#path-code-owner-status-info) entity. Only set if `change_type` is `DELETED` or `RENAMED`.
| `new_path_status` | optional | The code owner status for the new path as [PathCodeOwnerStatusInfo](#path-code-owner-status-info) entity. Not set if `change_type` is `DELETED`.

### <a id="general-info"> GeneralInfo
The `GeneralInfo` entity contains general code owners configuration parameters.

| Field Name       |          | Description |
| ---------------- | -------- | ----------- |
| `file_extension` | optional | The file extension that is used for the code owner config files in this project. Not set if no file extension is used.
| `merge_commit_strategy` || Strategy that defines for merge commits which files require code owner approvals. Can be `ALL_CHANGED_FILES` or `FILES_WITH_CONFLICT_RESOLUTION` (see [mergeCommitStrategy](config.html#pluginCodeOwnersMergeCommitStrategy) for an explanation of these values).
| `implicit_approvals` | optional |  Whether an implicit code owner approval from the last uploader is assumed (see [enableImplicitApprovals](config.html#pluginCodeOwnersEnableImplicitApprovals) for details). When unset, `false`.
| `override_info_url` | optional | Optional URL for a page that provides project/host-specific information about how to request a code owner override.
| `invalid_code_owner_config_info_url` | optional | Optional URL for a page that provides project/host-specific information about how to deal with invalid code owner config files.
|`fallback_code_owners` || Policy that controls who should own paths that have no code owners defined. Possible values are: `NONE`: Paths for which no code owners are defined are owned by no one. `PROJECT_OWNERS`: Paths for which no code owners are defined are owned by the project owners. `ALL_USERS`: Paths for which no code owners are defined are owned by all users.

### <a id="owned-paths-info"> OwnedPathsInfo
The `OwnedPathsInfo` entity contains paths that are owned by a user.


| Field Name    |          | Description |
| ------------- | -------- | ----------- |
| `owned_paths` |          |The owned paths as absolute paths, sorted alphabetically.
| `more`        | optional | Whether the request would deliver more results if not limited. Not set if `false`.

### <a id="path-code-owner-status-info"> PathCodeOwnerStatusInfo
The `PathCodeOwnerStatusInfo` entity describes the code owner status for a path
in a change.

| Field Name         | Description |
| ------------------ | ----------- |
| `path` | The path to which the code owner status applies.
| `status` | The code owner status for the path. Can be 'INSUFFICIENT_REVIEWERS' (the path needs a code owner approval, but none of its code owners is currently a reviewer of the change), `PENDING` (a code owner of this path has been added as reviewer, but no code owner approval for this path has been given yet) or `APPROVED` (the path has been approved by a code owner or a code owners override is present).

---

### <a id="rename-email-input"> RenameEmailInput
The `RenameEmailInput` entity specifies how an email should be renamed.

| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `message`   | optional | Commit message that should be used for the commit that renames the email in the code owner config files. If not set the following default commit message is used: "Rename email in code owner config files"
| `old_email` || The old email that should be replaced with the new email.
| `new_email` || The new email that should be used to replace the old email.

---

### <a id="rename-email-result-info"> RenameEmailResultInfo
The `RenameEmailResultInfo` entity describes the result of the rename email REST
endpoint.

| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `commit`    | optional | The commit that did the email rename. Not set, if no update was necessary.

---

### <a id="required-approval-info"> RequiredApprovalInfo
The `RequiredApprovalInfo` entity describes an approval that is required for an
action.

| Field Name | Description |
| ---------- | ----------- |
| `label`    | The name of label on which an approval is required.
| `value`    | The voting value that is required on the label.

---

## <a id="capabilities">Capabilities

### <a id="checkCodeOwner">Check Code Owner

Global capability that allows a user to call the [Check Code
Owner](#check-code-owner) REST endpoint and use the `--debug` option of the
[List Code Owners](#list-code-owners-for-path-in-branch) REST endpoints.

Assigning this capability allows users to inspect code ownerships. This may
reveal accounts and secondary emails to the user that the user cannot see
otherwise. Hence this capability should only ge granted to trusted users.

Administrators have this capability implicitly assigned.

The same as all global capabilities, the `Check Code Owner` global capability is
assigned on the `All-Project` project in the `Global Capabilities` access
section.

---

## <a id="serviceUsers">Service Users

Some of the @PLUGIN@ REST endpoints have special handling of code owners that
are service users:

* The [Suggest Code Owners for path in change](#list-code-owners-for-path-in-change)
  REST endpoint filters out code owners that are service users.

To detect service users the @PLUGIN@ plugin relies on the `Service Users` group.
This group should contain all service users, such as bots, and is maintained by
the host admins.

If you are a host admin, please make sure all bots that run against your host
are part of the `Service Users` group.

If you are a bot owner, please make sure your bot is part of the `Service Users`
group on all hosts it runs on.

To add users to the "Service Users" group, first ensure that the group exists on
your host. If it doesn't, create it. The name must exactly be `Service Users`.

To create a group, use the Gerrit UI: `BROWSE` -> `Groups` -> `CREATE NEW`.

Then, add the bots as members in this group. Alternatively, add an existing
group that only contains bots as a subgroup of the `Service Users` group.

To add members or subgroups, use the Gerrit UI: `BROWSE` -> `Groups` ->
search for `Service Users` -> `Members`.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
