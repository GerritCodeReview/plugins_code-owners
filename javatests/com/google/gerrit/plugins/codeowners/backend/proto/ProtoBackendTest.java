package com.google.gerrit.plugins.codeowners.backend.proto;

import static com.google.gerrit.truth.OptionalSubject.assertThat;

import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnerBackend;
import com.google.gerrit.plugins.codeowners.backend.AbstractFileBasedCodeOwnerBackendTest;
import com.google.gerrit.plugins.codeowners.backend.CodeOwnerConfigParser;
import com.google.gerrit.plugins.codeowners.backend.SimplePathExpressionMatcher;
import org.junit.Test;

/** Tests for {@link ProtoBackend}. */
public class ProtoBackendTest extends AbstractFileBasedCodeOwnerBackendTest {
  @Override
  protected Class<? extends AbstractFileBasedCodeOwnerBackend> getBackendClass() {
    return ProtoBackend.class;
  }

  @Override
  protected String getFileName() {
    return ProtoBackend.CODE_OWNER_CONFIG_FILE_NAME;
  }

  @Override
  protected Class<? extends CodeOwnerConfigParser> getParserClass() {
    return ProtoCodeOwnerConfigParser.class;
  }

  @Test
  public void getPathExpressionMatcher() throws Exception {
    assertThat(codeOwnerBackend.getPathExpressionMatcher(BranchNameKey.create(project, "master")))
        .value()
        .isInstanceOf(SimplePathExpressionMatcher.class);
  }
}
