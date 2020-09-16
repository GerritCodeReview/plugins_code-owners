# find-owners Backend

The `find-owners` backend supports the syntax of the
[find-owners](https://gerrit-review.googlesource.com/admin/repos/plugins/find-owners)
plugin (with some minor extensions). It is the backend that is used by default
if no other backend is explicitly [configured](config.html#codeOwnersBackend)
for a project or branch.

## Code owner configuration

Code owners are defined in `OWNERS` files which are stored in the source tree.
The code owners that are defined in an `OWNERS` file apply to the directory that
contains the `OWNERS` file, and all its subdirectories (except if a subdirectory
contains an `OWNERS` file that disables the inheritance of code owners from the
parent directories).

**NOTE:** It's possible that projects have a [file extension for code owner
config files](config.html#codeOwnersFileExtension) configured. In this case the
code owners are defined in `OWNERS.<file-extension>` files and `OWNERS` files
are ignored.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
