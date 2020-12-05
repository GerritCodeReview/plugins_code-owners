load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

package(
    default_testonly = True,
    default_visibility = ["//plugins/code-owners:visibility"],
)

acceptance_tests(
    srcs = glob(
        ["*Test.java"],
        exclude = ["Abstract*.java"],
    ),
    group = "backend",
    deps = [
        ":testbases",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance/testsuite",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/common",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/config",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing/backend:testutil",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/util",
    ],
)

java_library(
    name = "testbases",
    srcs = glob(["Abstract*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance/config",
        "//java/com/google/gerrit/common:annotations",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/exceptions",
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing/backend:testutil",
    ],
)
