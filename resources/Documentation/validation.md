# Validation

The code owners functionality relies on the validity of the following:

* [code owner config files](user-guide.md#codeOwnerConfigFiles) that define
  the code owners
* [code-owner.config](config.md#projectLevelConfigFile) files in the
  'refs/meta/config' branches of the projects that contain the project-level
  configuration of the `@PLUGIN@` plugin

To reduce the risk that these files become invalid, they are validated when
they are modified and invalid modifications are rejected. In addition code owner
config files in a repository can be validated on demand by the [Check code
owners files REST endpoint](rest-api.md#check-code-owner-config-files).

**NOTE:** Most configuration issues are gracefully handled and do not break the
code owners functionality (e.g. non-resolveable code owners or non-resolveable
imports are silently ignored), however some configuration issue (non-parseable
configuration files, configuration of a non-existing [backend](backends.md)
etc.) are severe errors and block the submission of all changes for which the
affected configuration files are relevant.

**NOTE:** It's possible to disable the validation of code owner config files on
push and setup an [external
validation](config-guide.md#externalValidationOfCodeOwnerConfigs) by a CI bot
instead. In this case findings would be posted on the change.

All validations are best effort to prevent invalid configurations from
entering the repository, but not all possible issues can be prevented. Doing the
validation is useful since it prevents most issues and also gives quick feedback
to uploaders about typos (e.g. if an email is misspelled it's not breaking
anything, but the intended change of the uploader is not working).

No validation is done when:

* the `@PLUGIN@` plugin is not installed/enabled (this means when the `@PLUGIN@`
  plugin gets installed/enabled, it is possible that invalid configuration files
  already exist in the repository)
* updates happen behind Gerrit's back (e.g. pushes that bypass Gerrit)
* the validation is disabled via the
  [enableValidationOnCommitReceived](config.md#codeOwnersEnableValidationOnCommitReceived)
  or [enableValidationOnSubmit](config.md#codeOwnersEnableValidationOnSubmit)
  config options
* the [--code-owners~skip-validation](#skipCodeOwnerConfigValidationOnDemand)
  push option was specified on push

In addition for [code owner config files](user-guide.md#codeOwnerConfigFiles)
no validation is done when:

* the code owners functionality is disabled for the repository or branch (this
  means when the code owners functionality gets enabled, it is possible that
  invalid code owner configs already exist in the repository)
* the `@PLUGIN@` plugin configuration is invalid (in this case we don't know
  which files are code owner config files, so we allow all uploads rather than
  blocking all uploads, to reduce the risk of breaking the plugin configuration
  `code-owner.config` files are validated too)

## <a id="howCodeOwnerConfigsCanGetIssuesAfterSubmit">
In addition it is possible that [code owner config
files](user-guide.md#codeOwnerConfigFiles) get issues after they have been
submitted:

* configuration parameters that are relevant for the validation are changed
  (e.g. the [accounts.visibility](../../../Documentation/config-gerrit.md#accounts.visibility)
  setting is changed, [another code owners backend is
  configured](setup-guide.md#configureCodeOwnersBackend) which now uses a
  different syntax or different names for code owner config files, the [file
  extension for code owner config file is set/changed](config.md#codeOwnersFileExtension),
  [arbitrary file extensions for code owner config files](config.md#codeOwnersEnableCodeOwnerConfigFilesWithFileExtensions)
  get enabled/disabled or the [allowed email domains are
  changed](config.md#pluginCodeOwnersAllowedEmailDomain))
* emails of users may change so that emails in code owner configs can no longer
  be resolved
* imported code owner config files may get deleted or renamed so that import
  references can no longer be resolved

When updating [code owner config files](user-guide.md#codeOwnerConfigFiles)
the validation only rejects the update if it introduces **new** issues. This
means the update is allowed if:

* there are issues that are still present after the update, but the update
  doesn't introduce any new issues
* the file was non-parseable and the update makes it parseable, but issues are
  present (since a parseable file with issues is better than a non-parseable
  file)
* the file was non-parseable and with the update it is still non-parseable

For [code owner config files](user-guide.md#codeOwnerConfigFiles) the
validation may also be performed on submit (in addition to the validation that
is performed on upload of the change, see
[enableValidationOnSubmit](config.md#codeOwnersEnableValidationOnSubmit)
config setting). Repeating the validation on submit can make sense because
relevant configuration can change between the time a change is uploaded and the
time a change is submitted. If enabled, on submit we repeat the exact same
validation that was done on upload. This means, all visibility checks will be
done from the perspective of the uploader.

## <a id="skipCodeOwnerConfigValidationOnDemand">Skip code owner config validation on demand

By setting the `code-owners~skip-validation` push option it is possible to skip
the code owner config validation on push:
`git push -o code-owners~skip-validation origin HEAD:refs/for/master`

For the [Create Change](../../../Documentation/rest-api-changes.md#create-change)
REST endpoint skipping the code owner config validation is possible by setting
`code-owners~skip-validation` with the value `true` as a validation option in
the [ChangeInput](../../../Documentation/rest-api-changes.md#change-input)
(see field `validation_options`).

Using the push option or the validation option requires the calling user to
have the `Can Skip Code Owner Config Validation` global capability. Host
administrators have this capability implicitly assigned via the `Administrate
Server` global capability.

**NOTE:** Using this option only makes sense if the [code owner config validation
on submit](config.md#pluginCodeOwnersEnableValidationOnSubmit) is disabled, as
otherwise it's not possible to submit the created change (using the push option
only skips the validation for the push, but not for the submission of the
change).

### <a id="codeOwnerConfigFileChecks">Validation checks for code owner config files

For [code owner config files](user-guide.md#codeOwnerConfigFiles) the
following checks are performed:

* the code owner config files are parseable
* the code owner emails are resolveable (unless this check is
  [disabled](config.md#codeOwnersRejectNonResolvableCodeOwners)):\
  a code owners email is not resolveable if:
    * the account that owns it is inactive
    * the account that owns it is not visible to the uploader (according to
      [accounts.visibility](../../../Documentation/config-gerrit.md#accounts.visibility)
      setting)
    * it is a non-visible secondary email
    * there is no account that has this email assigned
    * it is ambiguous (the same email is assigned to multiple active accounts)
    * it has an email domain that is disallowed (see
      [allowedEmailDomain](config.md#pluginCodeOwnersAllowedEmailDomain))
      configuration
* the imports are resolveable (unless this check is
  [disabled](config.md#codeOwnersRejectNonResolvableImports)):\
  an import is not resolveable if:
    * the imported file is not a code owner config file
    * the imported file is not parseable
    * the imported file doesn't exists
    * the branch from which the file should be imported doesn't exist or is not
      visible to the uploader
    * the project from which the file should be imported doesn't exist or is not
      visible to the uploader
    * the project from which the file should be imported doesn't permit reads
      (e.g. has the state `HIDDEN`)

**NOTE:** Whether commits that newly add non-resolvable code owners and
non-resolvable imports are rejected on commit received and on submit is
controlled by the
[rejectNonResolvableCodeOwners](config.md#pluginCodeOwnersRejectNonResolvableCodeOwners)
and [rejectNonResolvableImports](config.md#pluginCodeOwnersRejectNonResolvableImports)
config settings.

The following things are **not** checked (not an exhaustive list):

* Cycles in imports of owner config files:\
  Detecting cycles in imports can be expensive and they are not seen as a
  problem. When imports are resolved we keep track of the imported code owner
  config files and stop if we see a code owner config file that we’ve imported
  already. This behaviour is consistent with how Gerrit handles cycles in group
  includes.
* Impossible code owner configurations:\
  It is possible to create a code owner configuration where some folders/files
  have no code owners. In this case nobody can give a code owner approval for
  these folders/files, and submitting changes to them requires a
  [code owner override](user-guide.md#codeOwnerOverride).


### <a id="codeOwnersConfigFileChecks">Validation checks for code-owners.config files

For the [code-owner.config](config.md#projectLevelConfigFile) in the
`refs/meta/config` branch the following checks are performed:

* `code-owner.config` file is parseable
* the [codeOwners.backend](config.md#codeOwnersBackend) and
  [codeOwners.\<branch\>.backend](config.md#codeOwnersBranchBackend)
  configurations are valid (that they reference an existing [code owner
  backend](backends.md))
* the [codeOwners.disabled](config.md#codeOwnersDisabled) and
  [codeOwners.disabledBranch](config.md#codeOwnersDisabledBranch)
  configurations are valid (that they have parseable value)
* the [codeOwners.requiredApproval](config.md#codeOwnersRequiredApproval)
  and [codeOwners.overrideApproval](config.md#codeOwnersOverrideApproval)
  configurations are valid (that they reference an existing label that has a
  range that covers the specified voting value, it's currently not possible to
  add the definition of this label in the same commit but it must be present
  before)
* the [codeOwners.mergeCommitStrategy](config.md#codeOwnersMergeCommitStrategy)
  configuration is valid
* the [codeOwners.fallbackCodeOwners](config.md#codeOwnersFallbackCodeOwners)
  configuration is valid
* the [codeOwners.maxPathsInChangeMessages](config.md#codeOwnersMaxPathsInChangeMessages)
  configuration is valid

---

Back to [@PLUGIN@ documentation index](index.md)

Part of [Gerrit Code Review](../../../Documentation/index.md)
