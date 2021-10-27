# Gerrit Code Review code-owners plugin

This plugin provides support for defining code owners for files in a repository.

If the code-owners plugin is enabled, changes can only be submitted if all
touched files are covered by approvals from code owners.

Also see [resources/Documentation/about.md](./resources/Documentation/about.md).

IMPORTANT: Before installing/enabling the plugin follow the instructions from
the setup guide, see [resources/Documentation/setup-guide.md](./resources/Documentation/setup-guide.md).


## JavaScript Plugin

For testing the plugin with
[Gerrit FE Dev Helper](https://gerrit.googlesource.com/gerrit-fe-dev-helper/)
build the JavaScript bundle and copy it to the `plugins/` folder:

    bazel build //plugins/code-owners/ui:code-owners
    cp -f bazel-bin/plugins/code-owners/ui/code-owners.js plugins/

and let the Dev Helper redirect from 
`.+/plugins/code-owners/static/code-owners.js` to
`http://localhost:8081/plugins_/code-owners.js`.