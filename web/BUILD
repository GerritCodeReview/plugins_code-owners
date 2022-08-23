load("//tools/bzl:js.bzl", "gerrit_js_bundle")

package_group(
    name = "visibility",
    packages = ["//plugins/code-owners/..."],
)

package(default_visibility = [":visibility"])

gerrit_js_bundle(
    name = "code-owners",
    srcs = glob(["*.js"]),
    entry_point = "plugin.js",
)
