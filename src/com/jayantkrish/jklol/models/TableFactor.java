package com.jayantkrish.jklol.models;

import java.util.Iterator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A TableFactor is a representation of a factor where each weight is set
 * beforehand. The internal representation is sparse, making it appropriate for
 * factors where many weight settings are 0.
 * 
 * TableFactors are immutable.
 */
public class TableFactor extends DiscreteFactor {

  private final Tensor weights;

  /**
   * Constructs a {@code TableFactor} involving the specified variable numbers
   * (whose possible values are in variables). Note that vars can only contain
   * DiscreteVariables.
   */
  public TableFactor(VariableNumMap vars, Tensor weights) {
    super(vars);
    Preconditions.checkArgument(vars.size() == vars.getDiscreteVariables().size());
    this.weights = weights;
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns unit weight to
   * {@code assignment} and 0 to all other assignments. Requires
   * {@code assignment} to contain all of {@code vars}.
   * 
   * @param vars
   * @param assignment
   * @return
   */
  public static TableFactor pointDistribution(VariableNumMap vars, Assignment assignment) {
    TableFactorBuilder builder = new TableFactorBuilder(vars);
    builder.setWeight(assignment, 1.0);
    return builder.build();
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns 0 weight to all
   * assignments.
   * 
   * @param vars
   * @return
   */
  public static TableFactor zero(VariableNumMap vars) {
    TableFactorBuilder builder = new TableFactorBuilder(vars);
    return builder.build();
  }

  /**
   * Gets a {@code TableFactor} over {@code vars} which assigns weight 1 to all
   * assignments.
   * 
   * @param vars
   * @return
   */
  public static TableFactor unity(VariableNumMap vars) {
    TableFactorBuilder builder = new TableFactorBuilder(vars);
    Iterator<Assignment> iter = new AllAssignmentIterator(vars);
    while (iter.hasNext()) {
      builder.setWeight(iter.next(), 1.0);
    }
    return builder.build();
  }

  // //////////////////////////////////////////////////////////////////////////////
  // Factor overrides.
  // //////////////////////////////////////////////////////////////////////////////

  @Override
  public Iterator<Assignment> outcomeIterator() {
    return Iterators.transform(weights.keyIterator(), new Function<int[], Assignment>() {
      @Override
      public Assignment apply(int[] values) {
        return getVars().intArrayToAssignment(values);
      }
    });
  }

  @Override
  public double getUnnormalizedProbability(Assignment a) {
    Preconditions.checkArgument(a.containsAll(getVars().getVariableNums()));
    return weights.getByDimKey(getVars().assignmentToIntArray(a));
  }

  @Override
  public Tensor getWeights() {
    return weights;
  }

  @Override
  public double size() {
    return weights.size();
  }

  @Override
  public TableFactor relabelVariables(VariableRelabeling relabeling) {
    return new TableFactor(relabeling.apply(getVars()),
        weights.relabelDimensions(relabeling.getVariableIndexReplacementMap()));
  }

  @Override
  public String toString() {
    return "TableFactor(" + getVars() + ")(" + weights.size() + " weights)";
  }
}
