package com.jayantkrish.jklol.cfg;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

public class CptCfgFactor extends AbstractParametricFactor<SufficientStatistics> {

  private final VariableNumMap parentVar;
  private final VariableNumMap childVar;
  private final Grammar grammar;
  private final CptProductionDistribution parameterTemplate;
  
  public CptCfgFactor(VariableNumMap parentVar, VariableNumMap childVar,
      Grammar grammar, CptProductionDistribution grammarTemplate) {
    super(parentVar.union(childVar));
    this.parentVar = Preconditions.checkNotNull(parentVar);
    this.childVar = Preconditions.checkNotNull(childVar);
    this.grammar = grammar;
    this.parameterTemplate = Preconditions.checkNotNull(grammarTemplate);
    
    Preconditions.checkArgument(parentVar.size() == 1);
    Preconditions.checkArgument(childVar.size() == 1);
  }
  
  @Override
  public Factor getFactorFromParameters(SufficientStatistics parameters) {
    Preconditions.checkArgument(parameters instanceof CptProductionDistribution);
    CptProductionDistribution productionDistribution = (CptProductionDistribution) parameters;
    return new CfgFactor(parentVar.getDiscreteVariables().get(0), childVar.getDiscreteVariables().get(0),
        parentVar.getVariableNums().get(0), childVar.getVariableNums().get(0), 
        grammar, productionDistribution);
  }

  @Override
  public CptProductionDistribution getNewSufficientStatistics() {
    return parameterTemplate.emptyCopy();
  }

  @Override
  public SufficientStatistics getSufficientStatisticsFromAssignment(
      Assignment assignment, double count) {
    throw new UnsupportedOperationException("Cannot compute statistics from an assignment.");
  }

  @Override
  public SufficientStatistics getSufficientStatisticsFromMarginal(
      Factor marginal, double count, double partitionFunction) {
    Preconditions.checkArgument(marginal instanceof CfgFactor);
    ParseChart chart = ((CfgFactor) marginal).getMarginalChart(true);
    
    // Update binary/terminal rule counts
    CptProductionDistribution newProductionDist = getNewSufficientStatistics();
    newProductionDist.incrementBinaryCpts(
        chart.getBinaryRuleExpectations(), count / partitionFunction);
    newProductionDist.incrementTerminalCpts(
        chart.getTerminalRuleExpectations(), count / partitionFunction);

    return newProductionDist;
  }
  
  public CptProductionDistribution getParameterTemplate() {
    return parameterTemplate;
  }
}