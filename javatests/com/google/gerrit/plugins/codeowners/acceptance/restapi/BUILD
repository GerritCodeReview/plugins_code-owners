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
    group = "acceptance_restapi",
    deps = [
        "testbases",
        "//javatests/com/google/gerrit/acceptance/rest/util",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance/testsuite",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/util",
    ],
)

java_library(
    name = "testbases",
    srcs = glob(["Abstract*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:framework-lib",
        "//java/com/google/gerrit/extensions:api",
        "//lib:gson",
        "//lib:guava",
        "//lib/guice",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
    ],
)
