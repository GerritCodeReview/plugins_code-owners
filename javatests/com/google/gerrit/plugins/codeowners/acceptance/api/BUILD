load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

package(
    default_testonly = True,
    default_visibility = ["//plugins/code-owners:visibility"],
)

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
        exclude = ["Abstract*.java"],
    ),
    group = "acceptance_api",
    deps = [
        "testbases",
        "//plugins/code-owners:code-owners__plugin",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance/testsuite",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
    ],
)

java_library(
    name = "testbases",
    srcs = glob(["Abstract*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:framework-lib",
        "//java/com/google/gerrit/extensions:api",
        "//lib:guava",
        "//lib/guice",
        "//plugins/code-owners:code-owners__plugin",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance/testsuite",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
    ],
)
