# Alternative Plugins

Similar functionality is provided by the following plugins:

* [find-owners](#findOwners) plugin
* [owners](#owners) plugin

## <a id="findOwners">find-owners plugin (deprecated)

**Status:** deprecated, from Gerrit 3.4 on the `code-owners` plugin should be used instead\
**Repository:** [plugins/find-owners](https://gerrit-review.googlesource.com/admin/repos/plugins/find-owners)\
**Documentation:** [about](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/resources/Documentation/about.md), [syntax](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/resources/Documentation/syntax.md), [REST API](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/resources/Documentation/rest-api.md), [config](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/resources/Documentation/config.md)

### <a id="findOwnersCompatibility">Compatibility with the code-owners plugin

The [find-owners](backend-find-owners.html) backend in the`code-owners` plugin
supports the same syntax for `OWNERS` files as the `find-owners` plugin.  This
means that existing `OWNERS` files continue to work with the `code-owners`
plugin and no migration for the `OWNERS` files is required.

**IMPORTANT:** When migrating to the `code-owners` plugin, make sure that it is
correctly configured (see [setup guide](setup-guide.html)).

**NOTE:** The REST API of the `code-owners` plugin is completely different than
the REST API of the `find-owners` plugin. This means callers of the REST API
must be adapted to the new API.

**NOTE:** The `OWNERS` syntax in the `code-owners` plugin supports some
additional features. This means that `OWNERS` files that work with the
`code-owners` plugin may not work with the `find-owners` plugin.

### <a id="findOwnersFunctionality">Functionality

* Basic support for defining code owners:
    * Code owners can be specified in `OWNERS` files that can appear in any
      directory in the source branch.
    * Code owners can be specified by email.
    * Inheritance from parent directories is supported and can be disabled.
    * Including an `OWNERS` file from another directory in the same project or
      from another project on the same host is possible (same branch is assumed).
    * File globs can be used.
    * See [documentation](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/resources/Documentation/syntax.md) for the supported syntax.
<br><br>
* Prolog rule to prevent submitting changes without owner approvals.
    * A change can be exempted from owners approval by setting a footer in the
      commit message.
<br><br>
* Basic UI:
    * Supports to discover users that can grant owner approval on a change
      (weighed suggestion) and add them as reviewer to the change.
    * Missing owner approvals are visualized on a change.
    * Owner approval is granted by voting on the `Code-Review` label.
<br><br>
* REST endpoints:
    * [Action](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/java/com/googlesource/gerrit/plugins/findowners/Action.java) REST endpoint:
        * `GET /changes/<change-id>/revisions/<revision-id>/find-owners`
        * returns a [RestResult](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/java/com/googlesource/gerrit/plugins/findowners/RestResult.java) which contains:
            * a file to list of owners map
            * a list of owner infos with weight infos
            * fields for debugging
            * fields for change, patch set, current reviewers and changed files
    * [GetOwners](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/java/com/googlesource/gerrit/plugins/findowners/GetOwners.java) REST endpoint:
        * `GET /changes/<change-id>/owners`
        * Delegates to Action REST endpoint (see above)
    * Also see [REST endpoint documentation](https://gerrit.googlesource.com/plugins/find-owners/+/HEAD/src/main/resources/Documentation/rest-api.md)

## <a id="owners">owners plugin + owners-autoassign plugin

**Status:** maintained by the Gerrit open source community (no Google involvement)\
**Repository:** [plugins/owners](https://gerrit-review.googlesource.com/admin/repos/plugins/owners)\
**Documentation:** [readme](https://gerrit.googlesource.com/plugins/owners/+/HEAD/README.md), [config & syntax](https://gerrit.googlesource.com/plugins/owners/+/HEAD/owners/src/main/resources/Documentation/config.md)

### <a id="ownersCompatibility">Compatibility with the code-owners plugin

The `OWNERS` sytax that is used by the `owners` plugin is **not** compatible
with the `code-owners` plugin. This means any migration from the `owners` plugin
to the `code-owners` plugin (and vice versa) requires migrating all existing
`OWNERS` files.

**NOTE:** It would be feasible to implement a new [backend](backends.html) in
the `code-owners` plugin that supports the syntax of the `owners` plugin
(contributions are welcome).

**NOTE:** The `owners` plugin supports groups as code owners, which are not
supported by the `code-owners` plugin.

### <a id="ownersFunctionality">Functionality

* Basic support for defining code owners in the source branch and globally for
  a repository:
    * Code owners can be specified in `OWNERS` files that can appear in any
      directory in the source branch.
    * Code owners can be specified on repository level by an `OWNERS` file in
      the `refs/meta/config` branch.
    * Code owners can be specified by email or full name.
    * Groups can be specified as code owners (by group name and group UUID).
    * Inheritance from parent directories is supported and can be disabled.
    * Regular expressions can be used.
    * Syntax is based on YAML.
    * See [documentation](https://gerrit.googlesource.com/plugins/owners/+/HEAD/owners/src/main/resources/Documentation/config.md) for the supported syntax.
<br><br>
* Prolog rule to prevent submitting changes without code owner approvals.
    * The label on which code owners must vote is configurable.
<br><br>
* The UI visualizes whose approval is needed for submit.
    * Implemented by the `need` description of the Prolog rule, which will be
      shown in the UI (the plugin doesn't contain any UI code)
<br><br>
* Auto-adding of code owners as reviewers.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
