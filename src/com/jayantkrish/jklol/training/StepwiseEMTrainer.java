package com.jayantkrish.jklol.training;

import com.jayantkrish.jklol.models.*;
import com.jayantkrish.jklol.inference.InferenceEngine;

import java.util.*;
import java.lang.Math;

/**
 * Train the weights of a BayesNet using stepwise EM,
 * an online variant of EM.
 */
public class StepwiseEMTrainer {

    private InferenceEngine inferenceEngine;
    private int batchSize;
    private int numIterations;
    private double smoothing;
    private double decayRate;
    private LogFunction log;

    private double totalDecay;

    /**
     * decayRate controls how fast old statistics are removed from the model. It must satisfy 0.5 <
     * decayRate <= 1. Smaller decayRates cause old statistics to be forgotten faster.
     */
    public StepwiseEMTrainer(int numIterations, int batchSize, double smoothing, 
	    double decayRate, InferenceEngine inferenceEngine) {
	assert 0.5 < decayRate;
	assert decayRate <= 1.0;

	this.numIterations = numIterations;
	this.batchSize = batchSize;
	this.smoothing = smoothing;
	this.decayRate = decayRate;
	this.inferenceEngine = inferenceEngine;
	this.log = null;

	this.totalDecay = 1.0;
    }

    public StepwiseEMTrainer(int numIterations, int batchSize, double smoothing, 
	    double decayRate, InferenceEngine inferenceEngine, LogFunction log) {
	assert 0.5 < decayRate;
	assert decayRate <= 1.0;

	this.numIterations = numIterations;
	this.batchSize = batchSize;
	this.smoothing = smoothing;
	this.decayRate = decayRate;
	this.inferenceEngine = inferenceEngine;
	this.log = log;

	this.totalDecay = 1.0;
    }

    public void train(BayesNet bn, List<Assignment> trainingData) {
	initializeCpts(bn);
	inferenceEngine.setFactorGraph(bn);

	List<CptFactor> cptFactors = bn.getCptFactors();
	DiscreteFactor[][] storedMarginals = new DiscreteFactor[cptFactors.size()][batchSize];
	int numUpdates = 0;

	for (int i = 0; i < numIterations; i++) {
	    if (log != null) {log.notifyIterationStart(i);}
	    Collections.shuffle(trainingData);
	    for (int j = 0; j < trainingData.size(); j++) {
		int exampleIterNum = i * trainingData.size() + j;
		if (exampleIterNum % batchSize == 0 && exampleIterNum != 0) {
		    performParameterUpdate(cptFactors, storedMarginals, batchSize, numUpdates);
		    numUpdates++;
		}

		Assignment trainingExample = trainingData.get(j);
		if (log != null) {log.log(i, j, trainingExample, bn);}

		inferenceEngine.computeMarginals(trainingExample);
		for (int k = 0; k < cptFactors.size(); k++) {
		    CptFactor cptFactor = cptFactors.get(k);
		    DiscreteFactor marginal = inferenceEngine.getMarginal(cptFactor.getVars().getVariableNums());

		    storedMarginals[k][exampleIterNum % batchSize] = marginal;
		    
		    if (log != null) {log.log(i, j, cptFactor, marginal, bn);}
		}
	    }
	    if (log != null) {log.notifyIterationEnd(i);}
	}
    }


    public void performParameterUpdate(List<CptFactor> factorsToUpdate, DiscreteFactor[][] marginals, 
	    int numValidEntries, int numUpdates) {

	// Instead of multiplying the sufficient statistics (dense update)
	// use a sparse update which simply increases the weight of the added marginal.
	double batchDecayParam = Math.pow((numUpdates + 2), -1.0 * decayRate);
	totalDecay *= (1.0 - batchDecayParam);

	double batchMultiplier = batchDecayParam / totalDecay;
	for (int i = 0; i < factorsToUpdate.size(); i++) {
	    CptFactor factor = factorsToUpdate.get(i);
	    for (int j = 0; j < numValidEntries; j++) {
		factor.incrementOutcomeCount(marginals[i][j], batchMultiplier);
	    }
	}
    }


    private void initializeCpts(BayesNet bn) {
    	// Set all CPT statistics to the smoothing value
	for (CptFactor cptFactor : bn.getCptFactors()) {
	    cptFactor.clearCpt();
	    cptFactor.addUniformSmoothing(smoothing);
	}
    }
}