package_group(
    name = "visibility",
    packages = ["//plugins/code-owners/..."],
)

package(default_visibility = [":visibility"])

load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "code-owners",
    srcs = glob(["java/com/google/gerrit/plugins/codeowners/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: code-owners",
        "Gerrit-Module: com.google.gerrit.plugins.codeowners.Module",
    ],
    resource_strip_prefix = "plugins/code-owners/resources",
    resources = glob(["resources/**/*"]),
)
