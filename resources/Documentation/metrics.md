# Metrics

The @PLUGIN@ plugin exports several metrics which can give insights into the
usage and performance of the code owners functionality.

## <a id="latencyMetrics"> Latency Metrics

* `compute_changed_files`:
  Latency for computing changed files.
* `compute_file_statuses`:
  Latency for computing file statuses.
* `compute_owned_paths`:
  Latency for computing the files in a change that are owned by a user.
* `prepare_file_status_computation`:
  Latency for preparing the file status computation.
* `prepare_file_status_computation_for_account`:
  Latency for preparing the file status computation for an account.
* `resolve_code_owner_config`:
  Latency for resolving a code owner config file.
* `resolve_code_owner_config_import`:
  Latency for resolving an import of a code owner config file.
* `resolve_code_owner_config_imports`:
  Latency for resolving all imports of a code owner config file.
* `run_code_owner_submit_rule`:
  Latency for running the code owner submit rule.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
