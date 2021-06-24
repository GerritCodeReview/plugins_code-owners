# Feature Set

The @PLUGIN@ plugin supports the following features:

* Support for defining code owners:
    * Code owners can be specified in `OWNERS` files that can appear in any
      directory in the source branch.
    * Default code owners can be specified on repository level by an `OWNERS`
      file in the `refs/meta/config` branch.
    * Global code owners across repositories can be configured.
    * A fallback code owners policy controls who owns files that are not covered
      by `OWNERS` files.
    * Code owners can be specified by email (groups are not supported).
    * Inheritance from parent directories is supported and can be disabled.
    * Including an `OWNERS` file from other directories / branches / projects is
      possible (only on the same host).
    * File globs can be used.
    * see [code owners documentation](config-guide.md#codeOwners) and
      [OWNERS syntax](backend-find-owners.md#syntax)
<br><br>
* Prevents submitting changes without code owner approvals:
    * Which votes count as code owner approvals is
      [configurable](setup-guide.md#configureCodeOwnerApproval).
    * Implemented as Java submit rule (no Prolog).
    * Configuring [exemptions](user-guide.md#codeOwnerExemptions) is possible.
<br><br>
* Support for overrides:
    * Privileged users can be allowed to override the code owner submit check.
    * Overriding is done by voting on a [configured override
      label](setup-guide.md#configureCodeOwnerOverrides).
    * see [override setup](config-faqs.md#setupOverrides)
<br><br>
* UI extensions on change screen:
    * [Code owner suggestion](how-to-use.md#howDoesItWork)
    * [Display of the code owners submit requirement](how-to-use.md#codeOwnersSubmitRequirement)
    * [Display of code owner statuses in the file list](how-to-use.md#perFilCodeOwnerStatuses)
    * Change messages that list the owned paths.
    * see [UI walkthrough](how-to-use.md) and [user guide](user-guide.html)
<br><br>
* Extensible:
    * Supports multiple [backends](backends.md) which can implement different
      syntaxes for `OWNERS` files.
<br><br>
* Validation:
    * updates to `OWNERS` files are [validated](validation.md) on commit
      received and submit
    * `OWNERS` files in a [project](rest-api.md#check-code-owner-config-files)
      or [revision](rest-api.md#check-code-owner-config-files-in-revision) can
      be validated on demand to detect consistency issues
<br><br>
* Rich REST API:
    * see [REST API documentation](rest-api.md)
<br><br>
* Highly configurable:
    * see [setup guide](setup-guide.md), [config-guide](config-guide.html),
      [config FAQs](config-faqs.md) and [config documentation](config.md)

---

Back to [@PLUGIN@ documentation index](index.md)

Part of [Gerrit Code Review](../../../Documentation/index.md)
