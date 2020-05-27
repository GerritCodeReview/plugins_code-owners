# @PLUGIN@ - Configuration

The configuration of the @PLUGIN@ plugin is stored in the `gerrit.config` file
in the `plugin.@PLUGIN@` subsection.

# <a id="globalConfiguration">Global configuration in gerrit.config</a>

<a id="pluginCodeOwnersBackend">plugin.@PLUGIN@.backend</a>
:       The code owners backend that should be used.

        The following code owner backends are supported:

        * `find-owners`: Code owners backend that supports the syntax of the
        [find-owners](https://gerrit-review.googlesource.com/admin/repos/plugins/find-owners)
        plugin.

        By default `find-owners`.

Part of [Gerrit Code Review](../../../Documentation/index.html)

