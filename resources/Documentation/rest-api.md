# @PLUGIN@ - REST API

This page describes the code owners REST endpoints that are added by the
@PLUGIN@ plugin.

Please also take note of the general information on the
[REST API](../../../Documentation/rest-api.html).

## <a id="branch-endpoints">Branch Endpoints

### <a id="get-code-owner-config">Get Code Owner Config
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners.config/[\{path\}](#path)'_

Gets a code owner config for a path in a branch.

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
    "code_owners": [
      {
        "email": "jane.roe@example.com"
      },
      {
        "email": "john.doe@example.com"
      }
    ]
  }
```

If the path does not exist or if no code owner config exists for the path
'`204 No Content`' is returned.

## <a id="branch-endpoints"> Branch Endpoints

### <a id="list-code-owners"> List Code Owners
_'GET /projects/[\{project-name\}](../../../Documentation/rest-api-projects.html#project-name)/branches/[\{branch-id\}](../../../Documentation/rest-api-projects.html#branch-id)/code_owners/[\{path\}](#path)'_

Lists the accounts that are code owners of a file or folder in a branch.

The code owners are computed from the owner configuration at the tip of the
specified branch.

Code owners that are

* not resolvable (emails for which no Gerrit account exists) or
* ambiguous (the same email is assigned to multiple accounts)

are omitted from the result.

The following request parameters can be specified:

| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `o`         | optional | [Account option](../../../Documentation/rest-api-accounts.html#query-options) that controls which fields in the returned accounts should be populated. Can be specified multiple times. If not given, only the `_account_id` field for the account ID is populated.
| `O`         | optional | [Account option](../../../Documentation/rest-api-accounts.html#query-options) in hex. For the explanation see `o` parameter.

As a response a list of [CodeOwnerInfo](#code-owner-info) entities is returned.

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

## <a id="ids"> IDs

### <a id="path"> \{path\}

An arbitrary absolute path.

The leading'/' can be omitted.

The path may or may not exist in the branch.

## <a id="json-entities"> JSON Entities

### <a id="code-owner-config-info"> CodeOwnerConfigInfo
The `CodeOwnerConfigInfo` entity contains information about a code owner config
for a path.


| Field Name    | Description |
| ------------- | ----------- |
| `code_owners` | The list of code owners as [CodeOwnerReferenceInfo](#code-owner-reference-info) entities.

---

### <a id="code-owner-info"> CodeOwnerInfo
The `CodeOwnerInfo` entity contains information about a code owner.

| Field Name  |          | Description |
| ----------- | -------- | ----------- |
| `account`   | optional | The account of the code owner as an [AccountInfo](../../../Documentation/rest-api-accounts.html#account-info) entity. At the moment the `account` field is always set, but it's marked as optional as in the future we may also return groups as code owner and then the `account` field would be unset.

---

### <a id="code-owner-reference-info"> CodeOwnerReferenceInfo
The `CodeOwnerReferenceInfo` entity contains information about a code owner
reference in a code owner config.


| Field Name | Description |
| ---------- | ----------- |
| `email`    | The email of the code owner.

---

Part of [Gerrit Code Review](../../../Documentation/index.html)

