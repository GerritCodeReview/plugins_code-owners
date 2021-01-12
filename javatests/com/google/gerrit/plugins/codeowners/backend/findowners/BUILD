load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*Test.java"]),
    group = "findowners_backend",
    deps = [
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing/backend:testutil",
        "//plugins/code-owners/javatests/com/google/gerrit/plugins/codeowners/backend:testbases",
    ],
)
