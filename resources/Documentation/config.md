# Configuration

The global configuration of the @PLUGIN@ plugin is stored in the `gerrit.config`
file in the `plugin.@PLUGIN@` subsection.

## <a id="projectLevelConfigFile">
In addition some configuration can be done on the project level in
`@PLUGIN@.config` files that are stored in the `refs/meta/config` branches of
the projects.

Parameters that are not set for a project are inherited from the parent project.

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
        Can be overridden per project by setting
        [codeOwners.disabledBrancg](#codeOwnersDisabledBranch) in
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
        Allows to use different owner configurations for upstream and internal
        in the same repository. E.g. if upstream uses `OWNERS` code owner config
        files (no file extension configured) one could set `internal` as file
        extension internally so that internally `OWNERS.internal` files are used
        and the existing `OWNERS` files are ignored.\
        Can be overridden per project by setting
        [codeOwners.fileExtension](#codeOwnersFileExtension) in
        `@PLUGIN@.config`.\
        By default unset (no file extension is used).

<a id="pluginCodeOwnersOverrideInfoUrl">plugin.@PLUGIN@.overrideInfoUrl</a>
:       A URL for a page that provides host-specific information about how to
        request a code owner override.\
        The frontend displays a link to this page on the change screen so that
        users can discover the override instructions easily.\
        Can be overridden per project by setting
        [codeOwners.overrideInfoUrl](#codeOwnersFileExtension) in
        `@PLUGIN@.config`.\
        By default unset (no override info URL).

<a id="pluginCodeOwnersEnableImplicitApprovals">plugin.@PLUGIN@.enableImplictApprovals</a>
:       Whether an implicit code owner approval from the last uploader is
        assumed.\
        If enabled, code owners need to be aware of their implicit approval when
        they upload new patch sets for other users (e.g. if a contributor pushes
        a change to a wrong branch and a code owner helps them to get it rebased
        onto the correct branch, the rebased change has implicit approvals from
        the code owner, since the code owner is the uploader).\
        If implicit code owner approvals are disabled, code owners can still
        self-approve their own changes by voting on the change.\
        Can be overridden per project by setting
        [codeOwners.enableImplictApprovals](#codeOwnersEnableImplicitApprovals)
        in `@PLUGIN@.config`.\
        By default `false`.

<a id="pluginCodeOwnersGlobalCodeOwner">plugin.@PLUGIN@.globalCodeOwner</a>
:       The email of a user that should be a code owner globally across all
        branches.\
        If global code owners should be omitted in the code owner suggestion
        (e.g. because they are bots and cannot react to review requests), they
        can be added to the `Service Users` group (since members of this group
        are not suggested as code owners).\
        Can be specified multiple time to set multiple global code owners.\
        Can be overridden per project by setting
        [codeOwners.globalCodeOwner](#codeOwnersGlobalCodeOwner) in
        `@PLUGIN@.config`.\
        By default unset (no global code owners).

<a id="pluginCodeOwnersReadOnly">plugin.@PLUGIN@.readOnly</a>
:       Whether code owner config files are read-only.\
        Can be overridden per project by setting
        [codeOwners.readOnly](#codeOwnersReadOnly) in
        `@PLUGIN@.config`.\
        By default `false`.

<a id="pluginCodeOwnersEnableValidationOnCommitReceived">plugin.@PLUGIN@.enableValidationOnCommitReceived</a>
:       Policy for validating code owner config files when a commit is received.
        Allowed values are `true` (the code owner config file validation is
        enabled and the upload of invalid code owner config files is rejected),
        `false` (the code owner config file validation is disabled, invalid code
        owner config files are not rejected) and `dry_run` (code owner config
        files are validated, but invalid code owner config files are not
        rejected).\
        Should only be disabled if there is bot that validates the code owner
        config files in open changes as part of a pre-submit validation.\
        Can be overridden per project by setting
        [codeOwners.enableValidationOnCommitReceived](#codeOwnersEnableValidationOnCommitReceived)
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
        The required approval must be specified in the format
        "\<label-name\>+\<label-value\>".\
        The configured label must exist for all projects for which this setting
        applies (all projects that have code owners enabled and for which this
        setting is not overridden).\
        If the definition of the configured label has [copy
        rules](../../../Documentation/config-labels.html#label_copyAnyScore)
        enabled so that votes are sticky across patch sets, also the code owner
        approvals will be sticky.\
        Can be overridden per project by setting
        [codeOwners.requiredApproval](#codeOwnersRequiredApproval) in
        `@PLUGIN@.config`.\
        By default "Code-Review+1".

<a id="pluginCodeOwnersOverrideApproval">plugin.@PLUGIN@.overrideApproval</a>
:       Approval that is required to override the code owners submit check.\
        The override approval must be specified in the format
        "\<label-name\>+\<label-value\>".\
        The configured label must exist for all projects for which this setting
        applies (all projects that have code owners enabled and for which this
        setting is not overridden).\
        Can be overridden per project by setting
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
        Can be `NONE` or `ALL_USERS`.\
        \
        `NONE`:\
        Paths for which no code owners are defined are owned by no one. This
        means changes that touch these files can only be submitted with a code
        owner override.\
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
        Can be overridden per project by setting
        [codeOwners.fallbackCodeOwners](#codeOwnersFallbackCodeOwners) in
        `@PLUGIN@.config`.\
        By default `NONE`.

# <a id="projectConfiguration">Project configuration in @PLUGIN@.config</a>

<a id="codeOwnersDisabled">codeOwners.disabled</a>
:       Whether the code owners functionality is disabled for the project.\
        If `true` submitting changes doesn't require code owner approvals.\
        This allows projects to opt-out of the code owners functionality.\
        Overrides the global setting
        [plugin.@PLUGIN@.disabled](#pluginCodeOwnersDisabled) in
        `gerrit.config`.\
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
        By default unset.

<a id="codeOwnersBackend">codeOwners.backend</a>
:       The code owners backend that should be used for the project.\
        Overrides the global setting
        [plugin.@PLUGIN@.backend](#pluginCodeOwnersBackend) in `gerrit.config`.\
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
        [codeOwners.backend](#codeOwnersBackend).\
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
        Allows to use different owner configurations for upstream and internal
        in the same repository. E.g. if upstream uses `OWNERS` code owner config
        files (no file extension configured) one could set `internal` as file
        extension internally so that internally `OWNERS.internal` files are used
        and the existing `OWNERS` files are ignored.\
        Overrides the global setting
        [plugin.@PLUGIN@.fileExtension](#pluginCodeOwnersFileExtension) in
        `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.fileExtension](#pluginCodeOwnersFileExtension) in
        `gerrit.config` is used.

<a id="codeOwnersOverrideInfoUrl">codeOwners.overrideInfoUrl</a>
:       A URL for a page that provides project-specific information about how to
        request a code owner override.\
        The frontend displays a link to this page on the change screen so that
        users can discover the override instructions easily.\
        Overrides the global setting
        [plugin.@PLUGIN@.overrideInfoUrl](#pluginCodeOwnersOverrideInfoUrl) in
        `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.overrideInfoUrl](#pluginCodeOwnersOverrideInfoUrl) in
        `gerrit.config` is used.

<a id="codeOwnersEnableImplicitApprovals">codeOwners.enableImplicitApprovals</a>
:       Whether an implicit code owner approval from the last uploader is
        assumed.\
        If enabled, code owners need to be aware of their implicit approval when
        they upload new patch sets for other users (e.g. if a contributor pushes
        a change to a wrong branch and a code owner helps them to get it rebased
        onto the correct branch, the rebased change has implicit approvals from
        the code owner, since the code owner is the uploader).\
        If implicit code owner approvals are disabled, code owners can still
        self-approve their own changes by voting on the change.\
        Overrides the global setting
        [plugin.@PLUGIN@.enableImplicitApprovals](#pluginCodeOwnersenableImplicitApprovals)
        in `gerrit.config`.\
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
        Can be specified multiple time to set multiple global code owners.\
        Overrides the global setting
        [plugin.@PLUGIN@.globalCodeOwner](#pluginCodeOwnersGlobalCodeOwner) in
        `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.globalCodeOwner](#pluginCodeOwnersGlobalCodeOwner) in
        `gerrit.config` is used.

<a id="codeOwnersReadOnly">codeOwners.readOnly</a>
:       Whether code owner config files are read-only.\
        Overrides the global setting
        [plugin.@PLUGIN@.readOnly](#pluginCodeOwnersReadOnly) in
        `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.readOnly](#pluginCodeOwnersReadOnly) in
        `gerrit.config` is used.

<a id="codeOwnersEnableValidationOnCommitReceived">codeOwners.enableValidationOnCommitReceived</a>
:       Policy for validating code owner config files when a commit is received.
        Allowed values are `true` (the code owner config file validation is
        enabled and the upload of invalid code owner config files is rejected),
        `false` (the code owner config file validation is disabled, invalid code
        owner config files are not rejected) and `dry_run` (code owner config
        files are validated, but invalid code owner config files are not
        rejected).\
        Should only be disabled if there is bot that validates the code owner
        config files in open changes as part of a pre-submit validation.\
        Overrides the global setting
        [plugin.@PLUGIN@.enableValidationOnCommitReceived](#pluginCodeOwnersEnableValidationOnCommitReceived)
        in `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.enableValidationOnCommitReceived](#pluginCodeOwnersEnableValidationOnCommitReceived)
        in `gerrit.config` is used.

<a id="codeOwnersRequiredApproval">codeOwners.requiredApproval</a>
:       Approval that is required from code owners to approve the files in a
        change.\
        The required approval must be specified in the format
        "\<label-name\>+\<label-value\>".\
        The configured label must exist for all projects for which this setting
        applies (all child projects that have code owners enabled and for which
        this setting is not overridden).\
        If the definition of the configured label has [copy
        rules](../../../Documentation/config-labels.html#label_copyAnyScore)
        enabled so that votes are sticky across patch sets, also the code owner
        approvals will be sticky.\
        Overrides the global setting
        [plugin.@PLUGIN@.requiredApproval](#pluginCodeOwnersRequiredApproval) in
        `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.requiredApproval](#pluginCodeOwnersRequiredApproval) in
        `gerrit.config` is used.

<a id="codeOwnersOverrideApproval">codeOwners.overrideApproval</a>
:       Approval that is required to override the code owners submit check.\
        The override approval must be specified in the format
        "\<label-name\>+\<label-value\>".\
        The configured label must exist for all projects for which this setting
        applies (all projects that have code owners enabled and for which this
        setting is not overridden).\
        Overrides the global setting
        [plugin.@PLUGIN@.overrideApproval](#pluginCodeOwnersOverrideApproval) in
        `gerrit.config`.\
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
        in `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.mergeCommitStrategy](#pluginCodeOwnersMergeCommitStrategy)
        in `gerrit.config` is used.

<a id="codeOwnersFallbackCodeOwners">codeOwners.fallbackCodeOwners</a>
:       Policy that controls who should own paths that have no code owners
        defined. This policy only applies if the inheritance of parent code
        owners hasn't been explicity disabled in a relevant code owner config
        file and if there are no unresolved imports.\
        Can be `NONE` or `ALL_USERS` (see
        [plugin.@PLUGIN@.fallbackCodeOwners](#pluginCodeOwnersFallbackCodeOwners)
        for an explanation of these values).\
        Overrides the global setting
        [plugin.@PLUGIN@.fallbackCodeOwners](#pluginCodeOwnersFallbackCodeOwners)
        in `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.fallbackCodeOwners](#pluginCodeOwnersFallbackCodeOwners)
        in `gerrit.config` is used.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)

