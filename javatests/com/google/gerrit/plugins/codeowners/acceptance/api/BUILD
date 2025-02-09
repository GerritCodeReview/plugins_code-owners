load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

package(
    default_testonly = True,
    default_visibility = ["//plugins/code-owners:visibility"],
)

[acceptance_tests(
    srcs = [f],
    group = f[:f.index(".")],
    vm_args = ["-Xmx512m"],
    deps = [
        "testbases",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance/testsuite",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api/impl",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/common",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/util",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/validation",
    ],
) for f in glob(
    ["*IT.java"],
    exclude = ["Abstract*.java"],
)]

java_library(
    name = "testbases",
    srcs = glob(["Abstract*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:framework-lib",
        "//java/com/google/gerrit/common:server",
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/server",
        "//lib:guava",
        "//lib/guice",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance/testsuite",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
    ],
)
