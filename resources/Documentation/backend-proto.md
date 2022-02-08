# proto Backend

The `proto` backend supports a proto-based syntax.

**NOTE:** Since the proto syntax is not final yet and backwards incompatible
changes are likely to happen the `proto` backend is only for **experimental**
use and should not be used in production yet.

## <a id="codeOwnerConfiguration">Code owner configuration

Code owners are defined in `OWNERS_METADATA` files which are stored in the
source tree. The code owners that are defined in an `OWNERS_METADATA` file apply
to the directory that contains the `OWNERS_METADATA` file, and all its
subdirectories (except if a subdirectory contains an `OWNERS_METADATA` file that
disables the inheritance of code owners from the parent directories).

**NOTE:** It's possible that projects have a [file extension for code owner
config files](config.md#codeOwnersFileExtension) configured. In this case the
code owners are defined in `OWNERS_METADATA.<file-extension>` files and
`OWNERS_METADATA` files are ignored.

## <a id="syntax">Syntax

The proto syntax is not final yet and backwards incompatible changes are likely
to happen.

**TODO:** Document the proto syntax.

---

Back to [@PLUGIN@ documentation index](index.md)

Part of [Gerrit Code Review](../../../Documentation/index.md)
