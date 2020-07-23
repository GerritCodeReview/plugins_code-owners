# @PLUGIN@ - Configuration

The global configuration of the @PLUGIN@ plugin is stored in the `gerrit.config`
file in the `plugin.@PLUGIN@` subsection.

In addition some configuration can be done on the project level in
`@PLUGIN@.config` files that are stored in the `refs/meta/config` branches of
the projects.

Parameters that are not set for a project are inherited from the parent project.

# <a id="globalConfiguration">Global configuration in gerrit.config</a>

<a id="pluginCodeOwnersBackend">plugin.@PLUGIN@.backend</a>
:       The code owners backend that should be used.\
        Can be overridden per project by setting
        [codeOwners.backend](#codeOwnersBackend) in
        `@PLUGIN@.config`.\
        The supported code owner backends are listed at the
        [Backends](backends.html) page.\
        By default `find-owners`.

<a id="pluginCodeOwnersRequiredApproval">plugin.@PLUGIN@.requiredApproval</a>
:       Approval that is required from code owners to approve the files in a
        change.\
        The required approval must be specified in the format
        "<label-name>+<label-value>".\
        The configured label must exist for all projects for which this setting
        applies (all projects that have code owners enabled and for which this
        setting is not overridden).\
        Can be overridden per project by setting
        [codeOwners.requiredApproval](#codeOwnersRequiredApproval) in
        `@PLUGIN@.config`.\
        By default "Code-Review+1".

<a id="pluginCodeOwnersEnableExperimentalRestEndpoints">plugin.@PLUGIN@.enableExperimentalRestEndpoints</a>
:       Whether experimental REST endpoints are enabled.\
        By default `false`.

# <a id="projectConfiguration">Project configuration in @PLUGIN@.config</a>

<a id="codeOwnersDisabled">codeOwners.disabled</a>
:       Whether the code owners functionality is disabled for the project.\
        If `true` the code owners API is disabled and submitting changes doesn't
        require code owner approvals.\
        This allows projects to opt-out of the code owners functionality.\
        By default `false`.

<a id="codeOwnersDisabledBranch">codeOwners.disabledBranch</a>
:       An exact ref, a ref pattern or a regular expression to disable the code
        owners functionality for the matched branches.\
        For matched branches the code owners API is disabled and submitting
        changes doesn't require code owner approvals.\
        This allows branches to opt-out of the code owners functionality.\
        Can be set multiple times.\
        By default unset.

<a id="codeOwnersBackend">codeOwners.backend</a>
:       The code owners backend that should be used for the project.\
        Overrides the global setting
        [plugin.@PLUGIN@.backend](#pluginCodeOwnersBackend) in `gerrit.config`.\
        The supported code owner backends are listed at the
        [Backends](backends.html) page.\
        If not set, the global setting
        [plugin.@PLUGIN@.backend](#pluginCodeOwnersBackend) in `gerrit.config`\
        is used.

<a id="codeOwners.branch.backend">codeOwners.\<branch\>.backend</a>
:       The code owners backend that should be used for this branch.\
        The branch can be the short or full name. If both configurations exist
        the one for the full name takes precedence.\
        Overrides the per repository setting
        [codeOwners.backend](#codeOwnersBackend).\
        The supported code owner backends are listed at the
        [Backends](backends.html) page.\
        If not set, the project level configuration
        [codeOwners.backend](#codeOwnersBackend) is used.

<a id="codeOwnersRequiredApproval">codeOwners.requiredApproval</a>
:       Approval that is required from code owners to approve the files in a
        change.\
        The required approval must be specified in the format
        "<label-name>+<label-value>".\
        The configured label must exist for all projects for which this setting
        applies (all child projects that have code owners enabled and for which
        this setting is not overridden).\
        Overrides the global setting
        [plugin.@PLUGIN@.requiredApproval](#pluginCodeOwnersRequiredApproval) in
        `gerrit.config`.\
        If not set, the global setting
        [plugin.@PLUGIN@.requiredApproval](#pluginCodeOwnersRequiredApproval) in
        `gerrit.config` is used.

Part of [Gerrit Code Review](../../../Documentation/index.html)

