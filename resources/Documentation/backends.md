# Backends

The following code owner backends are supported:

* [find-owners](backend-find-owners.md):
  Code owner backend that supports the syntax of the
  [find-owners](https://gerrit-review.googlesource.com/admin/repos/plugins/find-owners)
  plugin.
* [proto](backend-proto.md):
  Code owner backend that supports a proto-based syntax. The proto syntax is not
  final yet and backwards incompatible changes are likely to happen. This is why
  this backend is experimental for now and should not be used in production.

Which backend is used can be
[configured](setup-guide.md#configureCodeOwnersBackend) globally, per
repository or per branch.

---

Back to [@PLUGIN@ documentation index](index.md)

Part of [Gerrit Code Review](../../../Documentation/index.md)
