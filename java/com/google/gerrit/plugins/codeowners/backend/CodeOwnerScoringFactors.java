package com.google.gerrit.plugins.codeowners.backend;

import java.util.HashMap;
import java.util.Map;
/** Container with scoring factors for a code owner. */
public class CodeOwnerScoringFactors {
  private final Map<String, Integer> scoringFactors;
  public CodeOwnerScoringFactors() {
    scoringFactors = new HashMap<>();
  }
  public void put(String codeOwnerScore, Integer value){
    if(codeOwnerScore!=null && value!=null) {
      scoringFactors.put(codeOwnerScore, value);
    }
  }

  public Map<String, Integer> getScoringFactors() {
    return scoringFactors;
  }
}
