## Intro

The Code-Owners plugin is currently in development. We are testing code-owners on some hosts on googlesource.com right now. If you build your Gerrit from master, you can enable it by enabling the code-owners plugin and adding OWNERS info to your code base.

During dogfooding, the plugin’s UI will be behind an experiment flag, you will need to append `?experiment=UiFeature__plugin_code_owners` to enable it since it is disabled by default.

The Code-Owner plugin is an open-source plugin and maintained by the Gerrit team at Google to replace find-owners plugin.

### Bug report / Feedback

Report a bug or send feedback using this [Monorail template](https://bugs.chromium.org/p/gerrit/issues/entry?template=code-owners-plugin). You can also report a bug through the bug icon in the reply dialog next to the Suggest Owners button.

![suggest owners from reply dialog](./suggest-owners-from-reply-dialog.png "Suggest owners")

## Code Owners

### Who are code owners?

A code owner is a user whose approval is required to modify files under a certain path. Who is a code owner of a path is controlled via "OWNERS'' files that are checked into the repository. For submitting a change Gerrit requires that all files that were touched in the change are approved by a code owner. Code owners usually apply their approval by voting with "Code-Review+1" on the change. Their approval is to confirm that “This change is appropriate for our system and belongs in this directory."

### Why do we leverage Code Owners?

Owners are gatekeepers before a CL is submitted, they enforce standards across the code base, help disseminate knowledge around their specific area of ownership, ensure their is appropriate code review coverage, and provide timely reviews. Code owners is designed as a code quality feature to ensure someone familiar with the code base reviews any changes to the codebase or a subset of the codebase they are the Owner of, by making sure the change is appropriate for the system.

## What is the code-owners plugin?

### What is the benefit?

Code owners in Gerrit will be supported by a new code-owners plugin which is developed as an open-source plugin and maintained by the Gerrit team at Google.
The code-owners plugin supports:

- defining code owners
- requiring owner approvals for submitting changes
- displaying owner information in the UI & suggesting code owners as reviewers
- overriding owner checks

### How does it work?

The plugin provides suggestions of owners for the directory or files that you are modifying in your change based on a score. It also informs you at a glance about the status of code-owners for the change and the status of code-owners per file.

#### Score

The Code-owners plugin suggests a maximum of 5 closest owners based on their score. The owner score is calculated based on the distance of owners to the files.

## Add owners to your change

1. To add owners of the files in your change, click on Suggest owners next to the Code-Owners submit requirement.

![suggest owners from change page](./suggest-owners-from-change-page.png "Suggest owners from change page")

2. The Reply dialog opens with the Code-Owners section expanded by default with owners suggestions. Code-Owners are suggested by groups of files which share the same file-owners.

![owner suggestions](./owner-suggestions.png "owner suggestions")

3. Hover over a file to view the list of files and their full file path.

![suggestion file groups](./suggestions-file-groups.png "suggestion file groups")

4. Click user chips to select owners for each file or group of files. The selected owner is automatically added to the Reviewers section and automatically selected on other files the code-owner owns in the change (if applicable).

![add or modify reviewers from suggestions](./add-owner-to-reviewer.png "add owner to reviewer")

5. Click Send to notify the owners you selected on your change.

## Reply dialog use cases

### Approved files

Once a file has received a +1 vote by the owner, the file disappears from the file list in the reply dialog. This lets you focus on the files that are not yet assigned to an owner or are pending approval.

### No code owners found

There are 3 possible reasons for encountering a “Not found” text:

![no owners found](./no-owners-found.png "no owners found")

- No owners were defined for these files.
  Reason: This could be due to missing OWNERS defined for these files.

- None of the code owners of these files are visible.
  Reason: The owners accounts are not visible to you.

- Owners defined for these files are invalid.
  Reason: The emails cannot be resolved.

For these 3 cases, we advise you to:

1. Ask someone with override powers (e.g. sheriff) to grant an override vote to unblock the change submission.
2. Contact the project owner to ask them to fix the code owner definitions.

### Renamed files

![renamed file from file list](./renamed-file-from-file-list.png "Renamed files")

![renamed file in code owners](./renamed-file-in-code-owners.png "Renamed files in code owners")

Renamed files (new path) will have a “Renamed” chip attached to them. A renamed file will be considered as approved only if both old path/name and new path/name are approved.

### Failed to fetch file

This status is informing you about a failed API call.
**Refresh the page** to recover from this error.

![failed to fetch](./failed-to-fetch-owners.png "Failed to fetch owners")

### Large change

In case of a large change containing a large number of files (hundreds or even thousands), it will take some time to fetch all suggested owners.
In the reply dialog, the plugin will show the overall status of the fetching and results as soon as it has results together with the loading indicator. The loading will disappear until all files finished fetching, failed files will be grouped into a single group.

The fetching of suggested owners should not block the reply itself. So you still can select from suggestions even when not all files are finished and sent for reviewing.

## Change page overview

In the change page, you can get an overview of the Code-Owners statuses.

If applicable, the Code-Owner status is displayed:

- Next to the Code-Owners submit requirement

![submit requirement](./submit-requirement.png "Submit requirement")

- Next to each file

![owner status](./owner-status.png "Owner status")

### Code-owner status

#### Code-owner label

The Code-Owner label is providing an overview about the owners status at a glance.

- Missing a reviewer that can grant the code-owner approval
- Pending code-owner approval
- Approved by code-owner

**Missing code owner approval**

The change is missing a reviewer that can grant the code-owner approval.

![missing owner](./owner-status-missing.png "Missing owner")

**Pending code-owner approval**

- The change is pending a vote from a reviewer that can grant the code-owner approval.  Owners have been added to the change but have not voted yet.

![pending owner's approval 1](./owner-status-pending-1.png "Pending owner's approval")

- A code owner has voted -1 on the change. A -1 doesn't block a file from being approved by another code owner. The status is pending because the change needs another round of review.

![pending owner's approval 2](./owner-status-pending-2.png "Pending owner's approval")

**Approved by code-owner**

Each file in your change was approved by at least one code owner. It's not required that all code owners approve a change.

![owner approved](./owner-status-approved.png "Owner approved")

#### File status

Additionally, the code-owners plugin provides a more detailed overview of code-owner status per file in the change with 3 statuses and you can **hover over the icon** to display a tooltip.

**Missing code owner approval**

A code owner of this file is missing as a reviewer to the change.

![missing owner tooltip](./tooltip-missing-owner.png "Tooltip for missing status")

**Pending code owner approval**

A code owner of this file has been added to the change but have not voted yet.

![pending owner tooltip](./tooltip-pending-owner.png "Tooltip for pending status")

**Approved by code owner**

A code owner of this file has approved the change. You can also see this icon if you are a code-owner of the file as in this case the file is implicitly approved by you. 

![approved owner tooltip](./tooltip-approved-owner.png "Tooltip for approved status")

**Failed to fetch status icon**

This status is informing you about a failed API call.
**Refresh the page** to recover from this error.

![failed owner tooltip](./tooltip-failed-owner.png "Tooltip for failed status")

#### No label and no status

When you own all the files in your change, the Code-Owners plugin will:

- Not show the Code-Owner submit requirement
- Not show the file status

### Owners-Override label

#### In the reply dialog

The Owners-Override label is votable by a user with certain permissions (e.r.sheriff).
The owner-override label will show in the reply dialog and you can vote on it if you have certain permissions.

![code owner override label in reply dialog](./code-owner-override-label-in-reply.png "Vote on owners-override label")


#### In the change page

When a user with certain permissions has voted "Owners-Override+1" and the Code-Owners submit requirement returns the status `Approved (Owners-Override)`.

![code owner override label in change page](./code-owner-override-label-in-change.png "Owners-override label")


