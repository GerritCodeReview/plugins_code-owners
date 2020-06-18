load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")
acceptance_tests(
    srcs = glob(["*Test.java"]),
    group = "codeowners",
    deps = [
        "//plugins/code-owners:code-owners__plugin",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
    ],
)

