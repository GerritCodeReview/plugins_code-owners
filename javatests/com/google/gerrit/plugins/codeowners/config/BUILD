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
    group = "config",
    deps = [
        ":testbases",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api/impl",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/common",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/config",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/util",
    ],
)

java_library(
    name = "testbases",
    srcs = glob(["Abstract*.java"]),
    deps = [
        "//java/com/google/gerrit/server",
        "//java/com/google/gerrit/testing:gerrit-test-util",
        "//lib:guava",
        "//lib:jgit",
        "//lib/truth",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/config",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
    ],
)
