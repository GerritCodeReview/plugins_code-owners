# Path Expressions

Path expressions are used to restrict access grants in code owner config files
to only apply to a subset of files in a directory (e.g. see
[per-file](backend-find-owners.html#perFile) rule for the
[find-owners](backend-find-owners.html) backend).

Which syntax is used depends on the used code owner backend:

* [find-owners](backend-find-owners.html) backend: uses [globs](#globs)
* `proto` backend: uses [simple path expressions](#simplePathExpressions)

## <a id="globs">Globs

Globs support the following wildcards:

* `*`: matches any string that does not include slashes
* `**`: matches any string, including slashes
* `?`: matches any character
* `[abc]`: matches one character given in the bracket
* `[a-c]`: matches one character from the range given in the bracket
* `{html,htm}`: matches either of the 2 expressions, `html` or `htm`

## <a id="simplePathExpressions">Simple path expressions

Simple path expressions use the following wildcards:

* `*`: matches any string that does not include slashes
* `...`: matches any string, including slashes

### Examples

| To Match | Glob | Simple Path Expression |
| -------- | ---- | ---------------------- |
| Concrete file in current folder | `BUILD` | `BUILD` |
| File type in current folder | `*.md` | `*.md` |
| Concrete file in the current folder and in all subfolders | `{**/,}BUILD` | needs 2 expressions: `BUILD` + `.../BUILD` |
| File type in the current folder and in all subfolder | `**.md` | `....md` |
| All files in a subfolder | `my-folder/**` | `my-folder/...` |
| All “foo-<1-digit-number>.txt” files in all subfolders | `{**/,}foo-[0-9].txt` | not possible |
| All “foo-<n-digit-number>.txt” files in all subfolders | not possible | not possible |

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
