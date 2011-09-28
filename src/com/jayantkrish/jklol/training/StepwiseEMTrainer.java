package com.jayantkrish.jklol.training;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Train the weights of a ParametricFactorGraph using stepwise EM, an online variant of EM.
 */
public class StepwiseEMTrainer {

  private final int batchSize;
  private final int numIterations;
  private final double decayRate;
  private final LogFunction log;

  // These parameters control the amount of parallel execution.
  // The sufficient statistics of each batch are run as numConcurrent
  // parallel tasks, which are evaluated using executor.
  private final SufficientStatisticsCalculator statisticsCalculator;

  /**
   * Creates a trainer which performs {@code numIterations} of stepwise EM
   * training. Each iteration of training processes a series of training example
   * batches; after each batch, the model parameters are updated.
   * {@code batchSize} controls the number of training examples in each batch.
   * 
   * <p>
   * This class supports parallel training. Each batch is split into
   * {@code numConcurrent} mini-batches which are processed concurrently.
   * 
   * @param numIterations number of iterations to train for.
   * @param batchSize number of examples to process between parameter updates.
   * @param decayRate controls how fast old statistics are removed from the
   * model. Smaller decayRates cause old statistics to be forgotten faster. Must
   * satisfy {@code 0.5 < decayRate <= 1}.
   * @param inferenceEngineSupplier gets the inference procedure which computes
   * marginals for training.
   * @param numConcurrent number of mini-batches
   * @param log monitors training progress. If {@code null}, no logging is
   * performed.
   */
  public StepwiseEMTrainer(int numIterations, int batchSize, double decayRate,
      Supplier<MarginalCalculator> inferenceEngineSupplier, int numConcurrent,
      LogFunction log) {
    Preconditions.checkArgument(0.5 < decayRate && decayRate <= 1.0);

    this.numIterations = numIterations;
    this.batchSize = batchSize;
    this.decayRate = decayRate;
    this.log = log != null ? log : new NullLogFunction();

    this.statisticsCalculator = new SufficientStatisticsCalculator(
        inferenceEngineSupplier, numConcurrent);
  }

  /**
   * Trains {@code bn} using {@code trainingData} as the training data. After
   * this method returns, the parameters of {@code bn} will be updated to the
   * trained values.
   * 
   * <p>
   * {@code initialParameters} are used as the starting point for training. A
   * reasonable initialization for these parameters is the uniform distribution
   * (add-one smoothing). These parameters can be retrieved using
   * {@code bn.getNewSufficientStatistics().increment(1)}. Note that stepwise EM
   * training will gradually forget the smoothing as the number of iterations
   * increases. {@code initialParameters} may be modified by this method.
   * 
   * @param bn
   * @param trainingData
   */
  public SufficientStatistics train(ParametricFactorGraph bn, SufficientStatistics initialParameters,
      List<Assignment> trainingData) {
    // Initialize state variables, which are used in updateBatchStatistics() and
    // updateParameters()
    double totalDecay = 1.0;
    // Make sure that the training data list is safe for concurrent access.
    List<Assignment> trainingDataList = Lists.newArrayList(trainingData);
    int numUpdates = 0;

    Collections.shuffle(trainingDataList);

    for (int i = 0; i < numIterations; i++) {
      log.notifyIterationStart(i);

      int numBatches = (int) Math.ceil(((double) trainingDataList.size()) / batchSize);
      for (int j = 0; j < numBatches; j++) {
        List<Assignment> batch = trainingDataList.subList(j * batchSize,
            Math.min((j + 1) * batchSize, trainingDataList.size()));
        
        // Calculate the sufficient statistics for batch.
        FactorGraph factorGraph = bn.getFactorGraphFromParameters(initialParameters);
        SufficientStatistics batchStatistics = statisticsCalculator
            .computeSufficientStatistics(factorGraph, bn, batch, log).getStatistics();
        
        // Update the the parameter vector.
        // Instead of multiplying the sufficient statistics (dense update)
        // use a sparse update which simply increases the weight of the added
        // marginals.
        double batchDecayParam = Math.pow((numUpdates + 2), -1.0 * decayRate);
        double newTotalDecay = totalDecay * (1.0 - batchDecayParam);
        double batchMultiplier = batchDecayParam / newTotalDecay;

        initialParameters.increment(batchStatistics, batchMultiplier);

        totalDecay = newTotalDecay;
        numUpdates++;
      }

      log.notifyIterationEnd(i);
    }
    return initialParameters;
  }
}
