The @PLUGIN@ plugin provides support to define
[code owners](user-guide.html#codeOwners) for directories and files in a
repository/branch.

If the @PLUGIN@ plugin is enabled, changes can only be submitted if all
touched files are covered by [approvals](user-guide.html#codeOwnerApproval) from
code owners.

An overview of the supported features can be found [here](feature-set.html).

**IMPORTANT:** Using the @PLUGIN@ plugin is recommended only for repositories
that contain files / folders that are owned by different users. If this is not
the case, and all files / folders in the repository are owned by the same users
[plain Gerrit permissions should be preferred
instead](config-guide.html#configureCodeOwnersByPermissions).

**IMPORTANT:** Before installing/enabling the plugin, or enabling the code
owners functionality for further projects, follow the instructions from the
[setup guide](setup-guide.html).

