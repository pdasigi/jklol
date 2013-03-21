package com.jayantkrish.jklol.boost;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.util.Assignment;

public class FactorFunctionalGradient implements FunctionalGradient {

  private final List<Factor> regressionTargets;
  private final List<Assignment> regressionAssignments;
  
  public FactorFunctionalGradient(List<Factor> regressionTargets,
      List<Assignment> regressionAssignments) {
    this.regressionTargets = Lists.newArrayList(Preconditions.checkNotNull(regressionTargets));
    this.regressionAssignments = Lists.newArrayList(Preconditions.checkNotNull(regressionAssignments));
    
    Preconditions.checkArgument(regressionTargets.size() == regressionAssignments.size());
  }
  
  public static FactorFunctionalGradient empty() {
    return new FactorFunctionalGradient(Collections.<Factor>emptyList(),
        Collections.<Assignment>emptyList());
  }

  public void addExample(Factor regressionTarget, Assignment assignment) {
    regressionTargets.add(regressionTarget);
    regressionAssignments.add(assignment);
  }

  public List<Factor> getRegressionTargets() {
    return regressionTargets;
  }

  public List<Assignment> getRegressionAssignments() {
    return regressionAssignments;
  }
}
