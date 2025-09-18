// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.codeowners.backend;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.plugins.codeowners.backend.config.CodeOwnersPluginConfiguration;
import com.google.gerrit.plugins.codeowners.backend.config.InvalidPluginConfigurationException;
import com.google.gerrit.plugins.codeowners.metrics.CodeOwnerMetrics;
import com.google.gerrit.server.ExceptionHook;
import com.google.inject.Inject;
import java.nio.file.InvalidPathException;
import java.util.Optional;

/**
 * Class to define the HTTP response status code and message for exceptions that can occur for all
 * REST endpoints and which should not result in a 500 Internal Server Error.
 *
 * <p>The following exceptions are handled:
 *
 * <ul>
 *   <li>exception due to invalid plugin configuration ({@link
 *       InvalidPluginConfigurationException}): mapped to {@code 409 Conflict}
 *   <li>exception due to invalid code owner config files ({@link
 *       org.eclipse.jgit.errors.ConfigInvalidException}): mapped to {@code 409 Conflict}
 * </ul>
 */
public class CodeOwnersExceptionHook implements ExceptionHook {
  private final CodeOwnersPluginConfiguration codeOwnersPluginConfiguration;
  private final CodeOwnerMetrics codeOwnerMetrics;

  @Inject
  CodeOwnersExceptionHook(
      CodeOwnersPluginConfiguration codeOwnersPluginConfiguration,
      CodeOwnerMetrics codeOwnerMetric) {
    this.codeOwnersPluginConfiguration = codeOwnersPluginConfiguration;
    this.codeOwnerMetrics = codeOwnerMetric;
  }

  @Override
  public boolean skipRetryWithTrace(String actionType, String actionName, Throwable throwable) {
    return isCausedByConfigurationError(throwable);
  }

  @Override
  public ImmutableList<String> getUserMessages(Throwable throwable, ImmutableSet<String> traceId) {
    Optional<InvalidPluginConfigurationException> invalidPluginConfigurationException =
        getInvalidPluginConfigurationCause(throwable);
    if (invalidPluginConfigurationException.isPresent()) {
      return ImmutableList.of(invalidPluginConfigurationException.get().getMessage());
    }

    Optional<InvalidCodeOwnerConfigException> invalidCodeOwnerConfigException =
        CodeOwners.getInvalidCodeOwnerConfigCause(throwable);
    if (invalidCodeOwnerConfigException.isPresent()) {
      codeOwnerMetrics.countInvalidCodeOwnerConfigFiles.increment(
          invalidCodeOwnerConfigException.get().getProjectName().get(),
          invalidCodeOwnerConfigException.get().getRef(),
          invalidCodeOwnerConfigException.get().getCodeOwnerConfigFilePath());

      ImmutableList.Builder<String> messages = ImmutableList.builder();
      messages.add(invalidCodeOwnerConfigException.get().getMessage());
      codeOwnersPluginConfiguration
          .getProjectConfig(invalidCodeOwnerConfigException.get().getProjectName())
          .getInvalidCodeOwnerConfigInfoUrl()
          .ifPresent(
              invalidCodeOwnerConfigInfoUrl ->
                  messages.add(String.format("For help check %s", invalidCodeOwnerConfigInfoUrl)));
      return messages.build();
    }

    Optional<InvalidPathException> invalidPathException = getInvalidPathException(throwable);
    if (invalidPathException.isPresent()) {
      return ImmutableList.of(invalidPathException.get().getMessage());
    }

    return ImmutableList.of();
  }

  @Override
  public Optional<Status> getStatus(Throwable throwable) {
    if (isCausedByConfigurationError(throwable)) {
      return Optional.of(Status.create(409, "Conflict"));
    }
    return Optional.empty();
  }

  public static boolean isCausedByConfigurationError(Throwable throwable) {
    return isInvalidPluginConfigurationException(throwable)
        || isInvalidCodeOwnerConfigException(throwable)
        || isInvalidPathException(throwable);
  }

  public static Optional<? extends Exception> getCauseOfConfigurationError(Throwable throwable) {
    Optional<InvalidPathException> invalidPathException =
        CodeOwnersExceptionHook.getInvalidPathException(throwable);
    if (invalidPathException.isPresent()) {
      return invalidPathException;
    }

    Optional<InvalidPluginConfigurationException> invalidPluginConfigurationException =
        CodeOwnersExceptionHook.getInvalidPluginConfigurationCause(throwable);
    if (invalidPluginConfigurationException.isPresent()) {
      return invalidPluginConfigurationException;
    }

    return CodeOwners.getInvalidCodeOwnerConfigCause(throwable);
  }

  private static boolean isInvalidPluginConfigurationException(Throwable throwable) {
    return getInvalidPluginConfigurationCause(throwable).isPresent();
  }

  public static Optional<InvalidPluginConfigurationException> getInvalidPluginConfigurationCause(
      Throwable throwable) {
    return getCause(InvalidPluginConfigurationException.class, throwable);
  }

  private static boolean isInvalidPathException(Throwable throwable) {
    return getInvalidPathException(throwable).isPresent();
  }

  public static Optional<InvalidPathException> getInvalidPathException(Throwable throwable) {
    return getCause(InvalidPathException.class, throwable);
  }

  private static <T extends Throwable> Optional<T> getCause(
      Class<T> exceptionClass, Throwable throwable) {
    return Throwables.getCausalChain(throwable).stream()
        .filter(exceptionClass::isInstance)
        .map(exceptionClass::cast)
        .findFirst();
  }

  private static boolean isInvalidCodeOwnerConfigException(Throwable throwable) {
    return CodeOwners.getInvalidCodeOwnerConfigCause(throwable).isPresent();
  }
}
