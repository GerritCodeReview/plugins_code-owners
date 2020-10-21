load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

package(
    default_testonly = True,
    default_visibility = ["//plugins/code-owners:visibility"],
)

acceptance_tests(
    srcs = glob(
        ["*IT.java"],
    ),
    group = "acceptance_batch",
    deps = [
        "//plugins/code-owners:code-owners__plugin",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
    ],
)
