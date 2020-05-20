load("@rules_java//java:defs.bzl", "java_library")

package(
    default_testonly = True,
    default_visibility = ["//plugins/code-owners:visibility"],
)

java_library(
    name = "acceptance",
    srcs = glob(["*.java"]),
    deps = [
        "//java/com/google/gerrit/acceptance:lib",
        "//plugins/code-owners:code-owners__plugin",
    ],
)

