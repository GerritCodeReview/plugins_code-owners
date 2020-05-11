The @PLUGIN@ plugin provides support for defining code owners for files in a
repository.

If the @PLUGIN@ plugin is enabled, changes can only be submitted if all
touched files are covered by approvals from code owners.

This plugin is specifically developed to support code owners for the Chrome and
Android teams at Google. This means some of the functionality and design
decisons are driven by Google-specific use-cases. Nonetheless the support for
code owners is pretty generic and configurable so that it should be suitable
for other teams as well.

# Alternatives

Similar functionality is provided by the find-owners plugin and the owners
plugin.
