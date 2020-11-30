# Config Guide

The `@PLUGIN@` plugin has many configuration parameters that can be used to
customize its behavior. These configuration parameters are described in the
[config](config.html) documentation. This guide gives some additional
recommendations for the configuration, but doesn't cover all configuration
parameters.

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

### <a id="implicitApprovals">Implicit code owner approvals

It's possible to [enable implicit approvals](config.html#pluginCodeOwnersEnableImplicitApprovals)
of code owners on their own changes. If enabled and the uploader of a patch set
is a code owner, an approval of the uploader is assumed for all owned files.
This means if a code owner uploads a change / patch set that only touches files
that they own, no approval from other code owners is required for submitting the
change.

If implicit approvals are enabled, paths can be exempted from requiring code
owner approvals by assigning the code ownership to [all
users](backend-find-owners.html#allUsers), as then any modification to the path
is always implicitly approved by the uploader.

**NOTE:** If implicit approvals are disabled, users can still self-approve their
own changes by voting on the required label.

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

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
