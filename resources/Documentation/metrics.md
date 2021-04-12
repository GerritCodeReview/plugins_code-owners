# Metrics

The @PLUGIN@ plugin exports several metrics which can give insights into the
usage and performance of the code owners functionality.

## <a id="latencyMetrics"> Latency Metrics

* `add_change_message_on_add_reviewer`:
  Latency for adding a change message with the owned path when a code owner is
  added as a reviewer.
* `compute_changed_files`:
  Latency for computing changed files.
* `compute_file_status`:
  Latency for computing the file status for one file.
* `compute_file_statuses`:
  Latency for computing file statuses for all files in a change.
* `compute_owned_paths`:
  Latency for computing file statuses.
* `compute_owned_paths`:
  Latency for computing the files in a change that are owned by a user.
* `compute_patch_set_approvals`:
  Latency for computing the approvals of the current patch set.
* `extend_change_message_on_post_review`:
  Latency for extending the change message with the owned path when a code owner
  approval is applied.
* `get_auto_merge`:
  Latency for getting the auto merge commit of a merge commit.
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

## <a id="codeOwnerConfigMetrics"> Code Owner Config Metrics

* `code_owner_config_cache_reads_per_change`:
  Number of code owner config cache reads per change.
* `code_owner_config_backend_reads_per_change`:
  Number of code owner config backend reads per change.
* `load_code_owner_config`:
  Latency for loading a code owner config file (read + parse).
* `parse_code_owner_config`:
  Latency for parsing a code owner config file.
* `read_code_owner_config`:
  Latency for reading a code owner config file.

## <a id="counterMetrics"> Counter Metrics

* `count_code_owner_config_reads`:
  Total number of code owner config reads from backend.
* `count_code_owner_config_cache_reads`:
  Total number of code owner config reads from cache.
* `count_code_owner_submit_rule_errors`:
  Total number of code owner submit rule errors.
    * `cause`:
      The cause of the submit rule error.
* `count_code_owner_submit_rule_runs`:
  Total number of code owner submit rule runs.
* `count_invalid_code_owner_config_files`:
  Total number of failed requests caused by an invalid / non-parsable code owner
  config file.
    * `project`:
      The name of the project that contains the invalid code owner config file.
    * `branch`:
      The name of the branch that contains the invalid code owner config file.
    * `path`:
      The path of the invalid code owner config file.

---

Back to [@PLUGIN@ documentation index](index.html)

Part of [Gerrit Code Review](../../../Documentation/index.html)
