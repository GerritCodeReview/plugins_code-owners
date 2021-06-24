# Backends

The following code owner backends are supported:

* [find-owners](backend-find-owners.html):
  Code owner backend that supports the syntax of the
  [find-owners](https://gerrit-review.googlesource.com/admin/repos/plugins/find-owners)
  plugin.
* [proto](backend-proto.html):
  Code owner backend that supports a proto-based syntax. The proto syntax is not
  final yet and backwards incompatible changes are likely to happen. This is why
  this backend is experimental for now and should not be used in production.

Which backend is used can be
[configured](setup-guide.html#configureCodeOwnersBackend) globally, per
repository or per branch.

## <a id="codeOwnerConfigFiles">Code owner config files

Code owner config files are stored in the source tree of the repository and
define the [code owners](user-guide.html#codeOwners) for a path.

The code owners that are defined in a code owner config file apply to the
directory that contains the code owner config file, and all its subdirectories
(except if a subdirectory contains a code owner config file that disables the
inheritance of code owners from the parent directories).

In which files code owners are defined depends on the configured code owner
backend:

| Backend       | Primary code owner config files |
| ------------- | ------------------------------- |
| `find-owners` | `OWNERS`                        |
| `proto`       | `OWNERS_METADATA`               |

In addition, there can be secondary code owner config files, which may be
imported into other code owner config files. These files have no effect on their
own, but only when they are directly or indirectly imported into a primary code
owner config file.

| Backend       | Secondary code owner config files                         |
| ------------- | --------------------------------------------------------- |
| `find-owners` | `<prefix>_OWNERS`, `OWNERS_<extension>`                   |
| `proto`       | `<prefix>_OWNERS_METADATA`, `OWNERS_METADATA_<extension>` |

Primary and secondary code owner config files are [validated](validation.html)
by the `@PLUGIN@` plugin when they are changed to ensure that they are always
parsable and valid.

By configuring a [file extension](config.html#codeOwnersFileExtension) for code
owner config files it is possible to use **a different set of code owner config
files**:

| Backend       | Primary code owner config files | Secondary code owner config files |
| ------------- | ------------------------------- | --------------------------------- |
| `find-owners` | `OWNERS.<configured-file-extension>` | `<prefix>_OWNERS.<configured-file-extension>`, `OWNERS_<extension>.<configured-file-extension>` |
| `proto`       | `OWNERS_METADATA.<configured-file-extension>` | `<prefix>_OWNERS_METADATA.<configured-file-extension>`, `OWNERS_METADATA_<extension>.<configured-file-extension>` |

In this case only the primary and secondary code owner config files with the
configured file extension are considered as code owner config files. This means
code owner config files without file extension or with other file extensions are
not interpreted and also not validated.

Configuring a file extension for code owner config files is useful if a
repository is forked and the fork should use a different set of code owner
config files than the upstream repository. For this use case it is important
that the code owner config files from the upstream repository are not validated,
as they may use a different syntax or reference non-resolvable accounts, and
hence would always be detected as invalid.

As some projects want to allow arbitrary file extensions for code owner config
files, it is possible to enable arbitrary file extensions for code owner config
files by [configuration](config.html#codeOwnersEnableCodeOwnerConfigFilesWithFileExtensions).
If arbitrary file extensions are enabled, the following files are consideres as
secondary code owner config files **in addition** to the once described above:

| Backend       | Additional secondary code owner config files              |
| ------------- | --------------------------------------------------------- |
| `find-owners` | `OWNERS.<arbitrary-file-extension>`                       |
| `proto`       | `OWNERS_METADATA.<arbitrary-file-extension>`              |

With this setup code owner config files with any file extension are validated.

**NOTE:** Enabling arbitrary file extensions for code owner config files
conflicts with configuring a certain file extension for code owner config files
in order to ignore upstream code owner config files, as in this case the
upstream code owner config files should not be validated. This is why arbitrary
file extensions should not be enabled if a certain file extension for code owner
config files was already configured.

**NOTE:** Code owner config files can only import other code owner config files,
but not arbitrary files. This ensures that code owner config files only import
files that have gone through validation and hence are known to be valid. If
arbitrary files could be imported, the imported files may not be parsable since
they were not validated. As a result of this, looking up code owners could
break, which would block submission of all changes for which the invalid file is
relevant. This would likely be considered as a serious outage, hence importing
arbitrary files is disallow so that this cannot happen.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
