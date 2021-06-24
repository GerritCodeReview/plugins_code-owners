# Path Expressions

Path expressions are used to restrict access grants in code owner config files
to only apply to a subset of files in a directory (e.g. see
[per-file](backend-find-owners.md#perFile) rule for the
[find-owners](backend-find-owners.md) backend).

Which syntax is used depends on the used code owner backend:

* [find-owners](backend-find-owners.md) backend:
  uses [globs](#globs), but each glob is automatically prefixed with `{**/,}`
  so that subfolders are always matched, e.g. `*.md` matches all md files in all
  subfolders, rather then only md files in the current folder (also see the
  [caveat](#findOwnersCaveat) section below)
* [proto](backend-proto.md) backend:
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
| Concrete file in current folder | `BUILD` | [not possible](#findOwnersCaveatMatchingAFile) | `BUILD` |
| File type in current folder | `*.md` | [not possible](#findOwnersCaveatMatchingFilesByType) | `*.md` |
| Concrete file in the current folder and in all subfolders | `{**/,}BUILD` | `BUILD` | needs 2 expressions: `BUILD` + `.../BUILD` |
| File type in the current folder and in all subfolders | `**.md` | `*.md` or `**.md` | `....md` |
| All files in a subfolder | `my-folder/**` | [not possible](#findOwnersCaveatMatchingFilesInSubfolder), but you can add a `my-folder/OWNERS` file instead of using a glob | `my-folder/...` |
| All “foo-<1-digit-number>.txt” files in all subfolders | `{**/,}foo-[0-9].txt` | `foo-[0-9].txt` |not possible |
| All “foo-<n-digit-number>.txt” files in all subfolders | not possible | not possible | not possible

## <a id="findOwnersCaveat">Caveat with find-owners path expressions

To be compatible with the `find-owners` plugin find-owners path expressions
are prefixes with `{**/,}` which matches any folder (see
[above](path-expressions.md)). This means if path expressions like  `BUILD`,
`*.md` or `my-folder/**` are used in `OWNERS` files the effective path
expression are `{**/,}BUILD`, `{**/,}*.md` and `{**/,}my-folder/**`. These path
expression do not only match `BUILD`, `*.md` and `my-folder/**` directly in the
folder that contains the `OWNERS` file but also `BUILD`, `*.md` and
`my-folder/**` in any subfolder (e.g. `foo/bar/BUILD`, `foo/bar/baz.md` and
`foo/bar/my-folder/`).

### Examples

#### <a id="findOwnersCaveatMatchingAFile">Matching a file

If you have the following `/foo/OWNERS` file:

```
  per-file BUILD=john.doe@example.com
```
\
John Doe owns the `/foo/BUILD` file, but also all `BUILD` files in
subfolders of `/foo/`, e.g. `/foo/bar/baz/BUILD`.

#### <a id="findOwnersCaveatMatchingFilesByType">Matching files by type

If you have the following `/foo/OWNERS` file:

```
  per-file *.md=john.doe@example.com
```
\
John Doe owns all `*.md` files in `/foo/`, but also all `*.md` files in
subfolders of `/foo/`, e.g. `/foo/bar/baz.md`.

#### <a id="findOwnersCaveatMatchingFilesInSubfolder">Matching files in subfolder

If you have the following `/foo/OWNERS` file:

```
  per-file my-folder/*=john.doe@example.com
```
\
John Doe owns all files in the `/foo/my-folder/` folder, but also all files in
any `my-folder/` subfolder, e.g. all files in `/foo/bar/baz/my-folder/`.

---

Back to [@PLUGIN@ documentation index](index.md)

Part of [Gerrit Code Review](../../../Documentation/index.md)
