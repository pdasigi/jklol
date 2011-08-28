package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * An MarginalCalculator is an algorithm for computing marginal distributions
 * of a factor graph.
 */
public interface MarginalCalculator {

	/**
	 * Compute (unconditional) marginal distributions over the factors in the factor graph.
	 */
	public MarginalSet computeMarginals(FactorGraph factorGraph);

	/**
	 * Compute marginals conditioned on the provided variable assignments. Passing an empty
	 * assignment results in unconditional marginals.
	 */
	public MarginalSet computeMarginals(FactorGraph factorGraph, Assignment assignment);

	/**
	 * Compute unconditional max marginals.
	 */
	public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph);

	/**
	 * Compute max marginals conditioned on the provided assignment.
	 */
	public MaxMarginalSet computeMaxMarginals(FactorGraph factorGraph, Assignment assignment);

}