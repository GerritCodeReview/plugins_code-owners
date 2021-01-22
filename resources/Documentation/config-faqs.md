# Config FAQ's

* [How to update the code-owners.config file for a project](#updateCodeOwnersConfig)
* [How to check if the code owners functionality is enabled for a project or branch](#checkIfEnabled)
* [How to avoid issues with code owner config files](#avoidIssuesWithCodeOwnerConfigs)
* [How to investigate issues with code owner config files](#investigateIssuesWithCodeOwnerConfigs)
* [How to setup code owner overrides](#setupOverrides)

## <a id="updateCodeOwnersConfig">How to update the code-owners.config file for a project

The project-level configuration of the `code-owners` plugin is done in the
`code-owners.config` file that is stored in the `refs/meta/config` branch of a
project. If it is not present, all configuration parameters are inherited from
the parent projects or the global configuration.

The `code-owners.config` file has the format of a Git config file (same as the
`project.config` file).

To update the `code-owners.config` file do (requires to be a project owner):

* clone the repository
* fetch and checkout the `refs/meta/config` branch (e.g. `git fetch origin
  refs/meta/config && git checkout FETCH_HEAD`)
* create or edit the `code-owners.config` file
* commit the changes
* push the newly created commit back to the `refs/meta/config` branch (e.g. `git
  push origin HEAD:refs/meta/config`)

Some of the configuration parameters can also be set via the [Update Code Owner
Project Config REST endpoint](rest-api.html#update-code-owner-project-config).

## <a id="checkIfEnabled">How to check if the code owners functionality is enabled for a project or branch

To check if the code owners functionality is enabled for a single branch, use
the [Get Code Owner Branch Config](rest-api.html#get-code-owner-branch-config)
REST endpoint and inspect the
[disabled](rest-api.html#code-owner-branch-config-info) field in the response
(if it is not present, the code owners functionality is enabled).

To check if the code owners functionality is enabled for a project or for
multiple branches, use the [Get Code Owner Project
Config](rest-api.html#get-code-owner-project-config) REST endpoint and inspect
the [status](rest-api.html#code-owners-status-info) in the response (an empty
status means that the code owners functionality is enabled for all branches of
the project).

You can invoke the REST endpoints via `curl` from the command-line or
alternatively open the following URLs in a browser:\
`https://<host>/projects/<project-name>/branches/<branch-name>/code_owners.branch_config`\
`https://<host>/projects/<project-name>/code_owners.project_config`\
(remember to URL-encode the project-name and branch-name)

## <a id="avoidIssuesWithCodeOwnerConfigs">How to avoid issues with code owner config files

To avoid issues with code owner config files it's highly recommended to keep the
[validation](validation.html) of code owner config files that is performed on
receive commits and submit enabled, as it prevents that issues are newly
introduced to code owner config files. Whether this validation is enabled and
whether code owner config files with new issues are rejected is controlled by
the following configuration parameters:

* [plugin.@PLUGIN@.enableValidationOnCommitReceived](config.html#pluginCodeOwnersEnableValidationOnCommitReceived)
* [plugin.@PLUGIN@.enableValidationOnSubmit](config.html#pluginCodeOwnersEnableValidationOnSubmit)
* [plugin.@PLUGIN@.rejectNonResolvableCodeOwners](config.html#pluginCodeOwnersRejectNonResolvableCodeOwners)
* [plugin.@PLUGIN@.rejectNonResolvableImports](config.html#pluginCodeOwnersRejectNonResolvableImports)

Since code owner config files can also get
[issues](validation.html#howCodeOwnerConfigsCanGetIssuesAfterSubmit) after they
have been submitted, host administrators and project owners are also recommended
to regularly check the existing code owner config files for issues by calling
the [Check Code Owner Config File REST
endpoint](rest-api.html#check-code-owner-config-files) (e.g. from a cronjob) and
then fix the reported issues.

## <a id="investigateIssuesWithCodeOwnerConfigs">How to investigate issues with code owner config files

If code owners config files are not working as expected, this is either caused
by:

* issues in the code owner config files
* a bug in the @PLUGIN@ plugin

Since code owner config files are part of the source code, any issues with them
should be investigated and fixed by the project owners and host administrators.
To do this they can:

* Check the code owner config files for issues by calling the [Check Code Owner
  Config File REST endpoint](rest-api.html#check-code-owner-config-files)
* Check the code ownership of a user for a certain path by calling the [Check
  Code Owner REST endpoint](rest-api.html#check-code-owner) (requires the caller
  to be host administrator or have the [Check Code Owner
  capability](#checkCodeOwner)).

Bugs with the @PLUGIN@ plugin should be filed as issues for the Gerrit team, but
only after issues with the code owner config files have been excluded.

Also see [above](#avoidIssuesWithCodeOwnerConfigs) how to avoid issues with code
owner config files in the first place.

## <a id="setupOverrides">How to setup code owner overrides

To setup code owner overrides do:

### 1. Define a label that should count as code owner override:

Create a [review label](../../../Documentation/config-labels.html)
via the [Create Label REST
endpoint](../../../Documentation/rest-api-projects.html#create-label):

```
  curl -X PUT -d '{"commit_message": "Create Owners-Override Label", "values": {" 0": "No Override", "+1": "Override"}}' --header "Content-Type: application/json" https://<gerrit-host>/a/projects/<project-name>/labels/Owners-Override
```

### 2. Configure this label as override approval:

Configure the override label via the [Update Code Owner Project Config REST
endpoint](rest-api.html#update-code-owner-project-config):

```
  curl -X PUT -d '{"override_approvals": ["Owners-Override+1"]}' --header "Content-Type: application/json" https://<gerrit-host>/a/projects/<project-name>/code_owners.project_config
```
\
Also see the description of the
[override_approval](config.html#codeOwnersOverrideApproval) configuration
parameter.

### 3. Assign permissions to vote on the override approval:

Go to the access screen of your project in the Gerrit web UI and assign
permissions to vote on the override label.

Alternatively the permissions can also be assigned via the [Set Access REST
endpoint](../../../Documentation/rest-api-projects.html#set-access).

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
