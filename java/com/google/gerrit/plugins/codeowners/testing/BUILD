load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//plugins/code-owners:visibility"],
)

java_library(
    name = "testing",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/entities",
        "//java/com/google/gerrit/extensions:api",
        "//java/com/google/gerrit/truth",
        "//lib:guava",
        "//lib:jgit",
        "//lib/truth",
        "//lib/truth:truth-java8-extension",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/common",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/util",
    ],
)
