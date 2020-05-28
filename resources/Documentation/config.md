# @PLUGIN@ - Configuration

The global configuration of the @PLUGIN@ plugin is stored in the `gerrit.config`
file in the `plugin.@PLUGIN@` subsection.

In addition some configuration can be done on the project level in
`@PLUGIN@.config` files that are stored in the `refs/meta/config` branches of
the projects.

Parameters that are not set for a project are inherited from the parent project.

# <a id="globalConfiguration">Global configuration in gerrit.config</a>

<a id="pluginCodeOwnersBackend">plugin.@PLUGIN@.backend</a>
:       The code owners backend that should be used.

        Can be overridden per project by setting
        [codeOwners.backend](#codeOwnersBackend) in `@PLUGIN@.config`.

        The supported code owner backends are listed at the
        [Backends](../../../Documentation/backends.html) page.

        By default `find-owners`.


# <a id="projectConfiguration">Project configuration in @PLUGIN@.config</a>

<a id="codeOwnersBackend">codeOwners.backend</a>
:       The code owners backend that should be used for the project.

        Overrides the global setting
        [plugin.@PLUGIN@.backend](#pluginCodeOwnersBackend) in `gerrit.config`.

        The supported code owner backends are listed at the
        [Backends](../../../Documentation/backends.html) page.

        If not set, the global setting
        [plugin.@PLUGIN@.backend](#pluginCodeOwnersBackend) in `gerrit.config`
        is used.

Part of [Gerrit Code Review](../../../Documentation/index.html)

