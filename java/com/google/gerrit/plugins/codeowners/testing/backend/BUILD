load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//plugins/code-owners:visibility"],
)

java_library(
    name = "testutil",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/server",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/guice",
        "//lib/guice:guice-assistedinject",
        "//plugins/code-owners:code-owners__plugin",
    ],
)
