# find-owners Backend

The `find-owners` backend supports the syntax of the
[find-owners](https://gerrit-review.googlesource.com/admin/repos/plugins/find-owners)
plugin (with some minor extensions). It is the backend that is used by default
if no other backend is explicitly [configured](config.html#codeOwnersBackend)
for a project or branch.

## <a id="codeOwnerConfiguration">Code owner configuration

Code owners are defined in `OWNERS` files which are stored in the source tree.
The code owners that are defined in an `OWNERS` file apply to the directory that
contains the `OWNERS` file, and all its subdirectories (except if a subdirectory
contains an `OWNERS` file that disables the inheritance of code owners from the
parent directories via the [set noparent](#setNoparent) keyword).

### <a id="defaultCodeOwnerConfiguration">
Default code owners that apply to all branches can be defined in an `OWNERS`
file in the root directory of the `refs/meta/config` branch. This `OWNERS` file
is the parent of the root `OWNERS` files in all branches. This means if a root
`OWNERS` file disables the inheritance of code owners from the parent
directories via the [set noparent](#setNoparent) keyword the `OWNERS` file in
the `refs/meta/config` branch is ignored. Default code owners are not inherited
from parent projects. If code owners should be defined for child projects this
can be done by setting [global code
owners](config.html#codeOwnersGlobalCodeOwner).

### <a id="codeOwnerConfigFileExtension">
**NOTE:** It's possible that projects have a [file extension for code owner
config files](config.html#codeOwnersFileExtension) configured. In this case the
code owners are defined in `OWNERS.<file-extension>` files and `OWNERS` files
are ignored.

## <a id="cookbook">Cookbook

A cookbook with examples of `OWNERS` files for various use cases can be found
[here](backend-find-owners-cookbook.html).

## <a id="syntax">Syntax

An `OWNERS` file is a set of lines which are order-independent. Each line can be

* a [file-level rule](#fileLevelRules)
* an [access grant](#accessGrants), possibly with a
  [restriction prefix](#restrictionPrefixes)
* a [comment](#comments)

### <a id="fileLevelRules">File-level rules

File-level rules apply to the entire `OWNERS` file and should not be repeated.

#### <a id="setNoparent">set noparent

By default, a directory inherits the code owners from its parent directory. e.g.
`/path/to/OWNERS` inherits from `/path/OWNERS`. Code owners won't be inherited
from parent directories if the `set noparent` keyword is added to an `OWNERS`
file.

For example, the following `OWNERS` file ignores code owners from the parent
directories and defines some local code owners:

```
  set noparent

  jana.roe@example.com
  john.doe@example.com
```

### <a id="accessGrants">Access grants

Access grants assign code ownerships to users.

#### <a id="userEmails">user emails

In a typical `OWNERS` file, many lines are user emails. These user emails grant
code ownership to the users that own these emails in Gerrit.

For example, the following `OWNERS` file lists a few different users who are
code owners:

```
  jane.roe@example.com
  john.doe@example.com
  richard.roe@example.com
```
\
The order of the user emails is not important and doesn't have any effect, but
for consistency an alphabetical order is recommended.

User emails that are added to `OWNERS` files must be resolvable in Gerrit. This
means:

* there must be an active Gerrit account that has this email assigned,
  which is only the case if the user logged in at least once into the Gerrit
  WebUI (or if an administrator registered the user programatically)
* the email is not ambiguous (the email belongs to exactly one active Gerrit
  account)
* the email has an allowed email domain (see [allowed email domain
  configuration](config.html#pluginCodeOwnersAllowedEmailDomain)).

##### <a id="nonResolvableCodeOwnersAreIgnored">
**NOTE:** Non-resolvable code owners in submitted code owner configuration files
are ignored.

**NOTE:** In Gerrit the visibility of users is controlled via the
[accounts.visibility](../../../Documentation/config-gerrit.html#accounts.visibility)
configuration. This means not every user may be able to see every other user.
For code owners this means:

* you can only add users as code owner that are visible to you
* by adding a user as code owner you expose the existence of this user to
  everyone who can read the `OWNERS` file
* the code owner suggestion only suggests code owners that are visible to the
  calling user, this means if the `OWNERS` file contains code owners that are
  not visible to the calling user, they are omitted from the suggestion
* code owners which are referenced by secondary email cannot be resolved for
  most users, this is because secondary emails of users are only visible to
  users that have the
  [Modify Account](../../../Documentation/access-control.html#capability_modifyAccount)
  global capability assigned in Gerrit, which is normally only granted to
  administrators

**NOTE:** Via configuration it is possible to
[limit the email domains](config.html#pluginCodeOwnersAllowedEmailDomain) that
are allowed for code owners. User emails that have an email domain that is not
allowed cannot be added as code owner, and are ignored if they exist.

#### <a id="allUsers">All users

Using '*' as [user email](#userEmails) assigns the code ownership to all
registered users, which effectively exempts the directory (or the matches files
if used in combination with a [per-file](#perFile) rule) from requiring code
owner approvals on submit (this is because a code owner approval from the
uploader is always implicit if the uploader is a code owner).

#### <a id="fileKeyword">file keyword

A `file:<path-to-owners-file>` line includes rules from another `OWNERS` file.
The path may be either a relative path (e.g. `../sibling/OWNERS`) or an absolute
path (e.g. `/build/OWNERS`). The specified path must include the name of the
referenced code owner config file (e.g. `OWNERS`).

```
  file:/build/OWNERS
  jane.roe@example.com
  john.doe@example.com
```
\
Many projects prefer to use the relative form for nearby `OWNERS` files.
Absolute paths are recommended for distant paths, but also to make it easier to
copy or integrate the line between multiple `OWNERS` files.

The file that is referenced by the `file` keyword must be a code owner config
file. This means it cannot have an arbitrary name, but the file name must be
`OWNERS` or `OWNER.<file-extension>`, if a
[file extension](#codeOwnerConfigFileExtension) is configured. In addition it is
allowed that the file names have an arbitray prefix (`<prefix>_OWNERS`, e.g.
`BUILD_OWNERS`) or an arbitrary extension (`OWNERS_<extension>`, e.g.
`OWNERS_BUILD`).

It's also possible to reference code owner config files from other projects or
branches (only within the same host):

* `file:<project>:<path-to-owners-file>`:
  Loads the `<path-to-owners-file>` code owner config file from the specified
  project. The code owner config file is loaded from the same branch that also
  contains the importing `OWNERS` file (e.g. if the importing `OWNERS` file is
  in the `master` branch then `<path-to-owners-file>` will be imported from the
  `master` branch of the specified project. Example:
  `file:foo/bar/baz:/path/to/OWNERS`
* `file:<project>:<branch>:<path-to-owners-file>`:
  Loads the `<path-to-owners-file>` code owner config file from the specified
  branch of the specified project. The branch can be specified as full ref name
  (e.g. `refs/heads/master`) or as short branch name (e.g. `master`). Example:
  `file:foo/bar/baz:master:/path/to/OWNERS`

If referenced `OWNERS` files do not exists, they are silently ignored when code
owners are resolved, but trying to add references to non-existing `OWNERS` file
will be rejected on upload/submit.

When referencing an external `OWNERS` file via the `file`  keyword, only
non-restricted [access grants](#accessGrants) are imported. This means
`per-file` rules from the referenced `OWNERS` file are not pulled in and also
any [set noparent](#setNoparent) line in the referenced `OWNERS` file is
ignored, but recursive imports are being resolved.

To also import `per-file` rules and any [set noparent](#setNoparent) line use
the [include](#includeKeyword) keyword instead.

#### <a id="includeKeyword">include keyword

An `include <path-to-owners-file>` line includes rules from another `OWNERS`
file, similar to the [file](#fileKeyword) keyword. The only difference is that
using the `include` keyword also imports `per-file` rules and any
[set noparent](#setNoparent) line from the referenced `OWNERS` file.

**NOTE:** In contrast to the [file](#fileKeyword) keyword, the `include` keyword
is used without any ':' between the keyword and the file path (e.g.
`include /path/to/OWNERS`).

**NOTE:** Using the include keyword in combination with a [per-file](#perFile)
rule is not possible.

#### <a id="groups">Groups

Groups are not supported in `OWNERS` files and assigning code ownership to them
is not possible.

Instead of using a group you may define a set of users in an `OWNERS` file with
a prefix (`<prefix>_OWNERS`) or an extension (`OWNERS_<extension>`) and then
import it into other `OWNERS` files via the [file](#fileKeyword) keyword or the
[include](#includeKeyword) keyword. By using a prefix or extension for the
`OWNERS` file it is only interpreted when it is imported into another `OWNERS`
file, but otherwise it has no effect.

### <a id="restrictionPrefixes">Restriction Prefixes

All restriction prefixes should be put at the start of a line, before an
[access grant](#accessGrants).

#### <a id="perFile">per-file

A `per-file` line restricts the given access grant to only apply to a subset of
files in the directory:

```
  per-file <path-exp-1,path-exp-2,...>=<access-grant>
```
\
The access grant applies only to the files that are matched by the given path
expressions. The path expressions are [globs](path-expressions.html#globs) and
can match absolute paths or paths relative to the directory of the `OWNERS`
file, but they can only match files in the directory of the `OWNERS` file and
its subdirectories. Multiple path expressions can be specified as a
comma-separated list.

In the example below, Jana Roe, John Doe and the code owners that are inherited
from parent `OWNERS` files are code owners of all files that are contained in
the directory that contains the `OWNERS` file. In addition Richard Roe is a code
owner of the `docs.config` file in this directory and all `*.md` files in this
directory and the subdirectories.

```
  jane.roe@example.com
  john.doe@example.com
  per-file docs.config,*.md=richard.roe@example.com
```

##### <a id="doNotUsePathExpressionsForSubdirectories">
**NOTE:** It is discouraged to use path expressions that explicitly name
subdirectories such as `my-subdir/*` as they will break when the subdirectory
gets renamed/moved. Instead prefer to define these code owners in
`my-subdir/OWNERS` so that the code owners for the subdirectory stay intact when
the subdirectory gets renamed/moved.

To grant per-file code ownership to more than one user multiple [user
emails](#userEmails) can be specified as comma-separated list, or an external
`OWNERS` file can be referenced via the [file](#fileKeyword) keyword.
Alternatively the `per-file` line can be repeated multiple times.

```
  jane.roe@example.com
  john.doe@example.com
  per-file docs.config,*.md=richard.roe@example.com,janie.doe@example.com
  per-file docs.config,*.md=file:/build/OWNERS
```
\
When referencing an external `OWNERS` file via the [file](#fileKeyword) keyword,
only non-restricted [access grants](#accessGrants) are imported. This means
`per-file` rules from the referenced `OWNERS` file are not pulled in and also
any [set noparent](#setNoparent) line in the referenced `OWNERS` file is
ignored, but recursive imports are being resolved.

Using the [include](#includeKeyword) keyword in combination with a `per-file`
rule is not possible.

It's also possible to combine [set noparent](#setNoparent) with `per-file` rules
to prevent access by non-per-file owners from the current directory as well as
from parent directories.

In the example below, Richard Roe is the only code owner of the `docs.config`
file in this directory and all `*.md` files in this directory and the
subdirectories. All other files in this directory and its subdirectories are
owned by Jana Roe, John Doe and the code owners that are inherited from parent
directories.

```
  jane.roe@example.com
  john.doe@example.com
  per-file docs.config,*.md=set noparent
  per-file docs.config,*.md=richard.roe@example.com
```

### <a id="anotations">Annotations

Lines representing [access grants](#accessGrants) can be annotated. Annotations
have the format `#{ANNOTATION_NAME}` and can appear at the end of the line.
E.g.:

```
  john.doe@example.com #{LAST_RESORT_SUGGESTION}
  per-file docs.config,*.md=richard.roe@example.com #{LAST_RESORT_SUGGESTION}
```
\
Annotations can be mixed with [comments](#comments) that can appear before and
after annotations, E.g.:

```
  jane.roe@example.com # foo bar #{LAST_RESORT_SUGGESTION} baz
```
\
The following annotations are supported:

#### <a id="lastResortSuggestion">
* `LAST_RESORT_SUGGESTION`:
  Code owners with this annotation are omitted when [suggesting code
  owners](rest-api.html#list-code-owners-for-path-in-change), except if dropping
  these code owners would make the suggestion result empty. If code ownership is
  assigned to the same code owner through multiple relevant access grants in the
  same code owner config file or in other relevant code owner config files the
  code owner gets omitted from the suggestion if it has the
  `LAST_RESORT_SUGGESTION` set on any of the access grants.

Unknown annotations are silently ignored.

**NOTE:** If an access grant line that assigns code ownership to multiple users
has an annotation, this annotation applies to all these users. E.g. if an
annotation is set for the all users wildcard (aka `*`) it applies to all users.

### <a id="comments">Comments

The '#' character indicates the beginning of a comment. Arbitrary text may be
added in comments.

Comments are only supported in 2 places:

* comment lines:
  A line starting with '#' (`# <comment-text>`).
* comments after [user emails](#userEmails) (`<user-email> # <comment-text>`).

Comments are not interpreted by the `code-owners` plugin and are intended for
human readers of the `OWNERS` files. However some projects/teams may have own
tooling that uses comments to store additional information in `OWNERS` files.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
