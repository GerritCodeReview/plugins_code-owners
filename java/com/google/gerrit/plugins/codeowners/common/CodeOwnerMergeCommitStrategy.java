package com.google.gerrit.plugins.codeowners.common;

import com.google.gerrit.entities.Project;

public interface CodeOwnerMergeCommitStrategy {
  MergeCommitStrategy getMergeCommitStrategy(Project.NameKey project);
}
