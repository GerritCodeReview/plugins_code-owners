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
    srcs = glob(["java/com/google/gerrit/plugins/codeowners/module/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: code-owners",
        "Gerrit-Module: com.google.gerrit.plugins.codeowners.module.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.codeowners.module.HttpModule",
        "Gerrit-BatchModule: com.google.gerrit.plugins.codeowners.module.BatchModule",
    ],
    resource_jars = ["//plugins/code-owners/ui:code-owners"],
    resource_strip_prefix = "plugins/code-owners/resources",
    resources = glob(["resources/**/*"]),
    deps = [
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/api/impl",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/backend",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/common",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/restapi",
        "//plugins/code-owners/java/com/google/gerrit/plugins/codeowners/validation",
    ],
)
