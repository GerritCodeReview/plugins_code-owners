package com.google.gerrit.plugins.codeowners.backend;

import java.util.HashMap;
import java.util.Map;
/** Container with scoring factors for a code owner. */
public class CodeOwnerScoringFactors {
  private final Map<CodeOwnerScore, Integer> scoringFactors;
  public CodeOwnerScoringFactors() {
    scoringFactors = new HashMap<>();
  }
  public void put(CodeOwnerScore codeOwnerScore, Integer value){
    if(codeOwnerScore!=null && value!=null) {
      scoringFactors.put(codeOwnerScore, value);
    }
  }

  public Map<CodeOwnerScore, Integer> getScoringFactors() {
    return scoringFactors;
  }
}
