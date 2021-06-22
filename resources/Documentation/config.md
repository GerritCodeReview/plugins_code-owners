# Configuration

The @PLUGIN@ plugin can be configured on host and on project-level:

* host-level:\
  in the `gerrit.config` file in the `plugin.@PLUGIN@` subsection
* project-level:\
  in `@PLUGIN@.config` files that are stored in the `refs/meta/config` branches
  of the projects

This page describes all available configuration parameters. For configuration
recommendations please consult the [config guide](config-guide.html).

**NOTE**: This is the configuration that controls the behavior of the @PLUGIN@
plugin. Code owners (except global code owners) are not defined here, but in
[code owner config files](user-guide.html#codeOwnerConfigFiles) (e.g. `OWNERS`
files) that are stored in the source tree of the repository.

## <a id="inheritance">Inheritance</a>

Projects inherit the configuration of their parent projects, following the chain
of parent projects until the `All-Projects` root project is reached which
inherits the configuration from `gerrit.config`.

Setting a single-value configuration parameter (single string, boolean, enum,
int, long) for a project overrides any inherited value for this configuration
parameter.

Example for single-value configuration parameters:

parent `code-owners.config`:
```
  [codeOwners]
    readOnly = true
    overrideInfoUrl = https://owners-overrides.example.com
    exemptPureReverts = true
```
\
project `code-owners.config`:
```
  [codeOwners]
    readOnly = false
    overrideInfoUrl = https://foo.example.com/owners-overrides
    fileExtension = fork
```
\
effective configuration:
```
  [code-owners]
    readOnly = false
    overrideInfoUrl = https://foo.example.com/owners-overrides
    fileExtension = fork
    exemptPureReverts = true
```
\
In contrast to this, if a value for a multi-value / list configuration parameter
is set, the value is added to the inherited value list (the inherited value list
is extended, not overridden). Overriding/unsetting an inherited value list is
not possible.

Example for multi-value / list configuration parameters:

parent `code-owners.config`:
```
  [codeOwners]
    globalCodeOwner = bot-foo@example.com
    globalCodeOwner = bot-bar@example.com
    exemptedUser = bot-abc@example.com
    exemptedUser = bot-xyz@example.com
    disabledBranch = refs/meta/config
```
\
project `code-owners.config`:
```
  [codeOwners]
    globalCodeOwner = bot-baz@example.com
    disabledBranch =
```
\
effective configuration:
```
  [code-owners]
    globalCodeOwner = bot-foo@example.com
    globalCodeOwner = bot-bar@example.com
    globalCodeOwner = bot-baz@example.com
    exemptedUser = bot-abc@example.com
    exemptedUser = bot-xyz@example.com
    disabledBranch = refs/meta/config
```

## <a id="staleIndexOnConfigChanges">
**NOTE:** Some configuration changes can lead to changes becoming stale in the
change index. E.g. if an additional branch is newly exempted in `gerrit.config`
or in the `code-owners.config` of a parent project, the submittability of
changes for that branch in child projects may change (since they no longer
require code owner approvals), but it's not feasible to reindex all affected
changes when this config change is done (as config changes can potentially
affect all open changes on the host and reindexing that many changes would be
too expensive). In this case the affected changes will be become stale in the
change index (e.g. the change index contains outdated submit records) and as a
result of this you may not observe the effects of the config change on all
changes immediately, but only when they have been reindexed (which happens on
any modification of the changes). If needed, you may force the reindexing of a
change by calling the [Index
Changes](../../../Documentation/rest-api-changes.html#index-change) REST
endpoint or by touching the change (e.g. by adding a comment).

# <a id="globalConfiguration">Global configuration in gerrit.config</a>

<a id="pluginCodeOwnersDisabled">plugin.@PLUGIN@.disabled</a>
:       Whether the code owners functionality is disabled for the project.\
        If `true` submitting changes doesn't require code owner approvals.\
        This allows projects to opt-out of the code owners functionality.\
        Can be overridden per project by setting
        [codeOwners.disabled](#codeOwnersDisabled) in
        `@PLUGIN@.config`.\
        By default `false`.

<a id="pluginCodeOwnersDisabledBranch">plugin.@PLUGIN@.disabledBranch</a>
:       An exact ref, a ref pattern or a regular expression to disable the code
        owners functionality for the matched branches.\
        By using a negative lookahead, it's possible to match all but one
        branches. E.g. to disable the code owners functionality for all branches
        except the `refs/heads/master` branch the following regular expression
        can be used: `^refs/(?!heads/master$).*`
        For matched branches submitting changes doesn't require code owner
        approvals.\
        This allows branches to opt-out of the code owners functionality.\
        Can be set multiple times.\
        The configured value list can be extended on project-level by setting
        [codeOwners.disabledBranch](#codeOwnersDisabledBranch) in
        `@PLUGIN@.config`.\
        By default unset.

<a id="pluginCodeOwnersBackend">plugin.@PLUGIN@.backend</a>
:       The code owners backend that should be used.\
        Can be overridden per project by setting
        [codeOwners.backend](#codeOwnersBackend) in
        `@PLUGIN@.config`.\
        The supported code owner backends are listed at the
        [Backends](backends.html) page.\
        By default `find-owners`.\
        \
        **NOTE:** Be careful with changing this parameter as it invalidates all
        existing [code owner config files](user-guide.html#codeOwnerConfigFiles).
        E.g. by changing the backend code owner configurations may now be read
        from different files, or even worse from the same files but with another
        syntax so that the existing code owner config files can no longer be
        parsed.

<a id="pluginCodeOwnersFileExtension">plugin.@PLUGIN@.fileExtension</a>
:       The file extension that should be used for code owner config files.\
        Allows to use a different code owner configuration in a fork. E.g. if
        the original repository uses `OWNERS` code owner config files (no file
        extension configured) one could set `fork` as file extension in the fork
        so that the fork uses `OWNERS.fork` files and the existing `OWNERS`
        files are ignored.\
        Can be overridden per project by setting
        [codeOwners.fileExtension](#codeOwnersFileExtension) in
        `@PLUGIN@.config`.\
        By default unset (no file extension is used).\
        If a file extension is configured,
        [plugin.@PLUGIN@.enableCodeOwnerConfigFilesWithFileExtensions](#pluginCodeOwnersEnableCodeOwnerConfigFilesWithFileExtensions)
        should be set to `false`, as otherwise code owner config files with any
        file extension will be validated, which causes validation errors if code
        owner config files with other file extensions use a different owners
        syntax or reference users that do not exist on this Gerrit host.

<a id="pluginCodeOwnersEnableCodeOwnerConfigFilesWithFileExtensions">plugin.@PLUGIN@.enableCodeOwnerConfigFilesWithFileExtensions</a>
:       Whether file extensions for code owner config files are enabled.\
        If enabled, code owner config files with file extensions are treated as
        regular code owner config files. This means they are validated on
        push/submit (if validation is enabled) and can be imported by other code
        owner config files (regardless of whether they have the same file
        extension or not).\
        Enabling this option should not be used in combination with the
        [plugin.@PLUGIN@.fileExtension](#pluginCodeOwnersFileExtension) option
        as that option uses file extensions to differentiate different sets of
        code owner config files in the same repository/branch which may use
        different code owner syntaxes or reference users that do not exist on
        this Gerrit host. In this case, code owner config files with (other)
        file extensions should not be validated as they likely will fail the
        validation.\
        Can be overridden per project by setting
        [codeOwners.enableCodeOwnerConfigFilesWithFileExtensions](#codeOwnersEnableCodeOwnerConfigFilesWithFileExtensions)
        in `@PLUGIN@.config`.\
        By default `false`.

<a id="pluginCodeOwnersOverrideInfoUrl">plugin.@PLUGIN@.overrideInfoUrl</a>
:       A URL for a page that provides host-specific information about how to
        request a code owner override.\
        The frontend displays a link to this page on the change screen so that
        users can discover the override instructions easily.\
        Can be overridden per project by setting
        [codeOwners.overrideInfoUrl](#codeOwnersOverrideInfoUrl) in
        `@PLUGIN@.config`.\
        By default unset (no override info URL).

<a id="pluginCodeOwnersInvalidCodeOwnerConfigInfoUrl">plugin.@PLUGIN@.invalidCodeOwnerConfigInfoUrl</a>
:       A URL for a page that provides host-specific information about how to
        deal with invalid code owner config files.\
        This URL is included into error messages that indicate invalid code
        owner config files.\
        Can be overridden per project by setting
        [codeOwners.invalidCodeOwnerConfigInfoUrl](#codeOwnersInvalidCodeOwnerConfigInfoUrl)
        in `@PLUGIN@.config`.\
        By default unset (no invalid code owner config info URL).

<a id="pluginCodeOwnersEnableImplicitApprovals">plugin.@PLUGIN@.enableImplicitApprovals</a>
:       Whether an implicit code owner approval from the change owner is
        assumed.\
        Implicit code owner approvals are only applied on patch sets that are
        uploaded by the change owner (change owner == last patch set uploader).\
        \
        Can be `FALSE`, `TRUE` or `FORCED`.\
        \
        `FALSE`:\
        Implicit code-owner approvals of change owners are disabled.\
        \
        `TRUE`:\
        Implicit code-owner approvals of change owners are enabled, but only if
        the configured [required label](#pluginCodeOwnersRequiredApproval) is
        not configured to [ignore self
        approvals](../../../Documentation/config-labels.html#label_ignoreSelfApproval)
        from the uploader.\
        \
        `FORCED`:\
        Implicit code-owner approvals of change owners are enabled, even if the
        configured [required label](#pluginCodeOwnersRequiredApproval) disallows
        self approvals.\
        \
        If enabled/enforced, code owners need to be aware of their implicit
        approval when they upload new changes for other users (e.g. if a
        contributor pushes a change to a wrong branch and a code owner helps
        them to get it rebased onto the correct branch, the rebased change has
        implicit approvals from the code owner, since the code owner is the
        change owner).\
        If implicit code owner approvals are disabled, code owners can still
        self-approve their own changes by voting on the change.\
        Can be overridden per project by setting
        [codeOwners.enableImplicitApprovals](#codeOwnersEnableImplicitApprovals)
        in `@PLUGIN@.config`.\
        By default `FALSE`.

<a id="pluginCodeOwnersGlobalCodeOwner">plugin.@PLUGIN@.globalCodeOwner</a>
:       The email of a user that should be a code owner globally across all
        branches.\
        If global code owners should be omitted in the code owner suggestion
        (e.g. because they are bots and cannot react to review requests), they
        can be added to the `Service Users` group (since members of this group
        are not suggested as code owners).\
        Can be specified multiple times to set multiple global code owners.\
        The configured value list can be extended on project-level by setting
        [codeOwners.globalCodeOwner](#codeOwnersGlobalCodeOwner) in
        `@PLUGIN@.config`.\
        By default unset (no global code owners).

<a id="pluginCodeOwnersExemptedUser">plugin.@PLUGIN@.exemptedUser</a>
:       The email of a user that should be exempted from requiring code owner
        approvals.\
        If a user is exempted from requiring code owner approvals patch sets
        that are uploaded by this user are automatically code-owner approved.\
        Can be specified multiple times to exempt multiple users.\
        The configured value list can be extended on project-level by setting
        [codeOwners.exemptedUser](#codeOwnersExemptedUser) in
        `@PLUGIN@.config`.\
        By default unset (no exempted users).

<a id="pluginCodeOwnersReadOnly">plugin.@PLUGIN@.readOnly</a>
:       Whether code owner config files are read-only.\
        Can be overridden per project by setting
        [codeOwners.readOnly](#codeOwnersReadOnly) in
        `@PLUGIN@.config`.\
        By default `false`.

<a id="pluginCodeOwnersExemptPureReverts">plugin.@PLUGIN@.exemptPureReverts</a>
:       Whether pure revert changes are exempted from needing code owner
        approvals for submit.\
        Only works for pure reverts which have been created through the Gerrit
        [REST API](../../../Documentation/rest-api-change.html#revert-change)
        (but not for pure reverts which were done locally and then pushed to
        Gerrit).\
        Can be overridden per project by setting
        [codeOwners.exemptPureReverts](#codeOwnersExemptPureReverts) in
        `@PLUGIN@.config`.\
        By default `false`.

<a id="pluginCodeOwnersEnableValidationOnCommitReceived">plugin.@PLUGIN@.enableValidationOnCommitReceived</a>
:       Policy for validating code owner config files when a commit is received.
        \
        Can be `TRUE`, `FALSE`, `DRY_RUN`, `FORCED` or `FORCED_DRY_RUN`.\
        \
        `TRUE`:\
        The code owner config file validation is enabled and the upload of
        invalid code owner config files is rejected.\
        If the code owners functionality is disabled, no validation is
        performed.\
        \
        `FALSE`:\
        The code owner config file validation is disabled, invalid code owner
        config files are not rejected.\
        \
        `DRY_RUN`:\
        Code owner config files are validated, but invalid code owner config
        files are not rejected.\
        If the code owners functionality is disabled, no dry-run validation is
        performed.\
        \
        `FORCED`:\
        Code owner config files are validated even if the code owners
        functionality is disabled.\
        This option is useful when the code owner config validation should be
        enabled as preparation to enabling the code owners functionality.\
        \
        `FORCED_DRY_RUN`:\
        Code owner config files are validated even if the code owners
        functionality is disabled, but invalid code owner config files are not
        rejected.\
        This option is useful when the code owner config validation should be
        enabled as preparation to enabling the code owners functionality.\
        \
        Should only be disabled if there is bot that validates the code owner
        config files in open changes as part of a pre-submit validation.\
        Can be overridden per project by setting
        [codeOwners.enableValidationOnCommitReceived](#codeOwnersEnableValidationOnCommitReceived)
        in `@PLUGIN@.config`.\
        By default `true`.

<a id="pluginCodeOwnersEnableValidationOnSubmit">plugin.@PLUGIN@.enableValidationOnSubmit</a>
:       Policy for validating code owner config files when a change is
        submitted.
        \
        Can be `TRUE`, `FALSE`, `DRY_RUN`, `FORCED` or `FORCED_DRY_RUN`.\
        \
        `TRUE`:\
        The code owner config file validation is enabled and the submission of
        invalid code owner config files is rejected.\
        If the code owners functionality is disabled, no validation is
        performed.\
        \
        `FALSE`:\
        The code owner config file validation is disabled, invalid code owner
        config files are not rejected.\
        \
        `DRY_RUN`:\
        Code owner config files are validated, but invalid code owner config
        files are not rejected.\
        If the code owners functionality is disabled, no dry-run validation is
        performed.\
        \
        `FORCED`:\
        Code owner config files are validated even if the code owners
        functionality is disabled.\
        This option is useful when the code owner config validation should be
        enabled as preparation to enabling the code owners functionality.\
        \
        `FORCED_DRY_RUN`:\
        Code owner config files are validated even if the code owners
        functionality is disabled, but invalid code owner config files are not
        rejected.\
        This option is useful when the code owner config validation should be
        enabled as preparation to enabling the code owners functionality.\
        \
        Disabling the submit validation is not recommended.\
        Can be overridden per project by setting
        [codeOwners.enableValidationOnSubmit](#codeOwnersEnableValidationOnSubmit)
        in `@PLUGIN@.config`.\
        By default `false`.

<a id="pluginCodeOwnersRejectNonResolvableCodeOwners">plugin.@PLUGIN@.rejectNonResolvableCodeOwners</a>
:       Whether modifications of code owner config files that newly add
        non-resolvable code owners should be rejected on commit received and
        submit.\
        If `true` newly added non-resolveable code owners are reported as errors
        and the commit is rejected.\
        If `false` newly added non-resolvable code owners are only reported as
        warnings and the commit is not rejected.\
        This setting has no effect if the validation is disabled via
        [enableValidationOnCommitReceived](#pluginCodeOwnersEnableValidationOnCommitReceived)
        or [enableValidationOnSubmit](#pluginCodeOwnersEnableValidationOnSubmit).
        Can be overridden per project by setting
        [codeOwners.rejectNonResolvableCodeOwners](#codeOwnersRejectNonResolvableCodeOwners)
        in `@PLUGIN@.config`.\
        By default `true`.

<a id="pluginCodeOwnersRejectNonResolvableImports">plugin.@PLUGIN@.rejectNonResolvableImports</a>
:       Whether modifications of code owner config files that newly add
        non-resolvable imports should be rejected on commit received an submit.\
        If `true` newly added non-resolveable imports are reported as errors and
        the commit is rejected.\
        If `false` newly added non-resolvable imports are only reported as
        warnings and the commit is not rejected.\
        This setting has no effect if the validation is disabled via
        [enableValidationOnCommitReceived](#pluginCodeOwnersEnableValidationOnCommitReceived)
        or [enableValidationOnSubmit](#pluginCodeOwnersEnableValidationOnSubmit).
        Can be overridden per project by setting
        [codeOwners.rejectNonResolvableImports](#codeOwnersRejectNonResolvableImports)
        in `@PLUGIN@.config`.\
        By default `true`.

<a id="pluginCodeOwnersAllowedEmailDomain">plugin.@PLUGIN@.allowedEmailDomain</a>
:       Email domain that allows to assign code ownerships to emails with this
        domain.\
        Can be specified multiple times.\
        Code ownerships that are assigned to emails with non-allowed domains are
        ignored and rejected on push.\
        By default unset (all email domains are allowed).

<a id="pluginCodeOwnersRequiredApproval">plugin.@PLUGIN@.requiredApproval</a>
:       Approval that is required from code owners to approve the files in a
        change.\
        Any approval on the configured label that has a value >= the configured
        value is considered as code owner approval.\
        The required approval must be specified in the format
        "\<label-name\>+\<label-value\>".\
        The configured label must exist for all projects for which this setting
        applies (all projects that have code owners enabled and for which this
        setting is not overridden).\
        If the definition of the configured label has [copy
        rules](../../../Documentation/config-labels.html#label_copyAnyScore)
        enabled so that votes are sticky across patch sets, also the code owner
        approvals will be sticky.\
        If the definition of the configured label [ignores self
        approvals](../../../Documentation/config-labels.html#label_ignoreSelfApproval)
        from the uploader, any vote from the uploader is ignored for the code
        owners check.\
        Can be overridden per project by setting
        [codeOwners.requiredApproval](#codeOwnersRequiredApproval) in
        `@PLUGIN@.config`.\
        By default "Code-Review+1".

<a id="pluginCodeOwnersOverrideApproval">plugin.@PLUGIN@.overrideApproval</a>
:       Approval that counts as override for the code owners submit check.\
        Any approval on the configured label that has a value >= the configured
        value is considered as code owner override.\
        The override approval must be specified in the format
        "\<label-name\>+\<label-value\>".\
        Can be specifed multiple times to configure multiple override approvals.
        If multiple approvals are configured, any of them is sufficient to
        override the code owners submit check.\
        The configured labels must exist for all projects for which this setting
        applies (all projects that have code owners enabled and for which this
        setting is not overridden).\
        If the definition of the configured labels has [copy
        rules](../../../Documentation/config-labels.html#label_copyAnyScore)
        enabled so that votes are sticky across patch sets, also the code owner
        overrides will be sticky.\
        If the definition of a configured label [ignores self
        approvals](../../../Documentation/config-labels.html#label_ignoreSelfApproval)
        from the uploader, any override vote from the uploader on that label is
        ignored for the code owners check.\
        The configured value list can be extended on project-level by setting
        [codeOwners.overrideApproval](#codeOwnersOverrideApproval) in
        `@PLUGIN@.config`.\
        By default unset which means that the override functionality is
        disabled.

<a id="pluginCodeOwnersEnableExperimentalRestEndpoints">plugin.@PLUGIN@.enableExperimentalRestEndpoints</a>
:       Whether experimental REST endpoints are enabled.\
        By default `false`.

<a id="pluginCodeOwnersMergeCommitStrategy">plugin.@PLUGIN@.mergeCommitStrategy</a>
:       Strategy that defines for merge commits which files require code owner
        approvals.\
        \
        Can be `ALL_CHANGED_FILES` or `FILES_WITH_CONFLICT_RESOLUTION`.\
        \
        `ALL_CHANGED_FILES`:\
        All files which differ between the merge commmit that is being reviewed
        and its first parent commit (which is the HEAD of the destination
        branch) require code owner approvals.\
        Using this strategy is the safest option, but requires code owners to
        also approve files which have been merged automatically.\
        Using this strategy makes sense if the code owners differ between
        branches and the code owners in one branch don't trust what the code
        owners in other branches have approved, or if there are branches that do
        not require code owner approvals at all.\
        \
        `FILES_WITH_CONFLICT_RESOLUTION`:\
        Only files which differ between the merge commmit that is being reviewed
        and the auto merge commit (the result of automatically merging the 2
        parent commits, may contain Git conflict markers) require code owner
        approvals.\
        Using this strategy means that files that have been merged automatically
        and for which no manual conflict resolution has been done do not require
        code owner approval.\
        Using this strategy is only recommended, if all branches require code
        owner approvals and if the code owners in all branches are trusted. If
        this is not the case, it is recommended to use the `ALL_CHANGED_FILES`
        strategy instead.\
        *Example*: If this strategy is used and there is a branch that doesn't
        require code owner approvals (e.g. a user sandbox branch or an
        experimental branch) the code owners check can be bypassed by:\
        a) setting the branch that doesn't require code owner approvals to the
        same commit as the main branch that does require code owner approvals\
        b) making a change in the branch that doesn't require code owner
        approvals\
        c) merging this change back into the main branch that does require code
        owner approvals\
        d) since it's a clean merge, all files are merged automatically and no
        code owner approval is required\
        \
        Can be overridden per project by setting
        [codeOwners.mergeCommitStrategy](#codeOwnersMergeCommitStrategy) in
        `@PLUGIN@.config`.\
        By default `ALL_CHANGED_FILES`.

<a id="pluginCodeOwnersFallbackCodeOwners">plugin.@PLUGIN@.fallbackCodeOwners</a>
:       Policy that controls who should own paths that have no code owners
        defined. This policy only applies if the inheritance of parent code
        owners hasn't been explicity disabled in a relevant code owner config
        file and if there are no unresolved imports.\
        \
        Can be `NONE`, `PROJECT_OWNERS` or `ALL_USERS`.\
        \
        `NONE`:\
        Paths for which no code owners are defined are owned by no one. This
        means changes that touch these files can only be submitted with a code
        owner override.\
        \
        `PROJECT_OWNERS`:\
        Paths for which no code owners are defined are owned by the project
        owners. This means changes to these paths can be approved by the project
        owners.\
        \
        `ALL_USERS`:\
        Paths for which no code owners are defined are owned by all users. This
        means changes to these paths can be approved by anyone. If [implicit
        approvals](#pluginCodeOwnersEnableImplicitApprovals) are enabled, these
        files are always automatically approved. The `ALL_USERS` option should
        only be used with care as it means that any path that is not covered by
        the code owner config files is automatically opened up to everyone and
        mistakes with configuring code owners can easily happen. This is why
        this option is intended to be only used if requiring code owner
        approvals should not be enforced.\
        \
        Please note that fallback code owners are not suggested as code owners
        in the UI.
        \
        Can be overridden per project by setting
        [codeOwners.fallbackCodeOwners](#codeOwnersFallbackCodeOwners) in
        `@PLUGIN@.config`.\
        By default `NONE`.

<a id="pluginCodeOwnersMaxPathsInChangeMessages">plugin.@PLUGIN@.maxPathsInChangeMessages</a>
:       The @PLUGIN@ plugin lists owned paths in change messages when:
        \
        1. A code owner votes on the [code owners
        label](#pluginCodeOwnersRequiredApproval):\
        The paths that are affected by the vote are listed in the change message
        that is posted when the vote is applied.\
        \
        2. A code owner is added as reviewer:\
        The paths that are owned by the reviewer are posted as a change
        message.\
        \
        This configuration parameter controls the maximum number of paths that
        are included in change messages. This is to prevent that the change
        messages become too big for large changes that touch many files.\
        Setting the value to `0` disables including owned paths into change
        messages.\
        Can be overridden per project by setting
        [codeOwners.maxPathsInChangeMessages](#codeOwnersMaxPathsInChangeMessages)
        in `@PLUGIN@.config`.\
        By default `50`.

<a id="pluginCodeOwnersMaxCodeOwnerConfigCacheSize">plugin.@PLUGIN@.maxCodeOwnerConfigCacheSize</a>
:       When computing code owner file statuses for a change (e.g. to compute
        the results for the code owners submit rule) parsed code owner config
        files are cached in memory for the time of the request.\
        This configuration parameter allows to set a limit for the number of
        code owner config files that are cached per request.\
        By default `10000`.

<a id="pluginCodeOwnersMaxCodeOwnerCacheSize">plugin.@PLUGIN@.maxCodeOwnerCacheSize</a>
:       When computing code owner file statuses for a change (e.g. to compute
        the results for the code owners submit rule) emails that are mentioned
        in relevant code owner config files need to be resolved to Gerrit
        accounts. The resolved code owners are cached in memory for the time of
        the request so that this resolution has to be done only once per email.\
        This configuration parameter allows to set a limit for the number of
        resolved code owners that are cached per request.\
        By default `10000`.

# <a id="projectConfiguration">Project configuration in @PLUGIN@.config</a>

<a id="codeOwnersDisabled">codeOwners.disabled</a>
:       Whether the code owners functionality is disabled for the project.\
        If `true` submitting changes doesn't require code owner approvals.\
        This allows projects to opt-out of the code owners functionality.\
        Overrides the global setting
        [plugin.@PLUGIN@.disabled](#pluginCodeOwnersDisabled) in
        `gerrit.config` and the `codeOwners.disabled` setting from parent
        projects.\
        By default `false`.

<a id="codeOwnersDisabledBranch">codeOwners.disabledBranch</a>
:       An exact ref, a ref pattern or a regular expression to disable the code
        owners functionality for the matched branches.\
        By using a negative lookahead, it's possible to match all but one
        branches. E.g. to disable the code owners functionality for all branches
        except the `refs/heads/master` branch the following regular expression
        can be used: `^refs/(?!heads/master$).*`
        For matched branches submitting changes doesn't require code owner
        approvals.\
        This allows branches to opt-out of the code owners functionality.\
        Can be set multiple times.\
        Extends the global setting
        [plugin.@PLUGIN@.disabledBranch](#pluginCodeOwnersDisabledBranch) in
        `gerrit.config` and the `codeOwners.disabledBranch` setting from parent
        projects.\
        By default unset.

<a id="codeOwnersBackend">codeOwners.backend</a>
:       The code owners backend that should be used for the project.\
        Overrides the global setting
        [plugin.@PLUGIN@.backend](#pluginCodeOwnersBackend) in `gerrit.config`
        and the `codeOwners.backend` setting from parent projects.\
        Can be overridden per branch by setting
        [codeOwners.\<branch\>.backend](#codeOwnersBranchBackend).\
        The supported code owner backends are listed at the
        [Backends](backends.html) page.\
        If not set, the global setting
        [plugin.@PLUGIN@.backend](#pluginCodeOwnersBackend) in `gerrit.config`\
        is used.\
        \
        **NOTE:** Be careful with changing this parameter as it invalidates all
        existing [code owner config files](user-guide.html#codeOwnerConfigFiles).
        E.g. by changing the backend code owner configurations may now be read
        from different files, or even worse from the same files but with another
        syntax so that the existing code owner config files can no longer be
        parsed.

<a id="codeOwnersBranchBackend">codeOwners.\<branch\>.backend</a>
:       The code owners backend that should be used for this branch.\
        The branch can be the short or full name. If both configurations exist
        the one for the full name takes precedence.\
        Overrides the per repository setting
        [codeOwners.backend](#codeOwnersBackend) and the
        `codeOwners.<branch>.backend` setting from parent projects.\
        The supported code owner backends are listed at the
        [Backends](backends.html) page.\
        If not set, the project level configuration
        [codeOwners.backend](#codeOwnersBackend) is used.\
        \
        **NOTE:** Be careful with changing this parameter as it invalidates all
        existing [code owner config files](user-guide.html#codeOwnerConfigFiles).
        E.g. by changing the backend code owner configurations may now be read
        from different files, or even worse from the same files but with another
        syntax so that the existing code owner config files can no longer be
        parsed.

<a id="codeOwnersFileExtension">codeOwners.fileExtension</a>
:       The file extension that should be used for the code owner config files
        in this project.\
        Allows to use a different code owner configuration in a fork. E.g. if
        the original repository uses `OWNERS` code owner config files (no file
        extension configured) one could set `fork` as file extension in the fork
        so that the fork uses `OWNERS.fork` files and the existing `OWNERS`
        files are ignored.\
        Overrides the global setting
        [plugin.@PLUGIN@.fileExtension](#pluginCodeOwnersFileExtension) in
        `gerrit.config` and the `codeOwners.fileExtension` setting from parent
        projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.fileExtension](#pluginCodeOwnersFileExtension) in
        `gerrit.config` is used.\
        If a file extension is configured,
        [codeOwners.enableCodeOwnerConfigFilesWithFileExtensions](#codeOwnersEnableCodeOwnerConfigFilesWithFileExtensions)
        should be set to `false`, as otherwise code owner config files with any
        file extension will be validated, which causes validation errors if code
        owner config files with other file extensions use a different owners
        syntax or reference users that do not exist on this Gerrit host.

<a id="codeOwnersEnableCodeOwnerConfigFilesWithFileExtensions">codeOwners.enableCodeOwnerConfigFilesWithFileExtensions</a>
:       Whether file extensions for code owner config files are enabled.\
        If enabled, code owner config files with file extensions are treated as
        regular code owner config files. This means they are validated on
        push/submit (if validation is enabled) and can be imported by other code
        owner config files (regardless of whether they have the same file
        extension or not).\
        Enabling this option should not be used in combination with the
        [plugin.@PLUGIN@.fileExtension](#pluginCodeOwnersFileExtension) option
        as that option uses file extensions to differentiate different sets of
        code owner config files in the same repository/branch which may use
        different code owner syntaxes or reference users that do not exist on
        this Gerrit host. In this case, code owner config files with (other)
        file extensions should not be validated as they likely will fail the
        validation.
        Overrides the global setting
        [plugin.@PLUGIN@.enableCodeOwnerConfigFilesWithFileExtensions](#pluginCodeOwnersEnableCodeOwnerConfigFilesWithFileExtensions)
        in `gerrit.config` and the `codeOwners.fileExtension` setting from
        parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.enableCodeOwnerConfigFilesWithFileExtensions](#pluginCodeOwnersEnableCodeOwnerConfigFilesWithFileExtensions)
        in `gerrit.config` is used.\

<a id="codeOwnersOverrideInfoUrl">codeOwners.overrideInfoUrl</a>
:       A URL for a page that provides project-specific information about how to
        request a code owner override.\
        The frontend displays a link to this page on the change screen so that
        users can discover the override instructions easily.\
        Overrides the global setting
        [plugin.@PLUGIN@.overrideInfoUrl](#pluginCodeOwnersOverrideInfoUrl) in
        `gerrit.config` and the `codeOwners.overrideInfoUrl` setting from parent
        projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.overrideInfoUrl](#pluginCodeOwnersOverrideInfoUrl) in
        `gerrit.config` is used.

<a id="codeOwnersInvalidCodeOwnerConfigInfoUrl">codeOwners.invalidCodeOwnerConfigInfoUrl</a>
:       A URL for a page that provides project-specific information about how
        to deal with invalid code owner config files.\
        This URL is included into error messages that indicate invalid code
        owner config files.\
        Overrides the global setting
        [plugin.@PLUGIN@.invalidCodeOwnerConfigInfoUrl](#pluginCodeOwnersInvalidCodeOwnerConfigInfoUrl)
        in `gerrit.config` and the `codeOwners.invalidCodeOwnerConfigInfoUrl`
        setting from parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.invalidCodeOwnerConfigInfoUrl](#pluginCodeOwnersInvalidCodeOwnerConfigInfoUrl)
        in `gerrit.config` is used.

<a id="codeOwnersEnableImplicitApprovals">codeOwners.enableImplicitApprovals</a>
:       Whether an implicit code owner approval from the change owner is
        assumed.\
        Implicit code owner approvals are only applied on patch sets that are
        uploaded by the change owner (change owner == last patch set uploader).\
        Can be `FALSE`, `TRUE` or `FORCED` (see
        [plugin.@PLUGIN@.enableImplicitApprovals](#pluginCodeOwnersEnableImplicitApprovals)
        for an explanation of these values).\
        If enabled/enforced, code owners need to be aware of their implicit
        approval when they upload new changes for other users (e.g. if a
        contributor pushes a change to a wrong branch and a code owner helps
        them to get it rebased onto the correct branch, the rebased change has
        implicit approvals from the code owner, since the code owner is the
        change owner).\
        If implicit code owner approvals are disabled, code owners can still
        self-approve their own changes by voting on the change.\
        Overrides the global setting
        [plugin.@PLUGIN@.enableImplicitApprovals](#pluginCodeOwnersenableImplicitApprovals)
        in `gerrit.config` and the `codeOwners.enableImplicitApprovals` setting
        from parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.enableImplicitApprovals](#pluginCodeOwnersenableImplicitApprovals)
        in `gerrit.config` is used.

<a id="codeOwnersGlobalCodeOwner">codeOwners.globalCodeOwner</a>
:       The email of a user that should be a code owner globally across all
        branches.\
        If global code owners should be omitted in the code owner suggestion
        (e.g. because they are bots and cannot react to review requests), they
        can be added to the `Service Users` group (since members of this group
        are not suggested as code owners).\
        Can be specified multiple times to set multiple global code owners.\
        Extends the global setting
        [plugin.@PLUGIN@.globalCodeOwner](#pluginCodeOwnersGlobalCodeOwner) in
        `gerrit.config` and the `codeOwners.globalCodeOwner` setting from parent
        projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.globalCodeOwner](#pluginCodeOwnersGlobalCodeOwner) in
        `gerrit.config` is used.

<a id="codeOwnersExemptedUser">codeOwners.exemptedUser</a>
:       The email of a user that should be exempted from requiring code owner
        approvals.\
        If a user is exempted from requiring code owner approvals patch sets
        that are uploaded by this user are automatically code-owner approved.\
        Can be specified multiple times to exempt multiple users.\
        Extends the global setting
        [plugin.@PLUGIN@.exemptedUser](#pluginCodeOwnersExemptedUser) in
        `gerrit.config` and the `codeOwners.exemptedUser` setting from parent
        projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.exemptedUser](#pluginCodeOwnersExemptedUser) in
        `gerrit.config` is used.

<a id="codeOwnersReadOnly">codeOwners.readOnly</a>
:       Whether code owner config files are read-only.\
        Overrides the global setting
        [plugin.@PLUGIN@.readOnly](#pluginCodeOwnersReadOnly) in
        `gerrit.config` and the `codeOwners.readOnly` setting from parent
        projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.readOnly](#pluginCodeOwnersReadOnly) in
        `gerrit.config` is used.

<a id="codeOwnersExemptPureReverts">codeOwners.exemptPureReverts</a>
:       Whether pure revert changes are exempted from needing code owner
        approvals for submit.\
        Only works for pure reverts which have been created through the Gerrit
        [REST API](../../../Documentation/rest-api-change.html#revert-change)
        (but not for pure reverts which were done locally and then pushed to
        Gerrit).\
        Overrides the global setting
        [plugin.@PLUGIN@.exemptPureReverts](#pluginCodeOwnersExemptPureReverts)
        in `gerrit.config` and the `codeOwners.exemptPureReverts` setting from
        parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.exemptPureReverts](#pluginCodeOwnersExemptPureReverts)
        in `gerrit.config` is used.

<a id="codeOwnersEnableValidationOnCommitReceived">codeOwners.enableValidationOnCommitReceived</a>
:       Policy for validating code owner config files when a commit is
        received.\
        Can be `TRUE`, `FALSE`, `DRY_RUN`, `FORCED` or `FORCED_DRY_RUN`. For a
        description of the values see
        [plugin.@PLUGIN@.enableValidationOnCommitReceived](#pluginCodeOwnersEnableValidationOnCommitReceived).\
        Should only be disabled if there is bot that validates the code owner
        config files in open changes as part of a pre-submit validation.\
        Overrides the global setting
        [plugin.@PLUGIN@.enableValidationOnCommitReceived](#pluginCodeOwnersEnableValidationOnCommitReceived)
        in `gerrit.config` and the `codeOwners.enableValidationOnCommitReceived`
        setting from parent projects.\
        Can be overriden on branch-level by setting
        [validation.\<branch\>.enableValidationOnCommitReceived](#validationBranchEnableValidationOnCommitReceived).\
        If not set, the global setting
        [plugin.@PLUGIN@.enableValidationOnCommitReceived](#pluginCodeOwnersEnableValidationOnCommitReceived)
        in `gerrit.config` is used.

<a id="validationBranchEnableValidationOnCommitReceived">validation.\<branch\>.enableValidationOnCommitReceived</a>
:       Branch-level policy for validating code owner config files when a commit
        is received.\
        Applies to all branches that are matched by `<branch>`, which can be
        an exact ref name (e.g. `refs/heads/master`), a ref pattern (e.g.
        `refs/heads/*`) or a regular expression (e.g. `^refs/heads/stable-.*`).\
        If a branches matches multiple validation subsections it is undefined
        which of the subsections takes precedence.\
        Overrides the project-level configuration for validating code owner
        config files when a commit is received that is configured by
        [codeOwners.enableValidationOnCommitReceived](#codeOwnersEnableValidationOnCommitReceived).\
        For further details see the description of
        [codeOwners.enableValidationOnCommitReceived](#codeOwnersEnableValidationOnCommitReceived).

<a id="codeOwnersEnableValidationOnSubmit">codeOwners.enableValidationOnSubmit</a>
:       Policy for validating code owner config files when a change is
        submitted.\
        Can be `TRUE`, `FALSE`, `DRY_RUN`, `FORCED` or `FORCED_DRY_RUN`. For a
        description of the values see
        [plugin.@PLUGIN@.enableValidationOnSubmit](#pluginCodeOwnersEnableValidationOnSubmit).\
        Disabling the submit validation is not recommended.\
        Overrides the global setting
        [plugin.@PLUGIN@.enableValidationOnSubmit](#pluginCodeOwnersEnableValidationOnSubmit)
        in `gerrit.config` and the `codeOwners.enableValidationOnSubmit` setting
        from parent projects.\
        Can be overriden on branch-level by setting
        [validation.\<branch\>.enableValidationOnSubmit](#validationBranchEnableValidationOnSubmit).\
        If not set, the global setting
        [plugin.@PLUGIN@.enableValidationOnSubmit](#pluginCodeOwnersEnableValidationOnSubmit)
        in `gerrit.config` is used.

<a id="validationBranchEnableValidationOnSubmit">validation.\<branch\>.enableValidationOnSubmit</a>
:       Branch-level policy for validating code owner config files when a change
        is submitted.\
        Applies to all branches that are matched by `<branch>`, which can be
        an exact ref name (e.g. `refs/heads/master`), a ref pattern (e.g.
        `refs/heads/*`) or a regular expression (e.g. `^refs/heads/stable-.*`).\
        If a branches matches multiple validation subsections it is undefined
        which of the subsections takes precedence.\
        Overrides the project-level configuration for validating code owner
        config files when a change is submitted that is configured by
        [codeOwners.enableValidationOnSubmit](#codeOwnersEnableValidationOnSubmit).\
        For further details see the description of
        [codeOwners.enableValidationOnSubmit](#codeOwnersEnableValidationOnSubmit).

<a id="codeOwnersRejectNonResolvableCodeOwners">codeOwners.rejectNonResolvableCodeOwners</a>
:       Whether modifications of code owner config files that newly add
        non-resolvable code owners should be rejected on commit received and
        submit.\
        If `true` newly added non-resolveable code owners are reported as errors
        and the commit is rejected.\
        If `false` newly added non-resolvable code owners are only reported as
        warnings and the commit is not rejected.\
        This setting has no effect if the validation is disabled via
        [enableValidationOnCommitReceived](#codeOwnersEnableValidationOnCommitReceived)
        or [enableValidationOnSubmit](#codeOwnersEnableValidationOnSubmit).
        Overrides the global setting
        [plugin.@PLUGIN@.rejectNonResolvableCodeOwners](#pluginCodeOwnersRejectNonResolvableCodeOwners)
        in `gerrit.config` and the `codeOwners.rejectNonResolvableCodeOwners`
        setting from parent projects.\
        Can be overriden on branch-level by setting
        [validation.\<branch\>.rejectNonResolvableCodeOwners](#validationBranchRejectNonResolvableCodeOwners).\
        If not set, the global setting
        [plugin.@PLUGIN@.rejectNonResolvableCodeOwners](#pluginCodeOwnersRejectNonResolvableCodeOwners)
        in `gerrit.config` is used.

<a id="validationBranchRejectNonResolvableCodeOwners">validation.\<branch\>.rejectNonResolvableCodeOwners</a>
:       Branch-level configuration to control whether modifications of code
        owner config files that newly add non-resolvable code owners should be
        rejected on commit received and submit.\
        Applies to all branches that are matched by `<branch>`, which can be
        an exact ref name (e.g. `refs/heads/master`), a ref pattern (e.g.
        `refs/heads/*`) or a regular expression (e.g. `^refs/heads/stable-.*`).\
        If a branches matches multiple validation subsections it is undefined
        which of the subsections takes precedence.\
        Overrides the project-level configuration for rejecting non-resolvable
        code owners that is configured by
        [codeOwners.rejectNonResolvableCodeOwners](#codeOwnersRejectNonResolvableCodeOwners).\
        For further details see the description of
        [codeOwners.rejectNonResolvableCodeOwners](#codeOwnersRejectNonResolvableCodeOwners).

<a id="codeOwnersRejectNonResolvableImports">codeOwners.rejectNonResolvableImports</a>
:       Whether modifications of code owner config files that newly add
        non-resolvable imports should be rejected on commit received an submit.\
        If `true` newly added non-resolveable imports are reported as errors and
        the commit is rejected.\
        If `false` newly added non-resolvable imports are only reported as
        warnings and the commit is not rejected.\
        This setting has no effect if the validation is disabled via
        [enableValidationOnCommitReceived](#codeOwnersEnableValidationOnCommitReceived)
        or [enableValidationOnSubmit](#codeOwnersEnableValidationOnSubmit).
        Overrides the global setting
        [plugin.@PLUGIN@.rejectNonResolvableImports](#pluginCodeOwnersRejectNonResolvableImports)
        in `gerrit.config` and the `codeOwners.rejectNonResolvableImports`
        setting from parent projects.\
        Can be overriden on branch-level by setting
        [validation.\<branch\>.rejectNonResolvableImports](#validationBranchRejectNonResolvableImports).\
        If not set, the global setting
        [plugin.@PLUGIN@.rejectNonResolvableImports](#pluginCodeOwnersRejectNonResolvableImports)
        in `gerrit.config` is used.

<a id="validationBranchRejectNonResolvableImports">validation.\<branch\>.rejectNonResolvableImports</a>
:       Branch-level configuration to control whether modifications of code
        owner config files that newly add non-resolvable imports should be
        rejected on commit received and submit.\
        Applies to all branches that are matched by `<branch>`, which can be
        an exact ref name (e.g. `refs/heads/master`), a ref pattern (e.g.
        `refs/heads/*`) or a regular expression (e.g. `^refs/heads/stable-.*`).\
        If a branches matches multiple validation subsections it is undefined
        which of the subsections takes precedence.\
        Overrides the project-level configuration for rejecting non-resolvable
        imports that is configured by
        [codeOwners.rejectNonResolvableImports](#codeOwnersRejectNonResolvableImports).\
        For further details see the description of
        [codeOwners.rejectNonResolvableImports](#codeOwnersRejectNonResolvableImports).

<a id="codeOwnersRequiredApproval">codeOwners.requiredApproval</a>
:       Approval that is required from code owners to approve the files in a
        change.\
        Any approval on the configured label that has a value >= the configured
        value is considered as code owner approval.\
        The required approval must be specified in the format
        "\<label-name\>+\<label-value\>".\
        The configured label must exist for all projects for which this setting
        applies (all child projects that have code owners enabled and for which
        this setting is not overridden).\
        If the definition of the configured label has [copy
        rules](../../../Documentation/config-labels.html#label_copyAnyScore)
        enabled so that votes are sticky across patch sets, also the code owner
        approvals will be sticky.\
        If the definition of the configured label [ignores self
        approvals](../../../Documentation/config-labels.html#label_ignoreSelfApproval)
        from the uploader, any vote from the uploader is ignored for the code
        owners check.\
        Overrides the global setting
        [plugin.@PLUGIN@.requiredApproval](#pluginCodeOwnersRequiredApproval) in
        `gerrit.config` and the `codeOwners.requiredApproval` setting from
        parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.requiredApproval](#pluginCodeOwnersRequiredApproval) in
        `gerrit.config` is used.

<a id="codeOwnersOverrideApproval">codeOwners.overrideApproval</a>
:       Approval that counts as override for the code owners submit check.\
        Any approval on the configured label that has a value >= the configured
        value is considered as code owner override.\
        The override approval must be specified in the format
        "\<label-name\>+\<label-value\>".\
        Can be specifed multiple times to configure multiple override approvals.
        If multiple approvals are configured, any of them is sufficient to
        override the code owners submit check.\
        The configured labels must exist for all projects for which this setting
        applies (all projects that have code owners enabled and for which this
        setting is not overridden).\
        If the definition of the configured labels has [copy
        rules](../../../Documentation/config-labels.html#label_copyAnyScore)
        enabled so that votes are sticky across patch sets, also the code owner
        overrides will be sticky.\
        If the definition of a configured label [ignores self
        approvals](../../../Documentation/config-labels.html#label_ignoreSelfApproval)
        from the uploader, any override vote from the uploader on that label is
        ignored for the code owners check.\
        Extends the global setting
        [plugin.@PLUGIN@.overrideApproval](#pluginCodeOwnersOverrideApproval) in
        `gerrit.config` and the `codeOwners.overrideApproval` setting from
        parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.overrideApproval](#pluginCodeOwnersOverrideApproval) in
        `gerrit.config` is used.

<a id="codeOwnersMergeCommitStrategy">codeOwners.mergeCommitStrategy</a>
:       Strategy that defines for merge commits which files require code owner
        approvals.\
        Can be `ALL_CHANGED_FILES` or `FILES_WITH_CONFLICT_RESOLUTION`
        (see [plugin.@PLUGIN@.mergeCommitStrategy](#pluginCodeOwnersMergeCommitStrategy)
        for an explanation of these values).\
        Overrides the global setting
        [plugin.@PLUGIN@.mergeCommitStrategy](#pluginCodeOwnersMergeCommitStrategy)
        in `gerrit.config` and the `codeOwners.mergeCommitStrategy` setting from
        parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.mergeCommitStrategy](#pluginCodeOwnersMergeCommitStrategy)
        in `gerrit.config` is used.

<a id="codeOwnersFallbackCodeOwners">codeOwners.fallbackCodeOwners</a>
:       Policy that controls who should own paths that have no code owners
        defined. This policy only applies if the inheritance of parent code
        owners hasn't been explicity disabled in a relevant code owner config
        file and if there are no unresolved imports.\
        Can be `NONE`, `PROJECT_OWNERS` or `ALL_USERS` (see
        [plugin.@PLUGIN@.fallbackCodeOwners](#pluginCodeOwnersFallbackCodeOwners)
        for an explanation of these values).\
        Overrides the global setting
        [plugin.@PLUGIN@.fallbackCodeOwners](#pluginCodeOwnersFallbackCodeOwners)
        in `gerrit.config` and the `codeOwners.fallbackCodeOwners` setting from
        parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.fallbackCodeOwners](#pluginCodeOwnersFallbackCodeOwners)
        in `gerrit.config` is used.

<a id="codeOwnersMaxPathsInChangeMessages">codeOwners.maxPathsInChangeMessages</a>
:       The @PLUGIN@ plugin lists owned paths in change messages when:
        \
        1. A code owner votes on the [code owners
        label](#pluginCodeOwnersRequiredApproval):\
        The paths that are affected by the vote are listed in the change message
        that is posted when the vote is applied.\
        \
        2. A code owner is added as reviewer:\
        The paths that are owned by the reviewer are posted as a change
        message.\
        \
        This configuration parameter controls the maximum number of paths that
        are included in change messages. This is to prevent that the change
        messages become too big for large changes that touch many files.\
        Setting the value to `0` disables including owned paths into change
        messages.\
        Overrides the global setting
        [plugin.@PLUGIN@.maxPathsInChangeMessages](#pluginCodeOwnersMaxPathsInChangeMessages)
        in `gerrit.config` and the `codeOwners.maxPathsInChangeMessages` setting
        from parent projects.\
        If not set, the global setting
        [plugin.@PLUGIN@.maxPathsInChangeMessages](#pluginCodeOwnersMaxPathsInChangeMessages)
        in `gerrit.config` is used.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)

