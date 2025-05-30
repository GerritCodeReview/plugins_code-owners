# Setup Guide

This guide explains how to setup and configure the `code-owners` plugin.

**IMPORTANT:** Do this setup **before** installing/enabling the plugin, or
enabling the code owners functionality for further projects. This is important
because as soon as the code owners functionality is switched on, all changes
will immediately require code owner approvals for the submission of changes and
you want everything been setup properly so that code owner approvals can be
granted and change submission is not blocked.

The following configuration steps are recommended:

1. [Configure the code owners backend that should be used](#configureCodeOwnersBackend)
2. [Opt-out projects/repositories that should not use code owners](#optOutProjects)
3. [Opt-out branches that should not use code owners](#optOutBranches)
4. [Configure the label vote that should count as code owner approval](#configureCodeOwnerApproval)
5. [Grant code owners permission to vote on the label that counts as code owner approval](#grantCodeOwnerPermissions)
6. [Configure code owner overrides & fallback code owners](#configureCodeOwnerOverridesAndFallbackCodeOwners)
7. [Configure allowed email domains](#configureAllowedEmailDomains)
8. [Optional Configuration](#optionalConfiguration)
9. [Stop using the find-owners Prolog submit rule](#stopUsingFindOwners)
10. [Add an initial code owner configuration at root level](#configureCodeOwners)

Recommendations about further configuration parameters can be found in the
[config guide](config-guide.html).

Please also heck out the [config FAQs](config-faqs.html).

### <a id="configureCodeOwnersBackend">1. Configure the code owners backend that should be used

The `code-owners` plugin supports multiple [code owner backends](backends.html)
and it must be configured which one should be used. The backend controls which
files represent code owner configs and which syntax they use. The backends may
also differ in the feature-set that they support.

By default, the [find-owners](backend-find-owners.html) backend is used, which
reads code owner configs from `OWNERS` files and uses the same
[syntax](backend-find-owners.html#syntax) that was previously supported by the
deprecated `find-owners` plugin.

To configure a different backend globally, set
[plugin.code-owners.backend](config.html#pluginCodeOwnersBackend) in
`gerrit.config` (requires to be a host admin).

Example global configuration in `gerrit.config` that configures the
[proto](backend-proto.html) backend:

```
  [plugin "code-owners"]
    backend = proto
```
\
To configure a backend on project-level
[codeOwners.backend](config.html#codeOwnersBackend) in the
[code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch can be set (requires to be a project owner).

Example per-project configuration in `code-owners.config` that configures the
[proto](backend-proto.html) backend:

```
  [codeOwners]
    backend = proto
```
\
It's also possible to configure backends per branch by setting
[codeOwners.\<branch\>.backend](config.html#codeOwnersBranchBackend) in the
[code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch (requires to be a project owner).

Example per-branch configuration in `code-owners.config` that configures the
[proto](backend-proto.html) backend:

```
  [codeOwners "refs/heads/experimental"]
    backend = proto
```
\
**IMPORTANT:** The `proto` backend is experimental only and should not be used
in production.

### <a id="optOutProjects">2. Opt-out projects/repositories that should not use code owners

You can skip this step if the code owners functionality should be enabled for
all projects/repositories on the host.

##### Variant 1: Enable the code owners functionality only for some projects

If the code owners functionality should only be enabled for some
projects/repositories, it's best to:

###### a) Disable the code owner functionality globally

To disable the code owners functionality globally, set
[plugin.code-owners.disabled](config.html#pluginCodeOwnersDisabled) in
`gerrit.config` to `true` (requires to be a host admin).

`gerrit.config`:
```
  [plugin "code-owners"]
    disabled = true
```
\
Alternatively you may set [codeOwners.disabled](config.html#codeOwnersDisabled)
in the [code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch of the `All-Projects` project to `true` (requires to
be a host admin).

`code-owners.config`:
```
  [codeOwners]
    disabled = true
```

###### b) Enable the code owners functionality for the projects/repositories that should use code owners

Enable the code owners functionality on project-level by setting
[codeOwners.disabled](config.html#codeOwnersDisabled) in the
[code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch to `false` (requires to be a project owner).

`code-owners.config`:
```
  [codeOwners]
    disabled = false
```

##### Variant 2: Enable the code owners functionality for all projects/repositories, except some projects/repositories

If the code owners functionality should only be disabled for some
projects/repositories it's best to:

###### a) Enable the code owners functionality globally

This is the default.

If needed, unset
[plugin.code-owners.disabled](config.html#pluginCodeOwnersDisabled) in
`gerrit.config` and [codeOwners.disabled](config.html#codeOwnersDisabled) in the
[code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch of the `All-Projects` project.

###### b) Disable the code owners functionality in the projects/repositories that should not use code owners

Disable the code owners functionality on project-level by setting
[codeOwners.disabled](config.html#codeOwnersDisabled) in the
[code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch to `true` (requires to be a project owner).

`code-owners.config`:
```
  [codeOwners]
    disabled = true
```

### <a id="optOutBranches">3. Opt-out branches that should not use code owners

If the code owners functionality is enabled for a project/repository, it is
enabled for all branches of the project/repository, unless branches opted-out.

You can skip this section, if the code owners functionality should be enabled
for all branches (which is the default configuration).

To opt-out branches from using code owners set
[codeOwners.disabledBranch](config.html#codeOwnersDisabledBranch) in the
[code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch to a regular expression that matches the branches that
should be opted-out (requires to be a project owner).

`code-owners.config`:
```
  [codeOwners]
    disabledBranch = refs/meta/config
    disabledBranch = ^refs/heads/stable-.*
```

**TIP:** Consider disabling the code owners functionality for `refs/meta/config`
branches, so that changes to the project configuration do not require code owner
approvals.

### <a id="configureCodeOwnerApproval">4. Configure the label vote that should count as code owner approval

By default `Code-Review+1` votes from [code owners](user-guide.html#codeOwners)
count as [code owner approval](user-guide.html#codeOwnerApproval). If this is
what you want, you can skip this step.

Otherwise you can configure the required code owner approval globally by setting
[plugin.code-owners.requiredApproval](config.html#pluginCodeOwnersRequiredApproval)
in `gerrit.config` (requires to be a host admin) or per project by setting
[codeOwners.requiredApproval](config.html#codeOwnersRequiredApproval) in the
[code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch (requires to be a project owner).

Example global configuration in `gerrit.config` that requires `Code-Review+2` as
code owner approval:

```
  [plugin "code-owners"]
    requiredApproval = Code-Review+2
```
\
Example per-project configuration in `code-owners.config` that requires
`Code-Review+2` as code owner approval:

```
  [codeOwners]
    requiredApproval = Code-Review+2
```
\
**IMPORTANT:** The specified label must exist for the project. Make sure that
the project configuration contains or inherits the definition of the specified
label. How to configure a label in `project.config` is described
[here](../../../Documentation/config-labels.html#label_custom). You may also use
the Gerrit REST API to
[create new label definitions](../../../Documentation/rest-api-projects.html#create-label).

**IMPORTANT:** Code owners need to be granted permissions to vote on the
specified label so that they can grant the code owner approval on changes (see
[next step](#grantCodeOwnerPermissions)).

**NOTE:** To submit a change, code owner approvals are required **in addition**
to labels that are otherwise required for submission. If you want to rely solely
on code owner approvals, you may need to reconfigure existing label definitions
(e.g. change the `Code-Review` label definition to not require a `Code-Review+2`
vote for change submission).

**NOTE:** Whether code owner approvals are sticky across patch sets depends on
the definition of the required label. If the label definition has [copy
rules](../../../Documentation/config-labels.html#label_copyAnyScore) enabled so
that votes are sticky across patch sets, then also the code owner approvals
which are based on these votes will be sticky.

### <a id="grantCodeOwnerPermissions">5. Grant code owners permission to vote on the label that counts as code owner approval

Code owners must be granted permissions to vote on the label that counts as code
owner approval (see [previous step](#configureCodeOwnerApproval)) in order to be
able grant the code owner approval on changes so that they can be submitted.

As for any other permission, the
[permission to vote on labels](../../../Documentation/access-control.html#category_review_labels)
needs to be granted via the access screen of the project or a parent project (at
`https://<host>/admin/repos/<project-name>,access`).

### <a id="configureCodeOwnerOverridesAndFallbackCodeOwners">6. Configure code owner overrides & fallback code owners

It's possible that some files have no code owners defined (e.g. missing root
code owner config file). In this case changes for these files cannot be code
owner approved and hence cannot be submitted.

To avoid that this leads to unsubmittable changes, it is recommended to
configure code owner overrides and/or fallback code owners.

#### <a id="configureCodeOwnerOverrides">Configure code owner overrides

It's possible to configure [code owner
overrides](user-guide.html#codeOwnerOverride) that allow privileged users to
override code owner approvals. This means they can approve changes without being
a code owner.

Configuring code owner overrides is optional, but recommended.

To enable code owner overrides, you must define which label vote is required for
an override. This can be done globally by setting
[plugin.code-owners.overrideApproval](config.html#pluginCodeOwnersOverrideApproval)
in `gerrit.config` (requires to be a host admin) or per project by setting
[codeOwners.overrideApproval](config.html#codeOwnersOverrideApproval) in the
[code-owners.config](config-faqs.html#updateCodeOwnersConfig) file in the
`refs/meta/config` branch (requires to be a project owner).

Example global configuration in `gerrit.config` that requires
`Owners-Override+1` for a code owner override:

```
  [plugin "code-owners"]
    overrideApproval = Owners-Override+1
```
\
Example per-project configuration in `code-owners.config` that requires
`Owners-Override+1` for a code owner override:

```
  [codeOwners]
    overrideApproval = Owners-Override+1
```
\
**IMPORTANT:** Same as for the label that is required as code owner approval
(see [above](#configureCodeOwnerApproval)), the specified label must exist for
the project and permissions to vote on it must be granted separately.

Example for the definition of the `Owners-Override` label in `project.config`:
```
  [label "Owners-Override"]
    function = NoBlock
    value = 0 No score
    value = +1 Override
    defaultValue = 0
```

#### <a id="configureFallbackCodeOwners">Configure fallback code owners

It is possible to configure a policy for [fallback code
owners](config.html#pluginCodeOwnersFallbackCodeOwners) that controls who should
own files for which no code owners have been defined, e.g. all users or no one
(default).

Configuring fallback code owners is optional. For the initial rollout of the
code-owners plugin it is highly recommended to allow fallback code owners so
that projects that do not have any code owner config files yet are not
disrupted.

### <a id="configureAllowedEmailDomains">7. Configure allowed email domains

By default, the emails in code owner config files that make users code owners
can have any email domain. It is strongly recommended to limit the allowed email
domains to trusted email providers (e.g. email providers that guarantee that an
email is never reassigned to a different user, since otherwise the user to which
the email is reassigned automatically takes over the code ownerships that are
assigned to this email, which is a security issue).

To limit the allowed email domains, set
[plugin.code-owners.allowedEmailDomain](config.html#pluginCodeOwnersAllowedEmailDomain)
in `gerrit.config` (requires to be a host admin).

Example `gerrit.config` configuration with restricted email domains:
```
  [plugin "code-owners"]
    allowedEmailDomain = google.com
    allowedEmailDomain = chromium.org
```

**NOTE:** Allowed email domains cannot be configured on project level.

### <a id="optionalConfiguration">8. Optional Configuration

Other optional configuration parameters are described in the [config
documentation](config.html).

Examples (not an exhaustive list):

* [Global code owners](config.html#pluginCodeOwnersGlobalCodeOwner)
* Whether [an implicit code owner approval from the change owner is
  assumed](config.html#codeOwnersEnableImplicitApprovals) (only for patch sets
  that are uploaded by the change owner)
* [Merge commit strategy](config.html#codeOwnersMergeCommitStrategy) that
  decides which files of merge commits require code owner approvals
* [File extension](config.html#codeOwnersFileExtension) that should be used for
  code owner config files.
* Whether [pure reverts should be exempted from requiring code owner
  approvals](config.html#pluginCodeOwnersExemptPureReverts).
* [Users that are exempted from requiring code owner
  approvals](config.html#pluginCodeOwnersExemptedUser)
* [configure](config.html#pluginCodeOwnersPathExpressions) a different syntax
  for [path expressions](path-expressions.html) in code owner config files (e.g.
  use plain globs)

### <a id="stopUsingFindOwners">9.Stop using the find-owners Prolog submit rule

This section can be skipped if you haven't used the `find-owners` plugin so far.

The `find-owners` plugin comes with a Prolog submit rule that prevents the
submission of changes that have insufficient code owner approvals. With the
`code-owners` plugin this is now being checked by a submit rule that is
implemented in Java. Hence the Prolog submit rule from the `find-owners` plugin
is no longer needed and you should stop using it before you start using the
`code-owners` plugin.

**NOTE:** It's possible to use the submit rules from the `code-owners` plugin
and `find-owners` plugin at the same time, but then certains things like [code
owner overrides](user-guide.html#codeOwnerOverride) and
[exemptions](user-guide.html#codeOwnerExemptions) are not working (as they are
not supported by the `find-owners` plugin).

### <a id="configureCodeOwners">10. Add an initial code owner configuration at root level

By enabling the code owners functionality, a code owner approval from code
owners will be required for submitting changes.

If code owners are not defined yet, changes can only be submitted

* with a code owner override (if override labels have been configured, see
  [above](#configureCodeOwnerOverrides))
* with an approval from a fallback code owner (if fallback code owners have been
  configured, see [above](#configureFallbackCodeOwners)).

Right after the code owners functionality got enabled for a project/branch, it
is recommended to add an initial code owner configuration at the root level that
defines the code owners for the project/branch explicitly.

**NOTE:** Submitting the initial code owner configuration requires an override
or an approval from a fallback code owner (see above).

**NOTE:** It's recommended to add the initial code owner configuration only
after enabling the code owners functionality so that the code owner
configuration is [validated](validation.html) on upload, which prevents
submitting an invalid code owner config that may block the submission of all
changes (e.g. if it is not parseable).

**NOTE** If the repository contains pre-existing code owner config files, it is
recommended to validate them via the [Check code owners files REST
endpoint](rest-api.html#check-code-owner-config-files) and fix the reported
issues.

**NOTE:** If neither code owner overrides nor fallback code owners are
configured an initial code owner configuration must be added before enabling the
code owners functionality as otherwise changes can become unsubmittable (they
require code-owner approvals, but noone can provide nor override them).

**NOTE:** Instead of defining root code owners in all branches, you may also
define default code owners in the `refs/meta/config` branch, that then apply to
all branches (also see [config guide](config-guide.html#codeOwners)).

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
