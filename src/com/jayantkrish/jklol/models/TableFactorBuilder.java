package com.jayantkrish.jklol.models;

import java.util.Iterator;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.SparseTensorBuilder;

/**
 * Helper class for constructing {@linkTableFactor}s. This class takes
 * insertions in terms of {@code Assignments}, automatically constructing the
 * table of weights which underlies the {@code TableFactor}. Using this class is
 * less efficient than directly using the {@code TableFactor} constructor. This
 * class is intended to be used to define graphical models, but not during
 * inference or mathematical operations.
 * 
 * @author jayantk
 */
public class TableFactorBuilder {

  private final VariableNumMap vars;
  private final SparseTensorBuilder weightBuilder;

  /**
   * Creates a builder which builds a {@code TableFactor} over {@code variables}
   * .
   * 
   * @param vars
   */
  public TableFactorBuilder(VariableNumMap variables) {
    this.vars = variables;
    this.weightBuilder = new SparseTensorBuilder(Ints.toArray(vars.getVariableNums()));
  }

  /**
   * Gets the variables which this builder accepts assignments over.
   * 
   * @return
   */
  public VariableNumMap getVars() {
    return vars;
  }

  /**
   * Convenience wrapper for {@link #setWeight(Assignment, double)}.
   * {@code varValues} is the list of values to construct an assignment out of,
   * sorted in order of variable number.
   * 
   * @param varValues
   * @param weight
   */
  public void setWeightList(List<? extends Object> varValues, double weight) {
    Preconditions.checkNotNull(varValues);
    Preconditions.checkArgument(getVars().size() == varValues.size());
    setWeight(vars.outcomeToAssignment(varValues), weight);
  }

  /**
   * Sets the weight of {@code a} to {@code weight} in the table factor returned
   * by {@link #build()}. If {@code a} has already been associated with a weight
   * in {@code this} builder, this call overwrites the old weight. If
   * {@code weight} is 0.0, {@code a} is deleted from this builder.
   */
  public void setWeight(Assignment a, double weight) {
    Preconditions.checkArgument(weight >= 0.0, "Weight must be positive, tried using: " + weight);
    Preconditions.checkArgument(a.containsVars(vars.getVariableNums()));
    weightBuilder.put(vars.assignmentToIntArray(a), weight);
  }

  /**
   * Sets the weight of {@code assignment} to
   * {@code getWeight(assignment) + weight}. This is shorthand for a fairly
   * common operation when building factors.
   * 
   * @param assignment
   * @param weight
   */
  public void incrementWeight(Assignment assignment, double weight) {
    setWeight(assignment, getWeight(assignment) + weight);
  }

  /**
   * Sets the weight of {@code assignment} to
   * {@code getWeight(assignment) * weight}. This is shorthand for a fairly
   * common operation when building factors.
   * 
   * @param assignment
   * @param weight
   */
  public void multiplyWeight(Assignment assignment, double weight) {
    setWeight(assignment, getWeight(assignment) * weight);
  }

  /**
   * Gets the weight of {@code assignment}. If no weight has been set for
   * {@code assignment}, returns 0.
   * 
   * @param assignment
   * @return
   */
  public double getWeight(Assignment assignment) {
    return weightBuilder.get(vars.assignmentToIntArray(assignment));
  }

  /**
   * Returns {@code true} if this builder has a weight associated with
   * {@code assignment}.
   * 
   * @param assignment
   * @return
   */
  public boolean containsKey(Assignment assignment) {
    return weightBuilder.containsKey(vars.assignmentToIntArray(assignment));
  }

  /**
   * Gets the number of assignments in {@code this} with nonzero probability.
   * 
   * @return
   */
  public int size() {
    return weightBuilder.size();
  }

  /**
   * Gets an iterator over all {@code Assignment}s which have nonzero weight.
   * 
   * @return
   */
  public Iterator<Assignment> assignmentIterator() {
    return Iterators.transform(weightBuilder.keyIterator(), new Function<int[], Assignment>() {
      @Override
      public Assignment apply(int[] values) {
        return getVars().intArrayToAssignment(values);
      }
    });
  }

  /**
   * Creates a {@code TableFactor} containing all of the assignment/weight
   * mappings that added to {@code this} builder. The returned factor is defined
   * over the variables in {@code this.getVars()}.
   * 
   * @return
   */
  public TableFactor build() {
    return new TableFactor(vars, weightBuilder.build());
  }
}