package com.google.gerrit.plugins.codeowners.backend;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Per thread project cache that caches missing projects for the time of a request.
 *
 * <p>{@code com.google.gerrit.server.project.ProjectCacheImpl} doesn't cache missing projects, but
 * each time a non-existing project is looked up from storage. Caching the missing projects makes
 * repeated lookups of the same non-existing projects in a request cheaper.
 */
public class PerThreadProjectCache {
  private static final String KEY = "PerThreadProjectCache";

  /**
   * Cache at maximum 50 missing projects per thread. This value was chosen arbitrarily. To prevent
   * this class from accumulating an unbound number of objects, we enforce this limit.
   */
  private static final int PER_THREAD_PROJECT_CACHE_SIZE = 50;

  @Singleton
  public static class Factory {
    private final ProjectCache projectCache;

    @Inject
    Factory(ProjectCache projectCache) {
      this.projectCache = projectCache;
    }

    PerThreadProjectCache getOrCreate() {
      PerThreadProjectCache cache =
          PerThreadCache.getOrCompute(
              PerThreadCache.Key.create(PerThreadProjectCache.class, KEY),
              () -> new PerThreadProjectCache(projectCache));
      return cache;
    }
  }

  private final ProjectCache projectCache;
  private final Set<Project.NameKey> missingProjects = Collections.synchronizedSet(new HashSet<>());

  /** Private constructor to prevent instantiation from outside this class. */
  private PerThreadProjectCache(ProjectCache projectCache) {
    this.projectCache = projectCache;
  }

  public boolean isPresent(Project.NameKey projectName) {
    return get(projectName).isPresent();
  }

  public Optional<ProjectState> get(Project.NameKey projectName) {
    if (missingProjects.contains(projectName)) {
      return Optional.empty();
    }

    Optional<ProjectState> project = projectCache.get(projectName);
    if (!project.isPresent() && missingProjects.size() < PER_THREAD_PROJECT_CACHE_SIZE) {
      missingProjects.add(projectName);
    }
    return project;
  }

  @VisibleForTesting
  public Set<Project.NameKey> getMissingProjects() {
    return missingProjects;
  }
}
