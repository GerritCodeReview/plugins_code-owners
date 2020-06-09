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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.acceptance.AbstractCodeOwnersTest;
import com.google.gerrit.plugins.codeowners.acceptance.testsuite.CodeOwnerConfigOperations;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigHierarchy.CodeOwnerConfigVisitor;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Tests for {@link CodeOwnerConfigHierarchy}. */
public class CodeOwnerConfigHierarchyTest extends AbstractCodeOwnersTest {
  private CodeOwnerConfigOperations codeOwnerConfigOperations;
  private CodeOwnerConfigHierarchy codeOwnerConfigHierarchy;

  @Before
  public void setUpCodeOwnersPlugin() throws Exception {
    codeOwnerConfigOperations =
        plugin.getSysInjector().getInstance(CodeOwnerConfigOperations.class);
    codeOwnerConfigHierarchy = plugin.getSysInjector().getInstance(CodeOwnerConfigHierarchy.class);
  }

  @Test
  public void visitorNotInvokedIfNoCodeOwnerConfigExists() throws Exception {
    CodeOwnerConfigVisitor visitor = mock(CodeOwnerConfigVisitor.class);
    codeOwnerConfigHierarchy.visit(
        BranchNameKey.create(project, "master"), Paths.get("/foo/bar/baz.md"), visitor);
    verifyZeroInteractions(visitor);
  }

  @Test
  public void visitorNotInvokedForNonApplyingCodeOwnerConfig() throws Exception {
    String branch = "master";

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/other/")
        .addCodeOwnerEmail(admin.email())
        .create();

    CodeOwnerConfigVisitor visitor = mock(CodeOwnerConfigVisitor.class);
    codeOwnerConfigHierarchy.visit(
        BranchNameKey.create(project, branch), Paths.get("/foo/bar/baz.md"), visitor);
    verifyZeroInteractions(visitor);
  }

  @Test
  public void visitorInvokedForApplyingCodeOwnerConfigs() throws Exception {
    String branch = "master";

    CodeOwnerConfig.Key rootCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfigVisitor visitor = mock(CodeOwnerConfigVisitor.class);
    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true);
    codeOwnerConfigHierarchy.visit(
        BranchNameKey.create(project, branch), Paths.get("/foo/bar/baz.md"), visitor);

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(rootCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }

  @Test
  public void visitorCanStopTheIterationOverCodeOwnerConfigsByReturningFalse() throws Exception {
    String branch = "master";

    codeOwnerConfigOperations
        .newCodeOwnerConfig()
        .project(project)
        .branch(branch)
        .folderPath("/")
        .addCodeOwnerEmail(admin.email())
        .create();

    CodeOwnerConfig.Key fooCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfig.Key fooBarCodeOwnerConfigKey =
        codeOwnerConfigOperations
            .newCodeOwnerConfig()
            .project(project)
            .branch(branch)
            .folderPath("/foo/bar/")
            .addCodeOwnerEmail(admin.email())
            .create();

    CodeOwnerConfigVisitor visitor = mock(CodeOwnerConfigVisitor.class);
    // Return true for the first time the visitor is invoked, and false for all further invocations.
    when(visitor.visit(any(CodeOwnerConfig.class))).thenReturn(true).thenReturn(false);
    codeOwnerConfigHierarchy.visit(
        BranchNameKey.create(project, branch), Paths.get("/foo/bar/baz.md"), visitor);

    // Verify that we received the callbacks in the right order, starting from the folder of the
    // given path up to the root folder. We expect only 2 callbacks, since the visitor returns false
    // for the second invocation.
    InOrder orderVerifier = Mockito.inOrder(visitor);
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooBarCodeOwnerConfigKey).get());
    orderVerifier
        .verify(visitor)
        .visit(codeOwnerConfigOperations.codeOwnerConfig(fooCodeOwnerConfigKey).get());
    verifyNoMoreInteractions(visitor);
  }
}
