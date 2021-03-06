package com.jayantkrish.jklol.models.bayesnet;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.AbstractParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A conditional probability table representing a probability distribution over
 * a child variable given its parents. This is the typical kind of factor you
 * expect to find in a Bayesian Network.
 * 
 * Sparse distributions can be encoded using {@code CptTableFactor} by
 * initializing the model parameters with zeros. If the sparsity structure is
 * a property of the model, however, it is more convenient to use
 * {@code SparseCptTableFactor}, which preserves sparsity with the standard
 * parameter vector operations. 
 */
public class CptTableFactor extends AbstractParametricFactor {

  private static final long serialVersionUID = 2120605287432496097L;
  
  // These are the parent and child variables in the CPT.
  private final VariableNumMap parentVars;
  private final VariableNumMap childVars;

  /**
   * Construct a conditional probability distribution over {@code childVars}
   * given {@code parentVars}. This describes a v-structure pointing from each
   * variable in {@code parentVars} to {@code childVars}.
   */
  public CptTableFactor(VariableNumMap parentVars, VariableNumMap childVars) {
    super(parentVars.union(childVars));
    Preconditions.checkArgument(parentVars.getDiscreteVariables().size() == parentVars.size());
    Preconditions.checkArgument(childVars.getDiscreteVariables().size() == childVars.size());
    this.parentVars = parentVars;
    this.childVars = childVars;
  }

  // ////////////////////////////////////////////////////////////////
  // ParametricFactor / CptFactor methods
  // ///////////////////////////////////////////////////////////////

  @Override
  public DiscreteFactor getModelFromParameters(SufficientStatistics parameters) {
    TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) parameters;
    Tensor allTensor = tensorStats.get();
    Tensor parentTensor = allTensor.sumOutDimensions(childVars.getVariableNumsArray());
    
    return new TableFactor(getVars(), allTensor.elementwiseProduct(parentTensor.elementwiseInverse()));
  }
  
  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) { 
    DiscreteFactor factor = getModelFromParameters(parameters);
    if (numFeatures >= 0) {
      return factor.describeAssignments(factor.getMostLikelyAssignments(numFeatures));
    } else {
      return factor.getParameterDescription();
    }
  }

  @Override
  public TensorSufficientStatistics getNewSufficientStatistics() {
    TensorBuilder combinedTensor = getTensorFromVariables(getVars());
    return new TensorSufficientStatistics(getVars(), combinedTensor);
  }

  /**
   * Constructs a tensor with one dimension per variable in {@code variables}.
   * 
   * @param variables
   * @return
   */
  private static TensorBuilder getTensorFromVariables(VariableNumMap variables) {
    // Get the dimensions and dimension sizes for the tensor.
    int[] dimensions = variables.getVariableNumsArray();
    int[] sizes = new int[dimensions.length];
    List<DiscreteVariable> varTypes = variables.getDiscreteVariables();
    for (int i = 0; i < varTypes.size(); i++) {
      sizes[i] = varTypes.get(i).numValues();
    }
    return new SparseTensorBuilder(dimensions, sizes);
  }
  
  public CptTableFactor rescaleFeatures(SufficientStatistics relabeling) {
    throw new UnsupportedOperationException("Rescaling features in CptTableFactor is not supported");
  }

  @Override
  public void incrementSufficientStatisticsFromAssignment(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Assignment a, double count) {
    Preconditions.checkArgument(a.containsAll(getVars().getVariableNumsArray()));
    TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) statistics;
    tensorStats.incrementFeature(a, count);
  }

  @Override
  public void incrementSufficientStatisticsFromMarginal(SufficientStatistics statistics,
      SufficientStatistics currentParameters, Factor marginal, Assignment conditionalAssignment,
      double count, double partitionFunction) {
    Assignment conditionalSubAssignment = conditionalAssignment.intersection(getVars());

    TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) statistics;
    Iterator<Outcome> outcomeIter = marginal.coerceToDiscrete().outcomeIterator();
    while (outcomeIter.hasNext()) {
      Outcome outcome = outcomeIter.next();
      Assignment a = outcome.getAssignment().union(conditionalSubAssignment);
      double incrementAmount = count * outcome.getProbability() / partitionFunction;
      
      tensorStats.incrementFeature(a, incrementAmount);
    }
  }

  // ///////////////////////////////////////////////////////////////////
  // CPTTableFactor methods
  // ///////////////////////////////////////////////////////////////////

  /**
   * Gets the parent variables of this factor.
   * 
   * @return
   */
  public VariableNumMap getParents() {
    return parentVars;
  }

  /**
   * Gets the child variables of this factor.
   * 
   * @return
   */
  public VariableNumMap getChildren() {
    return childVars;
  }

  /**
   * Get an iterator over all possible assignments to the parent variables
   */
  public Iterator<Assignment> parentAssignmentIterator() {
    return new AllAssignmentIterator(parentVars);
  }

  /**
   * Get an iterator over all possible assignments to the child variables
   */
  public Iterator<Assignment> childAssignmentIterator() {
    return new AllAssignmentIterator(childVars);
  }

  @Override
  public String toString() {
    return "[CptTableFactor Parents: " + parentVars.toString()
        + " Children: " + childVars.toString() + "]";
  }
}