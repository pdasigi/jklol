package com.jayantkrish.jklol.models.loglinear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jayantkrish.jklol.util.IndexedList;

/**
 * {@link LogLinearParameters} stores all of the parameters for a {@link LogLinearModel}. 
 * This includes all distinct features that exist within a particular FactorGraph, along with
 * their weights.
 */
public class LogLinearParameters {

	private IndexedList<FeatureFunction> allFeatures;
	private List<Double> featureWeights;

	public LogLinearParameters() {
		featureWeights = new ArrayList<Double>();
		allFeatures = new IndexedList<FeatureFunction>();
	}

	/**
	 * Copy constructor
	 * @param fs
	 */
	public LogLinearParameters(LogLinearParameters fs) {
		this.featureWeights = new ArrayList<Double>(fs.featureWeights);
		this.allFeatures = new IndexedList<FeatureFunction>(fs.allFeatures);
	}

	public void addFeature(FeatureFunction feature) {
		assert allFeatures.size() == featureWeights.size();
		if (allFeatures.contains(feature)) {
			return;
		}
		allFeatures.add(feature);
		featureWeights.add(0.0);

		assert allFeatures.size() == featureWeights.size();
	}

	public void setFeatureWeight(FeatureFunction feature, double weight) {
		addFeature(feature);

		int featureInd = allFeatures.getIndex(feature);
		featureWeights.set(featureInd, weight);
	}

	public double getFeatureWeight(FeatureFunction feature) {
		assert allFeatures.contains(feature);

		int featureInd = allFeatures.getIndex(feature);
		return featureWeights.get(featureInd);
	}

	public List<FeatureFunction> getFeatures() {
		return allFeatures.items();
	}

	/**
	 * Add a vector to the feature weights of the model.
	 */
	public void incrementFeatureWeights(Map<FeatureFunction, Double> gradient) {
		for (FeatureFunction f : gradient.keySet()) {
			setFeatureWeight(f, getFeatureWeight(f) + gradient.get(f));
		}
	}
}