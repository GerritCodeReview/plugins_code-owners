The @PLUGIN@ plugin provides support for defining
[code owners](user-guide.html#codeOwners) for directories and files in a
repository/branch.

If the @PLUGIN@ plugin is enabled, changes can only be submitted if all
touched files are covered by [approvals](user-guide.html#codeOwnerApproval) from
code owners.

**IMPORTANT:** Before installing/enabling the plugin, or enabling the code
owners functionality for further projects, follow the instructions from the
[setup guide](setup-guide.html).

**NOTE:** This plugin is specifically developed to support code owners for the
Chrome and Android teams at Google. This means some of the functionality and
design decisons are driven by Google-specific use-cases. Nonetheless the support
for code owners is pretty generic and [configurable](config.html) so that it
should be suitable for other teams as well.

## <a id="functionality">Functionality

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
    * see [code owners documentation](config-guide.html#codeOwners) and
      [OWNERS syntax](backend-find-owners.html#syntax)
<br><br>
* Prevents submitting changes without code owner approvals:
    * Which votes count as code owner approvals is
      [configurable](setup-guide.html#configureCodeOwnerApproval).
    * Implemented as Java submit rule (no Prolog).
<br><br>
* Support for overrides:
    * Privileged users can be allowed to override the code owner submit check.
    * Overriding is done by voting on a [configured override
      label](setup-guide.html#configureCodeOwnerOverrides).
    * see [override setup](config-faqs.html#setupOverrides)
<br><br>
* UI extensions on change screen:
    * Code owner suggestion
    * Display of the code owners submit requirement
    * Display of code owner statuses in the file list
    * Change messages that list the owned paths.
    * see [UI walkthrough](how-to-use.html) and [user guide](user-guide.html)
<br><br>
* Extensible:
    * Supports multiple [backends](backends.html) which can implement different
      syntaxes for `OWNERS` files.
<br><br>
* Validation:
    * updates to `OWNERS` files are [validated](validation.html) on commit
      received and submit
    * `OWNERS` files can be validated on demand to detect consistency issues
<br><br>
* Rich REST API:
    * see [REST API documentation](rest-api.html)
<br><br>
* Highly configurable:
    * see [setup guide](setup-guide.html), [config-guide](config-guide.html),
      [config FAQs](config-faqs.html) and [config documentation](config.html)

