# Gerrit Code Review code-owners plugin

This plugin provides support for defining code owners for files in a repository.

If the code-owners plugin is enabled, changes can only be submitted if all
touched files are covered by approvals from code owners.

For a detailed description of the plugin functionality please refer to the
[plugin documentation](https://android-review.googlesource.com/plugins/code-owners/Documentation/index.html).

IMPORTANT: Before installing/enabling the plugin follow the instructions from
the [setup guide](https://android-review.googlesource.com/plugins/code-owners/Documentation/setup-guide.html).

NOTE: The plugin documentation only renders correctly when the plugin is
installed in Gerrit and the documentation is accessed via
https://<gerrit-host>/plugins/code-owners/Documentation/index.html. If you want
to read the documentation before installing the plugin, you can find it properly
rendered
[here](https://android-review.googlesource.com/plugins/code-owners/Documentation/index.html).

## JavaScript Plugin

Ensure the Code Owners plugin is cloned in the gerrit/plugins folder.
From the root of the gerrit repository.

```
bazel test //plugins/code-owners/web:karma_test
```

For testing the plugin with the 
[Gerrit FE Dev Helper](https://gerrit.googlesource.com/gerrit-fe-dev-helper/)
the command below builds 

```
    bazel build //plugins/code-owners/web:code-owners
    ln -s bazel-bin/plugins/code-owners/web/code-owners.js polygerrit-ui/app/plugins/
```

If the symlink command does not work then try copying manually

```
    cp -f bazel-bin/plugins/code-owners/ui/code-owners.js polygerrit-ui/app/plugins/
```



and let the Dev Helper redirect from
`.+/plugins/code-owners/static/code-owners.js` to
`http://localhost:8081/plugins/code-owners.js`.
