# Build

This plugin can be built with Bazel in the Gerrit tree.

Clone or link this plugin to the plugins directory of Gerrit's
source tree.

From the Gerrit source tree issue the command:

```
  bazel build plugins/@PLUGIN@
```
\
The output is created in

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```
\
To execute the tests run:

```
  bazel test //plugins/@PLUGIN@/...
```
\
To measure the test coverage run:

```
  bazel coverage --test_output=all plugins/code-owners/... --coverage_report_generator=@bazel_tools//tools/test:coverage_report_generator --combined_report=lcov --instrumentation_filter="^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance/testsuite[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api/impl[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/common[/:],,^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/metrics[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/util[/:],^//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/validation[/:]" && genhtml -o . --ignore-errors source bazel-out/_coverage/_coverage_report.dat
```
\
and then open the generated `index.md` in a browser.

---

Back to [@PLUGIN@ documentation index](index.md)

Part of [Gerrit Code Review](../../../Documentation/index.md)
