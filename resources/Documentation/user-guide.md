# User Guide

The `@PLUGIN@` plugin allows to define [code owners](#codeOwners) for
directories and files and requires their [approval](#codeOwnerApproval) for
changes that touch these files.

This user guide explains the functionality of the `@PLUGIN@` plugin. For a
walkthrough of the UI please refer to the [intro](how-to-use.html) page.

**NOTE:** How to setup the code owners functionality is explained in the
[setup guide](setup-guide.html).

## <a id="codeOwners">What are code owners?

A code owner is a user that is configured as owner of a path (directory or file)
and whose approval is required to modify the path or files under that path.

Who is a code owner of a path is controlled via [code owner config
files](#codeOwnerConfigFiles).

## <a id="codeOwnerConfigFiles">Code owner config files

Code owner config files are stored in the source tree of the repository and
define the [code owners](#codeOwners) for a path.

In which files code owners are defined and which syntax is used depends on the
configured [code owner backend](backends.html). Example: if the
[find-owners](backend-find-owners.html) backend is used, code owners are defined
in [OWNERS](backend-find-owners.html#syntax) files.

To create/edit code owner config files, clone the repository, edit the code
owner config files locally and then push the new commit to the remote repository
in Gerrit. This the same as creating/editing any other source files.

On push, Gerrit validates any code owner config file that is touched by the new
commits. Commits that make code owner config files invalid are rejected.

**NOTE:** There is no dedicated editor for code owner config files in the Gerrit
UI.

## <a id="codeOwnerApproval">Code owner approval

For a change to be submittable Gerrit requires that all files that are touched
in the change are approved by a code owner.

Code owners apply their approval by voting on the change. By default, voting
with `Code-Review+1` counts as code owner approval, but it's possible that the
host administrators or the project owners have [configured a different label/vote
that is required as code owner approval](setup-guide.html#configureCodeOwnerApproval).

The code owner check for a file is satisfied as soon as one of its code owners
grants the code owner approval. Negative votes from other code owners do not
block the submission (unless it's a veto vote which is configured independently
of the `@PLUGIN@` plugin).

If a code owner uploads a change/patch set, an approval of the owned files is
always implicitly assumed. This means if a code owner only touches files that
they own, no approval from other code owners is required.

For files that are renamed/moved Gerrit requires a code owner approval for the
old and the new path of the files.

If code owner approvals are missing, it is possible to submit the change with a
[code owner override](#codeOwnerOverride), but this should only be done in
exceptional cases.

**NOTE:** It is possible that users are code owners, but miss permissions to
vote on the required label. This is a configuration issue that should be
reported to the project owners (who should either
[grant the permission](setup-guide.html#grantCodeOwnerPermissions) or remove
the code owner).

**NOTE:** It's possible that the change submission is still blocked after all
necessary code owner approvals have been granted. This is the case, if futher
non-code-owner approvals are required and missing, or if further non-code-owner
submit requirements are not fulfilled yet.

## <a id="codeOwnerOverride">Code owner override

Usually some privileged users, such as sheriffs, are allowed to override the
code owner approval check to make changes submittable, even if code owner
approvals for some or all files are missing.

Overrides are intended to be used in emergency cases only. What qualifies as an
emergency depends on the project. Usually overrides are used when changes need
to be submitted quickly to address an urgent production issue and there is no
time to await all required code owner approvals.

A code owner override is applied by voting on the change. Which label/vote
counts as code owner override depends on the
[configuration](setup-guide.html#configureCodeOwnerOverrides).

**NOTE:** It's possible that overrides are disabled for a project.

## <a id="codeOwnerSuggestion">Code owner Suggestion

As a change owner, you need to request code owner approvals for the files that
are modified in the change, so that your change can become submittable. This is
done by selecting a code owner for each of the files and adding them as reviewer
to the change. To help you with this task, Gerrit suggests you suitable code
owners for the files in the change and lets you pick which of them should be
added as reviewers (how this looks in the UI is shown in the
[intro](how-to-use.html#addCodeOwnersAsReviewers) page).

When suggesting code owners for a file, Gerrit filters out code owners that are

* inactive
* not visible to you (according to
[accounts.visibility](../../../Documentation/config-gerrit.html#accounts.visibility)
setting),
* are referenced by non-visible secondary emails
* not resolvable (emails for which no Gerrit account exists)
* ambiguous (the same email is assigned to multiple accounts)
* referenced by an email with a disallowed domain (see
  [allowedEmailDomain configuration](config.html#pluginCodeOwnersAllowedEmailDomain))

The suggested code owners are sorted by score, so that the best suitable code
owners appear first. The following criteria are taken into account for computing
the score:

* The distance of the [code owner config file](#codeOwnerConfigFiles) that
  defines the code owner from the owned path.\
  The smaller the distance the better we consider the code owner as
  reviewer/approver for the path.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
