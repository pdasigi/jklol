package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.lisp.ConsValue;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;

public class IndexableFunctionAssignment implements FunctionAssignment {
  
  protected final VariableNumMap inputVars;
  protected final DiscreteVariable outputVar;

  // The index of the value in outputVar that represents
  // an unassigned element.
  protected final int outputVarUnassigned;
  // All possible values of outputVar excluding the unassigned value.
  protected final List<Object> assignedOutputValuesList;
  protected final Object assignedOutputValuesConsList;
  
  // This tensor determines which assignments to inputVars
  // can be assigned a value. All other assignments return
  // the value sparsityValueIndex.
  protected final Tensor sparsity;
  protected final int sparsityValueIndex;
  
  // The values assigned to the permissible assignments. Each
  // assignment's index in values is determined by the 
  // corresponding index in the sparsity tensor.
  protected final int[] values;
  // Index of the first element in values whose value is 
  // unassigned.
  protected int firstUnassignedIndex;

  // A feature vector per input -> output mapping.
  protected final DiscreteVariable featureVar;
  protected final Tensor elementFeatures;

  // The current feature vector of this assignment.
  protected final double[] cachedFeatureVector;

  protected final FeatureVectorGenerator<FunctionAssignment> predicateFeatureGen;
  protected Tensor predicateFeatures;
  
  protected int id;

  protected IndexableFunctionAssignment(VariableNumMap inputVars, DiscreteVariable outputVar,
      int outputVarUnassigned, List<Object> assignedOutputValuesList, Object assignedOutputValuesConsList,
      Tensor sparsity, int sparsityValueIndex, int[] values,
      int firstUnassignedIndex, DiscreteVariable featureVar, Tensor elementFeatures, 
      double[] cachedFeatureVector, FeatureVectorGenerator<FunctionAssignment> predicateFeatureGen,
      Tensor predicateFeatures) {
    this.inputVars = Preconditions.checkNotNull(inputVars);
    this.outputVar = Preconditions.checkNotNull(outputVar);
    this.outputVarUnassigned = outputVarUnassigned;
    this.assignedOutputValuesList = assignedOutputValuesList;
    this.assignedOutputValuesConsList = assignedOutputValuesConsList;
    this.sparsity = Preconditions.checkNotNull(sparsity);
    this.sparsityValueIndex = sparsityValueIndex;
    this.values = values;
    this.firstUnassignedIndex = firstUnassignedIndex;
    this.featureVar = featureVar;
    this.elementFeatures = elementFeatures;
    this.cachedFeatureVector = cachedFeatureVector;
    this.predicateFeatureGen = predicateFeatureGen;
    this.predicateFeatures = predicateFeatures;
    this.id = -1;
  }

  public static IndexableFunctionAssignment unassignedDense(VariableNumMap inputVars,
      DiscreteVariable outputVar, Object unassignedValue, DiscreteVariable featureVar,
      Tensor features, FeatureVectorGenerator<FunctionAssignment> predicateFeatureGen) {
    int[] featureDims = features.getDimensionSizes();
    Preconditions.checkArgument(featureDims.length == inputVars.size() + 2,
        "Incorrect number of feature dimensions");
    Preconditions.checkArgument(featureDims[inputVars.size()] == outputVar.numValues(),
        "Incorrect ordering of feature dimensions");
    
    Tensor sparsity = DenseTensor.constant(inputVars.getVariableNumsArray(), inputVars.getVariableSizes(), 1.0);
    int[] values = new int[sparsity.size()];
    int unassignedValueIndex = outputVar.getValueIndex(unassignedValue);
    Arrays.fill(values, unassignedValueIndex);
    
    int[] featureDimSizes = features.getDimensionSizes();
    double[] cachedFeatureVector = new double[featureDimSizes[featureDimSizes.length - 1]];
    
    for (int i = 0; i < values.length; i++) {
      int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(i));
      updateFeatures(dimKey, values[i], features, inputVars.size(), cachedFeatureVector, 1.0);
    }
    
    List<Object> assignedOutputValuesList = Lists.newArrayList(outputVar.getValues());
    assignedOutputValuesList.remove(unassignedValue);
    Object assignedOutputValuesConsList = ConsValue.listToConsList(assignedOutputValuesList);

    return new IndexableFunctionAssignment(inputVars, outputVar,
        unassignedValueIndex, assignedOutputValuesList, assignedOutputValuesConsList,
        sparsity, -1, values, 0, featureVar, features, cachedFeatureVector, predicateFeatureGen,
        SparseTensor.empty(new int[] {0}, new int[] {predicateFeatureGen.getNumberOfFeatures()}));
  }

  public static IndexableFunctionAssignment unassignedSparse(VariableNumMap inputVars,
      DiscreteVariable outputVar, Object unassignedValue, Tensor sparsity,
      Object sparsityValue, DiscreteVariable featureVar, Tensor features,
      FeatureVectorGenerator<FunctionAssignment> predicateFeatureGen) {
    int[] featureDims = features.getDimensionSizes();
    Preconditions.checkArgument(featureDims.length == inputVars.size() + 2,
        "Incorrect number of feature dimensions");
    Preconditions.checkArgument(featureDims[inputVars.size()] == outputVar.numValues(),
        "Incorrect ordering of feature dimensions");

    int[] values = new int[sparsity.size()];
    int unassignedValueIndex = outputVar.getValueIndex(unassignedValue);
    int sparsityValueIndex = outputVar.getValueIndex(sparsityValue);

    Arrays.fill(values, unassignedValueIndex);

    int[] featureDimSizes = features.getDimensionSizes();
    double[] cachedFeatureVector = new double[featureDimSizes[featureDimSizes.length - 1]];

    for (int i = 0; i < values.length; i++) {
      int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(i));
      updateFeatures(dimKey, values[i], features, inputVars.size(), cachedFeatureVector, 1.0);
    }
    
    List<Object> assignedOutputValuesList = Lists.newArrayList(outputVar.getValues());
    assignedOutputValuesList.remove(unassignedValue);
    Object assignedOutputValuesConsList = ConsValue.listToConsList(assignedOutputValuesList);

    return new IndexableFunctionAssignment(inputVars, outputVar,
        unassignedValueIndex, assignedOutputValuesList, assignedOutputValuesConsList,
        sparsity, sparsityValueIndex, values, 0, featureVar, features, cachedFeatureVector,
        predicateFeatureGen, SparseTensor.empty(
            new int[] {0}, new int[] {predicateFeatureGen.getNumberOfFeatures()}));
  }

  public VariableNumMap getInputVars() {
    return inputVars;
  }
  
  public DiscreteVariable getOutputVar() {
    return outputVar;
  }
  
  public List<Object> getAssignedOutputValues() {
    return assignedOutputValuesList;
  }

  public Object getAssignedOutputValuesConsList() {
    return assignedOutputValuesConsList;
  }

  public int getUnassignedIndex() {
    return outputVarUnassigned;
  }

  public Tensor getSparsity() {
    return sparsity;
  }

  public DiscreteFactor getSparsityFactor() {
    return new TableFactor(inputVars, sparsity);
  }

  public Set<ImmutableList<Object>> getArgSet() {
    Set<ImmutableList<Object>> argSet = Sets.newHashSet();
    for (int i = 0; i < sparsity.size(); i++) {
      argSet.add(indexToArgs(i));
    }
    return argSet;
  }
  
  public Set<ImmutableList<Object>> getArgSetWithValue(Object value) {
    Set<ImmutableList<Object>> argSet = Sets.newHashSet();
    int valueInd = outputVar.getValueIndex(value);
    for (int i = 0; i < sparsity.size(); i++) {
      if (values[i] == valueInd) {
        argSet.add(indexToArgs(i));
      }
    }
    return argSet;
  }

  public int getSparsityIndex() {
    return sparsityValueIndex;
  }

  public int[] getValueArray() {
    return values;
  }

  public int getFirstUnassignedIndex() {
    return firstUnassignedIndex;
  }

  public DiscreteVariable getElementFeatureVar() {
    return featureVar;
  }

  public Tensor getElementFeatures() {
    return elementFeatures;
  }
  
  public DiscreteFactor getElementFeaturesFactor() {
    int[] dims = elementFeatures.getDimensionNumbers();
    int outputDim = dims[dims.length - 2];
    int featureDim = dims[dims.length - 1];
    
    VariableNumMap outputVarNumMap = VariableNumMap.singleton(outputDim,
        outputVar.getName(), outputVar);
    VariableNumMap featureVarNumMap = VariableNumMap.singleton(featureDim,
        featureVar.getName(), featureVar);
    
    VariableNumMap vars = VariableNumMap.unionAll(inputVars, outputVarNumMap, featureVarNumMap);
    return new TableFactor(vars, elementFeatures);
  }

  public boolean isComplete() {
    return firstUnassignedIndex == values.length;
  }

  public void indexToDimKey(int index, int[] toFill) {
    Preconditions.checkArgument(toFill.length == inputVars.size());
    sparsity.keyNumToDimKey(sparsity.indexToKeyNum(index), toFill);
  }

  public ImmutableList<Object> indexToArgs(int index) {
    long keyNum = sparsity.indexToKeyNum(index);
    int[] dimKey = sparsity.keyNumToDimKey(keyNum);
    return ImmutableList.copyOf(inputVars.intArrayToAssignment(dimKey).getValues());
  }

  public int argsToIndex(List<?> args) {
    long keyNum = sparsity.dimKeyToKeyNum(inputVars.outcomeToIntArray(args));
    int index = sparsity.keyNumToIndex(keyNum);
    if (index != -1) {
      return index;
    }
    return -1;
  }

  public int getValueIndex(int argIndex) {
    if (argIndex == -1) {
      return sparsityValueIndex;
    } else {
      return values[argIndex];
    }
  }

  @Override
  public Object getValue(List<?> args) {
    int index = argsToIndex(args);
    int valueIndex = -1;
    if (index == -1) {
      valueIndex = sparsityValueIndex;
    } else {
      valueIndex = values[index];
    }
    
    return outputVar.getValue(valueIndex);
  }

  @Override
  public void putValue(List<?> args, Object value) {
    int keyIndex = argsToIndex(args);
    Preconditions.checkArgument(keyIndex != -1,
        "Putting value for args \"%s\" not permitted by this FunctionAssignment.", args);
    int newValueIndex = outputVar.getValueIndex(value);

    put(keyIndex, newValueIndex);
  }
  
  public void put(int keyIndex, int newValueIndex) {
    Preconditions.checkArgument(keyIndex != -1);
    int oldValueIndex = values[keyIndex];
    values[keyIndex] = newValueIndex;
    
    int[] dimKey = sparsity.keyNumToDimKey(sparsity.indexToKeyNum(keyIndex));
    updateFeatures(dimKey, oldValueIndex, elementFeatures, inputVars.size(), cachedFeatureVector, -1.0);
    updateFeatures(dimKey, newValueIndex, elementFeatures, inputVars.size(), cachedFeatureVector, 1.0);
    
    while (firstUnassignedIndex < values.length &&
        values[firstUnassignedIndex] != outputVarUnassigned) {
      firstUnassignedIndex++;
    }

    this.predicateFeatures = predicateFeatureGen.apply(this);
  }
  
  @Override
  public boolean isConsistentWith(FunctionAssignment other) {
    Preconditions.checkState(other instanceof IndexableFunctionAssignment);
    IndexableFunctionAssignment o = (IndexableFunctionAssignment) other;

    int[] otherValues = o.values;    
    Preconditions.checkState(values.length == otherValues.length);
    for (int i = 0; i < values.length; i++) {
      if (values[i] != otherValues[i] && values[i] != outputVarUnassigned
          && otherValues[i] != outputVarUnassigned) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isEqualTo(FunctionAssignment other) {
    Preconditions.checkState(other instanceof IndexableFunctionAssignment);
    IndexableFunctionAssignment o = (IndexableFunctionAssignment) other;

    int[] otherValues = o.values;    
    Preconditions.checkState(values.length == otherValues.length);
    return Arrays.equals(values, otherValues);
  }

  @Override
  public Tensor getFeatureVector() {
    return new DenseTensor(new int[] {0}, new int[] {cachedFeatureVector.length}, cachedFeatureVector);
  }

  @Override
  public Tensor getPredicateFeatureVector() {
    return predicateFeatures;
  }
  
  public FeatureVectorGenerator<FunctionAssignment> getPredicateFeatureGen() {
    return predicateFeatureGen;
  }

  @Override
  public IndexableFunctionAssignment copy() {
    int[] newValues = Arrays.copyOf(values, values.length);
    double[] newFeatureVector = Arrays.copyOf(cachedFeatureVector, cachedFeatureVector.length);

    return new IndexableFunctionAssignment(inputVars, outputVar, outputVarUnassigned,
        assignedOutputValuesList, assignedOutputValuesConsList, sparsity,
        sparsityValueIndex, newValues, firstUnassignedIndex, featureVar,
        elementFeatures, newFeatureVector, predicateFeatureGen, predicateFeatures);
  }

  @Override
  public void copyTo(FunctionAssignment assignment) {
    IndexableFunctionAssignment a = (IndexableFunctionAssignment) assignment;
    
    Preconditions.checkArgument(a.values.length == values.length);
    System.arraycopy(values, 0, a.values, 0, values.length);

    a.firstUnassignedIndex = firstUnassignedIndex;
    
    Preconditions.checkArgument(a.cachedFeatureVector.length == cachedFeatureVector.length);
    System.arraycopy(cachedFeatureVector, 0, a.cachedFeatureVector, 0, cachedFeatureVector.length);

    a.predicateFeatures = predicateFeatures;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }

  private static void updateFeatures(int[] inputKey, int value, Tensor elementFeatures,
      int numInputVars, double[] featureVector, double multiplier) {
    long valueOffset = elementFeatures.getDimensionOffsets()[numInputVars];
    long first = elementFeatures.dimKeyPrefixToKeyNum(inputKey) + (valueOffset * value);
    long last = first + valueOffset;
    
    int index = elementFeatures.getNearestIndex(first);
    int lastIndex = elementFeatures.getNearestIndex(last);
    while (index < lastIndex) {
      long cur = elementFeatures.indexToKeyNum(index);
      double featureValue = elementFeatures.getByIndex(index);
      featureVector[(int) (cur - first)] += featureValue * multiplier;

      // Advance the index
      index++;
    }
  }
}
