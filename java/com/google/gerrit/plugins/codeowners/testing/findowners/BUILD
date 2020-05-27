load("@rules_java//java:defs.bzl", "java_library")

package(default_testonly = True)

java_library(
    name = "testutil",
    srcs = glob(["*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java/com/google/gerrit/server",
        "//lib:jgit",
        "//lib:jgit-junit",
        "//lib/guice",
        "//plugins/code-owners:code-owners__plugin",
    ],
)
