The @PLUGIN@ plugin provides support to define
[code owners](user-guide.html#codeOwners) for directories and files in a
repository/branch.

If the @PLUGIN@ plugin is enabled, changes can only be submitted if all
touched files are covered by [approvals](user-guide.html#codeOwnerApproval) from
code owners.

An overview of the supported features can be found [here](feature-set.html).

**IMPORTANT:** Before installing/enabling the plugin, or enabling the code
owners functionality for further projects, follow the instructions from the
[setup guide](setup-guide.html).

**NOTE:** This plugin is specifically developed to support code owners for the
Chrome and Android teams at Google. This means some of the functionality and
design decisons are driven by Google-specific use-cases. Nonetheless the support
for code owners is pretty generic and [configurable](config.html) so that it
should be suitable for other teams as well.

