load("//javatests/com/google/gerrit/acceptance:tests.bzl", "acceptance_tests")

acceptance_tests(
    srcs = glob(["*Test.java"]),
    group = "proto_backend",
    deps = [
        "//lib:protobuf",
        "//plugins/code-owners:code-owners__plugin",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/acceptance",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/testing",
        "//plugins/code-owners/javatests/com/google/gerrit/plugins/codeowners/backend:testbases",
    ],
)

