# Config Guide

The `@PLUGIN@` plugin has many configuration parameters that can be used to
customize its behavior. These configuration parameters are described in the
[config](config.html) documentation. This guide gives some additional
recommendations for the configuration, but doesn't cover all configuration
parameters.

Please also check out the [config FAQs](config-faqs.html).

## <a id="requiredConfiguration">Required Configuration

**Before** installing/enabling the plugin, or enabling the code owners
functionality for further projects, it is important to do some basic
configuration. This includes choosing a [code owner backend](backends.html),
defining the approvals that count as code owner approval and as code owner
override, opting-out projects or branches and configuring the allowed email
domain. All this configuration is covered in detail by the [setup
guide](setup-guide.html).

## <a id="workflow">Workflow Configuration

Some of the configuration parameters have an effect on the user workflow.

### <a id="stickyApprovals">Make code owner approvals / overrides sticky

Code owner approvals and code owner overrides can be made sticky by enabling
[copy rules](../../../Documentation/config-labels.html#label_copyAnyScore) in
the definitions of the labels that are configured as [required
approval](config.html#pluginCodeOwnersRequiredApproval) and [override
approval](config.html#pluginCodeOwnersOverrideApproval).

### <a id="implicitApprovals">Implicit code owner approvals

It's possible to [enable implicit approvals](config.html#pluginCodeOwnersEnableImplicitApprovals)
of code owners on their own changes. If enabled, changes of code owners are
automatically code owner approved, but only if the last patch set was uploaded
by the change owner (change owner == last patch set uploader). This implict code
owner approval covers all files that are owned by the change owner. This means
if a code owner uploads a change that only touches files that they own, no
approval from other code owners is required for submitting the change.

If implicit approvals are enabled, paths can be exempted from requiring code
owner approvals by assigning the code ownership to [all
users](backend-find-owners.html#allUsers), as then any modification to the path
is always implicitly approved by the change owner.

**NOTE:** If implicit approvals are disabled, users can still self-approve their
own changes by voting on the required label.

**IMPORTANT**: Enabling implicit approvals is considered unsafe, see [security
pitfalls](#securityImplicitApprovals) below.

### <a id="mergeCommits">Required code owner approvals on merge commits

For merge commits the list of modified files depends on the base against which
the merge commit is compared:

1. comparison against the destination branch (aka first parent commit):
   All files which differ between the merge commit and the destination branch.
   This includes all files which have been modified in the source branch since
   the last merge into the destination branch has been done.

2. comparison against the Auto-Merge (Auto-Merge = result of automatically
   merging the source branch into the destination branch):
   Only shows files for which a conflict resolution has been done.

Which files a users sees on the change screen depends on their base selection.

For the `@PLUGIN@` plugin it can be configured [which files of a merge commit
require code owner approvals](config.html#pluginCodeOwnersMergeCommitStrategy),
all files that differ with the destination branch (case 1) or only files that
differ with the Auto-Merge (case 2). If case 1 is configured, all file diffs
that have been approved in one branch must be re-approved when they are merged
into another branch. If case 2 is configured, only conflict resolutions have to
be approved when a merge is done.

**IMPORTANT**: Requiring code owner approvals only for files that differ with
the Auto-Merge (case 2) is considered unsafe, see [security
pitfalls](#securityMergeCommits) below.

## <a id="codeOwners">Recommendations for defining code owners

Code owners can be defined on different levels, which differ by scope. This
section gives an overview of the different levels and explains when they should
be used.

1. Folder and file code owners:
   These are the code owners that are defined in the [code owner config
   files](user-guide.html#codeOwnerConfigFiles) that are stored in the source
   tree of the repository. They can either apply to a whole
   [folder](backend-find-owners.html#userEmails) (folder code owners) or to
   [matched files](backend-find-owners.html#perFile) (file code owners).\
   This is the normal way to define code owners. This code owner definition is
   discoverable since it is stored in human-readable code owner config file in
   the source tree of the repository.\
   Folder and file code owners can differ from branch to branch since they are
   defined in the source tree.\
   Folder and file code owners are usually users that are expert for a code area
   and that should review and approve all changes to this code.
2. Root code owners:
   Root code owners are folder code owners (see 1.) that are defined in the code
   owner config file that is stored in the root directory of a branch.\
   Usually root code owners are the most experienced developers that can approve
   changes to all the code base if needed, but that should only review and
   approve changes if no other, more specific, code owner is available.\
   Root code owners can differ from branch to branch.
3. Default code owners:
   [Default code owners](backend-find-owners.html#defaultCodeOwnerConfiguration)
   are stored in the code owner config file (e.g. the `OWNERS` file) in the
   `refs/meta/config` branch and apply for all branches (unless inheritance is
   ignored).\
   The same as root code owners, default code owners are experienced developers
   that can approve changes to all the code base if needed.\
   However in contrast to root code owners, default code owners apply to all
   branches (including newly created branches), and hence can be used if code
   owners should be kept consistent across all branches.\
   A small disadvantage is that this code owner definition is not very well
   discoverable since it is stored in the `refs/meta/config` branch, but default
   code owners are suggested to users the same way as other code owners.
4. Global code owners:
   [Global code owners](config.html#pluginCodeOwnersGlobalCodeOwner) are defined
   in the plugin configuration and apply to all projects or all child projects.\
   They are intended to configure bots as code owners that need to operate on
   all or multiple projects. Alternatively bots may be configured as exempted
   users (see further below).\
   Global code owners still apply if parent code owners are ignored.
5. Fallback code owners:
   [Fallback code owners](config.html#pluginCodeOwnersFallbackCodeOwners) is a
   policy configuration that controls who should own paths that have no code
   owners defined.\
   Fallback code owners are not included in the code owner suggestion.\
   Configuring all users as fallback code owners may allow bypassing the code
   owners check (see [security pitfalls](#securityFallbackCodeOwners) below).
6. Exempted users:
   [Exempted users](config.html#pluginCodeOwnersExemptedUser) are exempted from
   requiring code owner approvals.\
   If a user is exempted from requiring code owner approvals changes that are
   uploaded by this user are automatically code-owner approved.\
   Exempted users are intended to be used for bots that need to create changes
   on all or multiple projects that should not require code owner approvals.

In addition users can be allowed to [override the code owner submit
check](user-guide.html#codeOwnerOverride). This permission is normally granted
to users that that need to react to emergencies and need to submit changes
quickly (e.g sheriffs) or users that need to make large-scale changes across
many repositories.

## <a id="externalValidationOfCodeOwnerConfigs">External validation of code owner config files

By default, when code owner config files are modified they are
[validated](validation.html) on push. If any issues in the modified code owner
config files are found, the push is rejected. This is important since
non-parsable code owner config files make submissions fail which likely blocks
the development teams, and hence needs to be prevented.

However rejecting pushes in case of invalid code owner config files is not an
ideal workflow for everyone. Instead it may be wanted that the push always
succeeds and that issues with modified code owner config files are then detected
and reported by a CI bot. The CI bot would then post its findings as checks on
the open change which prevent the change submission. To enable this the
validation of code owner config files on push can be
[disabled](config.html#pluginCodeOwnersEnableValidationOnCommitReceived), but
then the host admins should setup a bot to do the validation of modified code
owner config files externally. For this the bot could use the [Check Code Owner
Config Files In Revision](rest-api.html#check-code-owner-config-files-in-revision)
REST endpoint.

## <a id="differentCodeOwnerConfigurations">Use different code owner configurations in a fork

If a respository is forked and code owners are used in the original repository,
the code owner configuration of the original repository shouldn't apply for the
fork (the fork should have different code owners, and if the fork is stored on
another Gerrit host it's also likely that the original code owners cannot be
resolved on that host). In this case it is possible to [configure a file
extension](config.html#pluginCodeOwnersFileExtension) for code owner config
files in the fork so that its code owner config files do not clash with the
original code owner config files.

## <a id="securityPitfalls">Security pitfalls

While requiring code owner approvals is primarily considered as a code quality
feature and not a security feature, many admins / projects owners are concerned
about possibilities to bypass code owner approvals. These admins / projects
owners should be aware that some configuration settings may make it possible to
bypass code owner approvals, and hence using them is not recommended.

### <a id="securityImplicitApprovals">Implicit approvals

If [implicit approvals](#implicitApprovals) are enabled, it is important that
code owners are aware of their implicit approval when they upload new changes
for other users.

Example:

* If a contributor pushes a change to a wrong branch and a code owner helps them
  to get it rebased onto the correct branch, the rebased change has implicit
  approvals from the code owner, since the code owner is the change owner.

To avoid situations like this it is recommended to not enable implicit
approvals.

**NOTE:** Why are implicit approvals not always applied for the change owner?\
If implicit approvals would be always applied for the change owner, and not
only when the change owner is also the last uploader, anyone could upload a new
patch set to a change that is owned by a code owner and get it implicitly
approved by the change owner. This would be really bad, as it means that anyone
could submit arbitrary code without a code owner having actually looked at it
before the submission.

**NOTE:** Why are implicit approvals not always applied for the last uploader?\
If implicit approvals would be always applied for the last uploader, and not
only when the last uploader is also the change owner, changes would get
implicitly approved whenever a code owner touches a change of somebody else
(e.g. when editing the commit message, since editing the commit message creates
a new patch set which has the user editing the commit message as an uploader).
This would be bad, because code owners are not aware that editing the commit
message of a change would implictly code-owner approve it.

### <a id="securityMergeCommits">Required code owner approvals on merge commits

If any branch doesn't require code owner approvals or if the code owners in any
branch are not trusted, it is not safe to [configure for merge commits that they
only require code owner approvals for files that differ with the
Auto-Merge](#mergeCommits). E.g. if there is a branch that doesn't require code
owner approvals, with this setting the code owners check can be bypassed by:

1. setting the branch that doesn't require code owner approvals to the same
   commit as the main branch that does require code owner approvals
2. making a change in the branch that doesn't require code owner approvals
3. merging this change back into the main branch that does require code owner
   approvals
4. since it's a clean merge, all files are merged automatically and no code
   owner approval is required

### <a id="securityFallbackCodeOwners">Setting all users as fallback code owners

As soon as the code owners functionality is enabled for a project / branch, all
files in it require code owner approvals. This means if any path doesn't have
any code owners defined, submitting changes to the path is only possible with

1. a code owner override
2. an approval from a fallback code owners (only if enabled)

[Configuring all users as fallback code
owners](config.html#pluginCodeOwnersFallbackCodeOwners) is problematic, as it
can happen easily that code owner config files are misconfigured so that some
paths are accidentally not covered by code owners. In this case, the affected
paths would suddenly be open to all users, which may not be wanted. This is why
configuring all users as fallback code owners is not recommended.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
