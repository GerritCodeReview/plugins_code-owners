# find-owners: Cookbook for OWNERS files

For the [find-owners](backend-find-owners.html) backend code owners are defined
in `OWNERS` files. This cookbook provides examples of `OWNERS` files for various
use cases.

**NOTE:** The syntax of `OWNERS` files is described
[here](backend-find-owners.html#syntax).

### <a id="defineUsersAsCodeOwners">Define users as code owners

To define a set of users as code owners, each user email must be placed in a
separate line:

```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
```
\
The order of the user emails is not important and doesn't have any effect, but
for consistency an alphabetical order is recommended.

The listed users own all files that are contained in the directory that contains
this `OWNERS` file, except:

* files in subdirectories that have an `OWNERS` file that disables inheriting
  code owners from parent directories via the
  [set noparent](backend-find-owners.html#setNoparent) file-level rule
  ([example](#ignoreParentCodeOwners))
* files that are matched by a path expression in a
  [per-file](backend-find-owners.html#perFile) line that uses
  [set noparent](backend-find-owners.html#setNoparent) (in this `OWNERS` file or
  in any `OWNERS` file in a subdirectory, [example](#perFileWithSetNoparent))

In addition to the specified owners the files are also owned by the code owners
that are inherited from the parent directories. To prevent this the
[set noparent](backend-find-owners.html#setNoparent) file-level rule can be used
(see [next example](#ignoreParentCodeOwners)).

### <a id="ignoreParentCodeOwners">Ignore parent code owners

To ignore code owners that are defined in the `OWNERS` files of the parent
directories the [set noparent](backend-find-owners.html#setNoparent) file-level
rule can be used:

```
  set noparent
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
```
\
For example, if code owners for the file '/foo/bar/baz.txt' are computed the
`OWNERS` files are evaluated in this order:

1. `/foo/bar/OWNERS`
2. `/foo/OWNERS`
3. `/OWNERS`
4. `/OWNERS` in `refs/meta/config`
   (contains [default code owners](backend-find-owners.html#defaultCodeOwnerConfiguration))

If any `set noparent` file-level rule is seen the evaluation is stopped and
further `OWNERS` files are ignored. E.g. if `/foo/OWNERS` contains
`set noparent` the `OWNERS` files mentioned at 3. and 4. are ignored.

**NOTE:** When the [set noparent](backend-find-owners.html#setNoparent)
file-level rule is used you should always define code owners which should be
used instead of the code owners from the parent directories. Otherwise the files
in the directory stay [without code owners](#noCodeOwners) and nobody can grant
code owner approval on them. To [exempt a directory from requiring code owner
approvals](#exemptFiles), assign the code ownership to [all
users](backend-find-owners.html#allUsers) instead ([example](#exemptFiles)).

**NOTE:** The usage of `set noparent` has no effect on `OWNERS` files in
subfolders.

### <a id="defineCodeOwnersForAFile">Define code owners for a certain file

By using the [per-file](backend-find-owners.html#perFile) restriction prefix it
is possible to define code owners for a certain file (in the current directory
and all its subdirectories), e.g. for the `BUILD` file:

```
  per-file BUILD=tina.toe@example.com
```
\
Multiple code owners for the same file can be specified as comma-separated list:

```
  per-file BUILD=tina.toe@example.com,martha.moe@example.com
```
\
Alternatively it's also possible to repeat the per-file line multiple times:

```
  per-file BUILD=tina.toe@example.com
  per-file BUILD=martha.moe@example.com
```
\
If a file is matched by the path expressions of multiple
[per-file](backend-find-owners.html#perFile) lines, the file is owned by all
users that are mentioned in these [per-file](backend-find-owners.html#perFile)
lines.

If folder code owners are present, the file is also owned by any folder code
owners:

```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
  per-file BUILD=tina.toe@example.com,martha.moe@example.com
```
\
In addition, the file is also owned by code owners that are inherited from
parent directories.

#### <a id="perFileWithSetNoparent">
Ignoring folder code owners and inherited parent code owners for a file is
possible by using a matching [per-file](backend-find-owners.html#perFile) line
with [set noparent](backend-find-owners.html#setNoparent).

```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
  per-file BUILD=set noparent
  per-file BUILD=tina.toe@example.com,martha.moe@example.com
```
\
For example, if code owners for the file '/foo/bar/baz.txt' are computed the
code owners in the `OWNERS` files are evaluated in this order:

1. matching per-file code owners in `/foo/bar/OWNERS`
2. folder code owners in `/foo/bar/OWNERS`
3. matching per-file code owners in `/foo/OWNERS`
4. folder code owners in `/foo/OWNERS`
5. matching per-file code owners in `/OWNERS`
6. folder code owners in `/OWNERS`
7. matching per-file code owners in `/OWNERS` in `refs/meta/config`
   (contains [default code owners](backend-find-owners.html#defaultCodeOwnerConfiguration))
8. folder code owners in `/OWNERS` in `refs/meta/config`
   (contains [default code owners](backend-find-owners.html#defaultCodeOwnerConfiguration))

If any `set noparent` file-level rule is seen the evaluation is stopped and
code owners on further levels are ignored. E.g. if `/foo/OWNERS` contains a
matching per-file rule with `set noparent` the code owners mentioned at 4. to 8.
are ignored.

**NOTE:** When the [set noparent](backend-find-owners.html#setNoparent) rule is
used on a per-file rule you should always define code owners which should be
used instead of the folder code owners and the code owners from the parent
directories.  Otherwise the matched files stay [without code
owners](#noCodeOwners) and nobody can grant code owner approval on them. To
[exempt matched files from requiring code owner approvals](#exemptFiles), assign
the code ownership to [all users](backend-find-owners.html#allUsers) instead
([example](#exemptFiles)).

**NOTE:** The syntax for path expressions / globs is explained
[here](path-expressions.html#globs).

### <a id="defineCodeOwnersForAFileType">Define code owners for a certain file type

This is the same as [defining code owners for a file](#defineCodeOwnersForAFile)
only that the [per-file](backend-find-owners.html#perFile) line must have a path
expression that matches all files of the wanted type, e.g. all '*.md' files:

```
  per-file *.md=tina.toe@example.com
```
\
This matches all '*.md' in the current directory and all its subdirectories.

**NOTE**: Using '*.md' is the same as using '**.md' (both expressions match
files in the current directory and in all subdirectories).

**NOTE:** The syntax for path expressions / globs is explained
[here](path-expressions.html#globs).

### <a id="defineCodeOwnersForAllFileInASubdirectory">Define code owners for all files in a subdirectory

It is discouraged to use path expressions that explicitly name subdirectories
such as `my-subdir/*` as they will break when the subdirectory gets
renamed/moved. Instead prefer to define these code owners in `my-subdir/OWNERS`
so that the code owners for the subdirectory stay intact when the subdirectory
gets renamed/moved.

Nontheless, it's possible to define code owners for all files in a subdirectory
using a [per-file](backend-find-owners.html#perFile) line. This is the same as
[defining code owners for a file](#defineCodeOwnersForAFile) only that the path
expression matches all files in the subdirectory:

```
  per-file my-subdir/*=tina.toe@example.com
```

### <a id="defineAGroupAsCodeOwner">Define a group as code owner

Groups are not supported in `OWNERS` files and assigning code ownership to them
is not possible.

Instead of using a group you may define a set of users in an `OWNERS` file with
a prefix (`<prefix>_OWNERS`) or an extension (`OWNERS_<extension>`) and then
[import](#importOtherOwnersFile) it into other `OWNERS` files via the
[file](backend-find-owners.html#fileKeyword) keyword.

`/OWNERS_BUILD`:
```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
  per-file BUILD=tina.toe@example.com
```
\
other `OWNERS` file:
```
  per-file BUILD=file:/OWNERS_BUILD
```
\
This is equivalent to having:

```
  per-file BUILD=jane.roe@example.com,john.doe@example.com,richard.roe@example.com
```
\
**NOTE:** The `per-file` line from `/OWNERS_BUILD` is not imported, since the
[file](backend-find-owners.html#fileKeyword) keyword only imports folder code
owners. Using the [include](backend-find-owners.html#includeKeyword) keyword,
that would also consider per-file code owners, is not supported for `per-file`
lines.

### <a id="importOtherOwnersFile">Import code owners from other OWNERS file

To import code owners from another `OWNERS` file the
[file](backend-find-owners.html#fileKeyword) or
[include](backend-find-owners.html#includeKeyword) keyword can be used:

`java/com/example/foo/OWNERS`:
```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
  per-file BUILD=tina.toe@example.com
```
\
`javatests/com/example/foo/OWNERS`:
```
  file:/java/com/example/foo/OWNERS
```
\
This is equivalent to having:

```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
```
\
**NOTE:** The `per-file` line from `java/com/example/foo/OWNERS` is not
imported, since the [file](backend-find-owners.html#fileKeyword) keyword only
imports folder code owners. If also `per-line` lines should be imported the
[include](backend-find-owners.html#includeKeyword) keyword can be used instead:

`javatests/com/example/foo/OWNERS`:
```
  include /java/com/example/foo/OWNERS
```
\
This is equivalent to having:

```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
  per-file BUILD=tina.toe@example.com
```

### <a id="importFromOtherRepository">Import code owners from other repository

To import code owners from an `OWNERS` file in another repository the
[file](backend-find-owners.html#fileKeyword) or
[include](backend-find-owners.html#includeKeyword) keyword can be used:

`/OWNERS` in the `master` branch of respository `my-project`:
```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
```
\
`/OWNERS` in the `master` branch of respository `my-project/plugin-foo`:
```
  file:my-project:/OWNERS
```
\
This way `my-project/plugin-foo` always has the same code owners as
`my-project`.

**NOTE:** If a branch is not specified, the `OWNERS` file will always be
imported from the same branch, in which the importing `OWNERS` file is stored.
If `file:my-project:/OWNERS` is contained in an `OWNERS` file in the `master`
branch, `my-project:master:/OWNERS` is imported. If `file:my-project:/OWNERS` is
contained in an `OWNERS` file in the `stable-3.5` branch,
`my-project:stable-3.5:/OWNERS` is imported.

### <a id="importFromOtherBranch">Import code owners from other branch

To import code owners from an `OWNERS` file in another branch the
[file](backend-find-owners.html#fileKeyword) or
[include](backend-find-owners.html#includeKeyword) keyword can be used:

`/OWNERS` in the `master` branch of respository `my-project`:
```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
```
\
`/OWNERS` in the `stable-3.5` branch of respository `my-project`:
```
  file:my-project:master:/OWNERS
```
\
This way the `stable-3.5` branch of `my-project` always has the same global code
owners as the `master` branch of `my-project`.

**NOTE:** To import code owners from another branch it is mandatory to specify
the repository, even if it is the same repository that contains the importing
`OWNERS` file.

### <a id="exemptFiles">Exempt files from requiring code owner approval

To exempt files from requiring code owner approval the code ownership can be
assigned to [all users](backend-find-owners.html#allUsers) by using '*' as
[user email](backend-find-owners.html#userEmails). Assigning the code ownership
to all users effectively exempts the directory (or the matches files if used in
combination with a [per-file](backend-find-owners.html#perFile) rule) from
requiring code owner approvals on submit. This is because a code owner approval
from the uploader is always implicit if the uploader is a code owner.

`OWNERS` file that exempts the whole directory from requiring code owner
approvals:
```
  *
```
\
`OWNERS` files that exempts all '*.md' files (in the current directory and all
subdirectories) from requiring code owner approvals:
```
  per-file *.md=*
```
\
**NOTE:** Files that are not owned by anyone are **not** excluded from requiring
code ower approvals, see [next section](#noCodeOwners).

### <a id="noCodeOwners">Avoid that files are not owned by anyone

Files that are not owned by anyone cannot be approved since there is no code
owner that can grant the approval. Hence submitting modifications to them is
only possible with an override approval.

Due to this it is recommended to avoid code owner configurations that leave
files without code owners, e.g. the following configurations should be avoided:

* an `OWNERS` file with only `set noparent` (ignores code owners from parent
  directories, but doesn't define code owners that should be used instead)
* a `per-file` line with only `set noparent` (ignores folder code owners and
  code owners from parent directories, but doesn't define code owners that
  should be used instead)
* no `OWNERS` file at root level

**NOTE:** How to exempt files from requiring code owner approval is described
[here](#exemptFiles).

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
