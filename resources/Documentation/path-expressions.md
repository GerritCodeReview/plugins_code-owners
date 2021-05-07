# Path Expressions

Path expressions are used to restrict access grants in code owner config files
to only apply to a subset of files in a directory (e.g. see
[per-file](backend-find-owners.html#perFile) rule for the
[find-owners](backend-find-owners.html) backend).

Which syntax is used depends on the used code owner backend:

* [find-owners](backend-find-owners.html) backend:
  uses [globs](#globs), but each glob is automatically prefixed with `{**/,}`
  so that subfolders are always matched (e.g. `*.md` matches all md files in all
  subfolders, rather then only md files in the current folder)
* [proto](backend-proto.html) backend:
  uses [simple path expressions](#simplePathExpressions)

## <a id="globs">Globs

Globs support the following wildcards:

* `*`: matches any string that does not include slashes
* `**`: matches any string, including slashes
* `?`: matches any character
* `[abc]`: matches one character given in the bracket
* `[a-c]`: matches one character from the range given in the bracket
* `{html,htm}`: matches either of the 2 expressions, `html` or `htm`

See [below](#examples) for examples.

## <a id="simplePathExpressions">Simple path expressions

Simple path expressions use the following wildcards:

* `*`: matches any string that does not include slashes
* `...`: matches any string, including slashes

See [below](#examples) for examples.

## <a id="examples">Examples

| To Match | Glob | find-owners | Simple Path Expression |
| -------- | ---- | ----------- | ---------------------- |
| Concrete file in current folder | `BUILD` | not possible (1) | `BUILD` |
| File type in current folder | `*.md` | not possible (1) | `*.md` |
| Concrete file in the current folder and in all subfolders | `{**/,}BUILD` | `BUILD` | needs 2 expressions: `BUILD` + `.../BUILD` |
| File type in the current folder and in all subfolders | `**.md` | `*.md` or `**.md` | `....md` |
| All files in a subfolder | `my-folder/**` | not possible (1), but you can add a `my-folder/OWNERS` file instead of using a glob | `my-folder/...` |
| All “foo-<1-digit-number>.txt” files in all subfolders | `{**/,}foo-[0-9].txt` | `foo-[0-9].txt` |not possible |
| All “foo-<n-digit-number>.txt” files in all subfolders | not possible | not possible | not possible

(1): To be compatible with the `find-owners` plugin find-owners path expressions
are prefixes with `{**/,}` which matches any folder (see
[above](path-expressions.html)). This means if path expressions like  `BUILD`,
`*.md` or `my-folder/**` are used in `OWNERS` files the effective path
expression are `{**/,}BUILD`, `{**/,}*.md` and `{**/,}my-folder/**`. These path
expression do not only match `BUILD`, `*.md` and `my-folder/**` directly in the
folder that contains the `OWNERS` file but also `BUILD`, `*.md` and
`my-folder/**` in any subfolder (e.g. `foo/bar/BUILD`, `foo/bar/baz.md` and
`foo/bar/my-folder/`).

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
