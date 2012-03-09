package com.jayantkrish.jklol.models;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.tensor.CachedSparseTensor;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.LogSpaceTensorAdapter;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.TensorBase.KeyValue;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Helper class for constructing {@link TableFactor}s. This class takes
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
    Preconditions.checkArgument(variables.size() == variables.getDiscreteVariables().size());
    this.vars = variables;
    this.weightBuilder = new SparseTensorBuilder(Ints.toArray(vars.getVariableNums()),
        vars.getVariableSizes());
  }

  /**
   * Copy constructor.
   * 
   * @param toCopy
   */
  public TableFactorBuilder(TableFactorBuilder toCopy) {
    this.vars = Preconditions.checkNotNull(toCopy.getVars());
    this.weightBuilder = toCopy.weightBuilder.getCopy();
  }

  /**
   * Gets a {@code TableFactorBuilder} where each outcome is initialized with a
   * weight of 1.
   * 
   * @param variables
   * @return
   */
  public static TableFactorBuilder ones(VariableNumMap variables) {
    TableFactorBuilder builder = new TableFactorBuilder(variables);
    Iterator<Assignment> allAssignmentIter = new AllAssignmentIterator(variables);
    while (allAssignmentIter.hasNext()) {
      builder.setWeight(allAssignmentIter.next(), 1.0);
    }
    return builder;
  }

  /**
   * Gets a {@code TableFactorBuilder} containing the same assignments and weights
   * as {@code probabilities}.
   * 
   * @param variables
   * @param probabilities
   * @return
   */
  public static TableFactorBuilder fromMap(VariableNumMap variables,
      Map<Assignment, Double> probabilities) {
    TableFactorBuilder builder = new TableFactorBuilder(variables);
    for (Map.Entry<Assignment, Double> outcome : probabilities.entrySet()) {
      builder.setWeight(outcome.getKey(), outcome.getValue());
    }
    return builder;
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

  public void setWeight(double weight, Object... varValues) {
    setWeightList(Arrays.asList(varValues), weight);
  }

  /**
   * Sets the weight of {@code a} to {@code weight} in the table factor returned
   * by {@link #build()}. If {@code a} has already been associated with a weight
   * in {@code this} builder, this call overwrites the old weight. If
   * {@code weight} is 0.0, {@code a} is deleted from this builder.
   */
  public void setWeight(Assignment a, double weight) {
    Preconditions.checkArgument(weight >= 0.0, "Weight must be positive, tried using: " + weight);
    Preconditions.checkArgument(a.containsAll(vars.getVariableNums()));
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
   * Increments the weight of each assignment in {@code this} by its weight in
   * {@code factor}.
   * 
   * @param factor
   */
  public void incrementWeight(DiscreteFactor factor) {
    Preconditions.checkArgument(factor.getVars().equals(getVars()));
    Iterator<Outcome> iter = factor.outcomeIterator();
    while (iter.hasNext()) {
      Outcome outcome = iter.next();
      incrementWeight(outcome.getAssignment(), outcome.getProbability());
    }
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
   * Sets the weight of {@code assignment} to
   * {@code Math.max(getWeight(assignment), weight)}.
   * 
   * @param assignment
   * @param weight
   */
  public void maxWeight(Assignment assignment, double weight) {
    setWeight(assignment, Math.max(getWeight(assignment), weight));
  }

  /**
   * Gets the weight of {@code assignment}. If no weight has been set for
   * {@code assignment}, returns 0.
   * 
   * @param assignment
   * @return
   */
  public double getWeight(Assignment assignment) {
    return weightBuilder.getByDimKey(vars.assignmentToIntArray(assignment));
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
    return Iterators.transform(weightBuilder.keyValueIterator(), new Function<KeyValue, Assignment>() {
      @Override
      public Assignment apply(KeyValue keyValue) {
        return getVars().intArrayToAssignment(keyValue.getKey());
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

  /**
   * Creates a {@code TableFactor} containing all of the assignment/weight
   * mappings that added to {@code this} builder. Caches out all permutations of
   * the weight tensor, for fast multiplication and additions. The returned
   * factor is defined over the variables in {@code this.getVars()}.
   * 
   * @return
   */
  public TableFactor buildWithCache() {
    return new TableFactor(vars, CachedSparseTensor.cacheAllPermutations(weightBuilder.build()));
  }

  /**
   * Creates a {@code TableFactor} containing all of the assignment/weight
   * mappings that added to {@code this} builder. Unlike {@link #build()},
   * assignments and weights added to this are interpreted as log weights.
   * 
   * @return
   */
  public TableFactor buildInLogSpace() {
    return new TableFactor(vars, new LogSpaceTensorAdapter(
        DenseTensor.copyOf(weightBuilder.build())));
  }
}
