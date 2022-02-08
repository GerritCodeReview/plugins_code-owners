# Config FAQ's

* [How to update the code-owners.config file for a project](#updateCodeOwnersConfig)
* [How to check if the code owners functionality is enabled for a project or branch](#checkIfEnabled)
* [How to avoid issues with code owner config files](#avoidIssuesWithCodeOwnerConfigs)
* [How to investigate issues with code owner config files](#investigateIssuesWithCodeOwnerConfigs)
* [How to investigate issues with the code owner suggestion](#investigateIssuesWithCodeOwnerSuggestion)
* [How to define default code owners](#defineDefaultCodeOwners)
* [How to setup code owner overrides](#setupOverrides)
* [What's the best place to keep the global plugin
  configuration](#globalPluginConfiguration)
* [How to make unicode characters in file paths work?](#unicodeCharsInFilePaths)

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
Project Config REST endpoint](rest-api.md#update-code-owner-project-config).

## <a id="checkIfEnabled">How to check if the code owners functionality is enabled for a project or branch

To check if the code owners functionality is enabled for a single branch, use
the [Get Code Owner Branch Config](rest-api.md#get-code-owner-branch-config)
REST endpoint and inspect the
[disabled](rest-api.md#code-owner-branch-config-info) field in the response
(if it is not present, the code owners functionality is enabled).

To check if the code owners functionality is enabled for a project or for
multiple branches, use the [Get Code Owner Project
Config](rest-api.md#get-code-owner-project-config) REST endpoint and inspect
the [status](rest-api.md#code-owners-status-info) in the response (an empty
status means that the code owners functionality is enabled for all branches of
the project).

You can invoke the REST endpoints via `curl` from the command-line or
alternatively open the following URLs in a browser:\
`https://<host>/projects/<project-name>/branches/<branch-name>/code_owners.branch_config`\
`https://<host>/projects/<project-name>/code_owners.project_config`\
(remember to URL-encode the project-name and branch-name)

## <a id="avoidIssuesWithCodeOwnerConfigs">How to avoid issues with code owner config files

To avoid issues with code owner config files it's highly recommended to keep the
[validation](validation.md) of code owner config files that is performed on
receive commits and submit enabled, as it prevents that issues are newly
introduced to code owner config files. Whether this validation is enabled and
whether code owner config files with new issues are rejected is controlled by
the following configuration parameters:

* [plugin.@PLUGIN@.enableValidationOnCommitReceived](config.md#pluginCodeOwnersEnableValidationOnCommitReceived)
* [plugin.@PLUGIN@.enableValidationOnSubmit](config.md#pluginCodeOwnersEnableValidationOnSubmit)
* [plugin.@PLUGIN@.rejectNonResolvableCodeOwners](config.md#pluginCodeOwnersRejectNonResolvableCodeOwners)
* [plugin.@PLUGIN@.rejectNonResolvableImports](config.md#pluginCodeOwnersRejectNonResolvableImports)

Since code owner config files can also get
[issues](validation.md#howCodeOwnerConfigsCanGetIssuesAfterSubmit) after they
have been submitted, host administrators and project owners are also recommended
to regularly check the existing code owner config files for issues by calling
the [Check Code Owner Config File REST
endpoint](rest-api.md#check-code-owner-config-files) (e.g. from a cronjob) and
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
  Config File REST endpoint](rest-api.md#check-code-owner-config-files)
* Check the code ownership of a user for a certain path by calling the [Check
  Code Owner REST endpoint](rest-api.md#check-code-owner) (requires the caller
  to be host administrator or have the [Check Code Owner
  capability](rest-api.md#checkCodeOwner)).

Bugs with the @PLUGIN@ plugin should be filed as issues for the Gerrit team, but
only after issues with the code owner config files have been excluded.

Also see [above](#avoidIssuesWithCodeOwnerConfigs) how to avoid issues with code
owner config files in the first place.

## <a id="investigateIssuesWithCodeOwnerSuggestion">How to investigate issues with the code owner suggestion

If the code owners config suggestion is not working as expected, this is either
caused by:

* issues in the code owner config files
* user permissions
* account visibility
* account states
* a bug in the @PLUGIN@ plugin

Issues with code owner config files, user permissions, account visibility and
account states should be investigated and fixed by the project owners and host
administrators.

To do this they can:

* Use the `--debug` option of the [List Code
  Owners](rest-api.md#list-code-owners-for-path-in-branch) REST endpoints to
  get debug logs included into the response.
* Check the code owner config files for issues by calling the [Check Code Owner
  Config File REST endpoint](rest-api.md#check-code-owner-config-files)
* Check the code ownership of a user for a certain path by calling the [Check
  Code Owner REST endpoint](rest-api.md#check-code-owner) (requires the caller
  to be host administrator or have the [Check Code Owner
  capability](rest-api.md#checkCodeOwner))

Bugs with the @PLUGIN@ plugin should be filed as issues for the Gerrit team, but
only after other causes have been excluded.

Also see [above](#avoidIssuesWithCodeOwnerConfigs) how to avoid issues with code
owner config files in the first place.

## <a id="defineDefaultCodeOwners">How to define default code owners

[Default code owners](backend-find-owners.md#defaultCodeOwnerConfiguration)
that apply to all branches can be defined in an `OWNERS` file in the root
directory of the `refs/meta/config` branch.

To add an `OWNERS` file in the `refs/meta/config` branch do (requires to be a
project owner):

* clone the repository
* fetch and checkout the `refs/meta/config` branch (e.g. `git fetch origin
  refs/meta/config && git checkout FETCH_HEAD`)
* create or edit the `OWNERS` file
* commit the changes
* push the newly created commit back to the `refs/meta/config` branch (e.g. `git
  push origin HEAD:refs/meta/config`)

## <a id="setupOverrides">How to setup code owner overrides

To setup code owner overrides do:

### 1. Define a label that should count as code owner override:

Create a [review label](../../../Documentation/config-labels.md)
via the [Create Label REST
endpoint](../../../Documentation/rest-api-projects.md#create-label):

```
  curl -X PUT -d '{"commit_message": "Create Owners-Override Label", "values": {" 0": "No Override", "+1": "Override"}}' --header "Content-Type: application/json" https://<gerrit-host>/a/projects/<project-name>/labels/Owners-Override
```

### 2. Configure this label as override approval:

Configure the override label via the [Update Code Owner Project Config REST
endpoint](rest-api.md#update-code-owner-project-config):

```
  curl -X PUT -d '{"override_approvals": ["Owners-Override+1"]}' --header "Content-Type: application/json" https://<gerrit-host>/a/projects/<project-name>/code_owners.project_config
```
\
Also see the description of the
[override_approval](config.md#codeOwnersOverrideApproval) configuration
parameter.

### 3. Assign permissions to vote on the override approval:

Go to the access screen of your project in the Gerrit web UI and assign
permissions to vote on the override label.

Alternatively the permissions can also be assigned via the [Set Access REST
endpoint](../../../Documentation/rest-api-projects.md#set-access).

## <a id="globalPluginConfiguration">What's the best place to keep the global plugin configuration

The global plugin configuration can either be maintained in the
[gerrit.config](config.md) file or in the
[code-owners.config](config.md#projectLevelConfigFile) file in the
`refs/meta/config` branch of the `All-Projects` project. From the perspective of
the code-owners plugin both places are equally good. However which place is
preferred can depend on the system setup, e.g. changes to `gerrit.config` may be
harder to do and require a multi-day rollout, whereas changes of the
`All-Projects` configuration can be done through the [REST
API](rest-api.md#update-code-owner-project-config) and are always instant
(this can also be a disadvantage as it means that also bad config changes are
effective immediately).

**NOTE:** Any configuration that is done in `All-Projects` overrides the
corresponding configuration that is inherited from `gerrit.config`.

**NOTE:** There are a few configuration parameters (e.g. for [allowed email
domains](config.md#pluginCodeOwnersAllowedEmailDomain)) that cannot be set on
project level and hence must be set in `gerrit.config`.

## <a id="unicodeCharsInFilePaths">How to make unicode characters in file paths work?

The @PLUGIN@ plugin uses the Java NIO API which reads the default character
encoding from the system language settings. On Unix this means the `LANG` and
`LC_CTYPE` environment variables (setting one of them is sufficent). To enable
unicode characters in file paths e.g. set: `LANG=en_US.UTF-8`

If paths are used that are not valid according to the system language setting
(e.g. if a path contains unicode characters but `LANG` is `en_US.iso88591`)
the Java NIO API throws a `java.nio.file.InvalidPathException` with the message
`Malformed input or input contains unmappable characters`. If such an exception
occurs code-owner requests return `409 Conflict`, telling the user about the
invalid path.

---

Back to [@PLUGIN@ documentation index](index.md)

Part of [Gerrit Code Review](../../../Documentation/index.md)
