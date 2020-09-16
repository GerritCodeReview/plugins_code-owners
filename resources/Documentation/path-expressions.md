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
* `{html,htm}`: matches either of the 2 expressions, “html” or “htm”

## <a id="simplePathExpressions">Simple path expressions

Simple path expressions use the following wildcards:

* `*`: matches any string that does not include slashes
* `...`: matches any string, including slashes

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
