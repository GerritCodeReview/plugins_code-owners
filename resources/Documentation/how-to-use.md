# Intro

The `@PLUGIN@` plugin provides support for
[code owners](user-guide.html#codeOwners) in Gerrit.

If the `@PLUGIN@` plugin is enabled, changes can only be submitted if all
touched files are covered by [approvals](user-guide.html#codeOwnerApproval) from
code owners.

**NOTE:** The `@PLUGIN@` is replacing the `find-owners` plugin. For projects that
used code owners with the `find-owners` plugin before, the existing `OWNERS`
files continue to work and the only major difference is that the `@PLUGIN@`
plugin comes with a new UI for selecting code owners and showing the code owner
status.

This document focuses on the workflows in the UI. Further information can be
found in the [user guide](user-guide.html).

### Enable the plugin

As a user you don’t need to do anything as the plugin is enabled by the host
administrator.

**NOTE:** As host administrator please follow the instructions in the [setup
guide](setup-guide.html).

### Bug report / Feedback

Please report bugs or send feedback using this [Monorail
template](https://bugs.chromium.org/p/gerrit/issues/entry?template=code-owners-plugin).

You can also report bugs through the bug icon in the reply dialog next to the
`HIDE OWNERS` / `SUGGEST OWNERS` button.

![suggest owners from reply dialog](./suggest-owners-from-reply-dialog.png "Suggest owners")

## Code Owners

### Why do we leverage Code Owners?

Owners are gatekeepers before a CL is submitted, they enforce standards across
the code base, help disseminate knowledge around their specific area of
ownership, ensure their is appropriate code review coverage, and provide timely
reviews. Code owners is designed as a code quality feature to ensure someone
familiar with the code base reviews any changes to the codebase or a subset of
the codebase they are the Owner of, by making sure the change is appropriate for
the system.

## What is the `code-owners` plugin?

### What is the benefit?

Code owners in Gerrit will be supported by a new `code-owners` plugin which is
developed as an open-source plugin and maintained by the Gerrit team at Google.

The `code-owners` plugin supports:

- [defining code owners](#definingCodeOwners)
- requiring owner approvals for submitting changes
- displaying owner information in the UI & suggesting code owners as reviewers
- overriding owner checks
- a [REST API](rest-api.html) to inspect code owners

### How does it work?

The plugin provides suggestions of owners for the directory or files that you
are modifying in your change based on a score. It also informs you at a glance
about the status of code owners for the change and the status of code owners per
file.

#### Score

The `code-owners` plugin suggests a maximum of 5 closest code owners based on
their score. The code owner score is calculated based on the distance of code
owners to the files.

## <a id="definingCodeOwners">Defining code owners

If you have used code owners via the `find-owners` plugin before, your code
owners are already defined in `OWNERS` files and you don’t need to do anything
since the new `code-owners` plugin just reads the existing `OWNERS` files.

If you haven’t used code owners before, you can now define code owners in
`OWNERS` files which are stored in the source tree. The code owners that are
defined in an `OWNERS` file apply to the directory that contains the `OWNERS`
file, and all its subdirectories (except if a subdirectory contains an `OWNERS`
file that disables the inheritance of code owners from the parent directories).

The syntax of `OWNERS` file is explained in the [backend
documentation](backend-find-owners.html#syntax) and examples can be found in the
[cookbook](backend-find-owners-cookbook.html).

The code-owners plugin does not support an editor to create and edit `OWNERS`
files from the UI. `OWNERS` files must be created and edited manually in the
local repository and then be pushed to the remote repository, the same way as
any other source file.

## <a id="addCodeOwnersAsReviewers">Add owners to your change

1. To add owners of the files in your change, click on `SUGGEST OWNERS` next to
   the `Code-Owners` submit requirement.

![suggest owners from change page](./suggest-owners-from-change-page.png "Suggest owners from change page")

2. The Reply dialog opens with the code owners section expanded by default with
   owners suggestions. Code owners are suggested by groups of files which share
   the same code owners.

![owner suggestions](./owner-suggestions.png "owner suggestions")

3. Hover over a file to view the list of files and their full file path.

![suggestion file groups](./suggestions-file-groups.png "suggestion file groups")

4. Click user chips to select code owners for each file or group of files. The
   selected code owner is automatically added to the reviewers section and
   automatically selected on other files the code owner owns in the change (if
   applicable).

![add or modify reviewers from suggestions](./add-owner-to-reviewer.png "add owner to reviewer")

5. Click `SEND` to notify the code owners you selected on your change.

## Reply dialog use cases

### Approved files

Once a file has received an approval vote by the code owner, the file disappears
from the file list in the reply dialog. This lets you focus on the files that
are not yet assigned to a code owner or are pending approval.

### No code owners found

There are 3 possible reasons for encountering a "Not found" text:

![no owners found](./no-owners-found.png "no owners found")

- No code owners were defined for these files.
  Reason: This could be due to missing `OWNERS` defined for these files.

- None of the code owners of these files are visible.
  Reason: The code owners accounts are not visible to you.

- None of the code owners can see the change.
  Reason: The code owners have no read permission on the target branch of the
  change and hence cannot approve the change.

- Code owners defined for these files are invalid.
  Reason: The emails cannot be resolved.

For these cases, we advise you to:

1. Ask someone with override powers (e.g. sheriff) to grant an override vote to
   unblock the change submission.
2. Contact the project owner to ask them to fix the code owner definitions, or
   permissions if needed.

### Renamed files

![renamed file from file list](./renamed-file-from-file-list.png "Renamed files")

![renamed file in code owners](./renamed-file-in-code-owners.png "Renamed files in code owners")

Renamed files (new path) will have a "Renamed" chip attached to them. A renamed
file will be considered as approved only if both old path/name and new path/name
are approved.

### Failed to fetch file

This status is informing you about a failed API call.
**Refresh the page** to recover from this error.

![failed to fetch](./failed-to-fetch-owners.png "Failed to fetch owners")

### Large change

In case of a large change containing a large number of files (hundreds or even
thousands), it will take some time to fetch all suggested code owners. In the
reply dialog, the plugin will show the overall status of the fetching and
results as soon as it has results together with the loading indicator. The
loading will disappear until all files finished fetching, failed files will be
grouped into a single group.

The fetching of suggested code owners should not block the reply itself. So you
still can select from suggestions even when not all files are finished and sent
for reviewing.

## Change page overview

In the change page, you can get an overview of the code owner statuses.

If applicable, the code owner status is displayed:

- Next to the `Code-Owners` submit requirement

![submit requirement](./submit-requirement.png "Submit requirement")

- Next to each file

![owner status](./owner-status.png "Owner status")

### Code owner status

#### `Code-Owners` submit requirement

The `Code-Owners` submit requirement is providing an overview about the code
owner status at a glance.

- Missing a reviewer that can grant the code owner approval
- Pending code owner approval
- Approved by a code owner

**Missing code owner approval**

The change is missing a reviewer that can grant the code owner approval.

![missing owner](./owner-status-missing.png "Missing code owner")

**Pending code owner approval**

- The change is pending a vote from a reviewer that can grant the code owner
  approval. Code owners have been added to the change but have not voted yet.

![pending owner's approval 1](./owner-status-pending-1.png "Pending owner's approval")

- A code owner has voted -1 on the change. A -1 doesn't block a file from being
  approved by another code owner. The status is pending because the change needs
  another round of review.

![pending owner's approval 2](./owner-status-pending-2.png "Pending owner's approval")

**Approved by code owner**

Each file in your change was approved by at least one code owner. It's not
required that all code owners approve a change.

![owner approved](./owner-status-approved.png "Code owner approved")

#### File status

Additionally, the `code-owners` plugin provides a more detailed overview of code
owner status per file in the change with 3 statuses and you can **hover over the
icon** to display a tooltip.

**Missing code owner approval**

A code owner of this file is missing as a reviewer to the change.

![missing owner tooltip](./tooltip-missing-owner.png "Tooltip for missing status")

**Pending code owner approval**

A code owner of this file has been added to the change but have not voted yet.

![pending owner tooltip](./tooltip-pending-owner.png "Tooltip for pending status")

**Approved by code owner**

A code owner of this file has approved the change. You can also see this icon if
you are a code owner of the file as in this case the file is implicitly approved
by you.

![approved owner tooltip](./tooltip-approved-owner.png "Tooltip for approved status")

**Failed to fetch status icon**

This status is informing you about a failed API call.
**Refresh the page** to recover from this error.

![failed owner tooltip](./tooltip-failed-owner.png "Tooltip for failed status")

#### No label and no status

When you own all the files in your change, the `code-owners` plugin will:

- Not show the `Code-Owners` submit requirement
- Not show the file status

### Owners-Override label

#### In the reply dialog

The `Owners-Override` label is votable by a user with certain permissions (e.g.
sheriff). The `Owner-Override` label will show in the reply dialog and you can
vote on it if you have certain permissions.

![code owner override label in reply dialog](./code-owner-override-label-in-reply.png "Vote on owners-override label")

#### In the change page

When a user with certain permissions has voted "Owners-Override+1" and the
`Code-Owners` submit requirement returns the status `Approved
(Owners-Override)`.

![code owner override label in change page](./code-owner-override-label-in-change.png "Owners-override label")

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
