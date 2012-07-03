package com.jayantkrish.jklol.tensor;

import java.util.Arrays;
import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

public class DenseTensorBuilder extends DenseTensorBase implements TensorBuilder {

  /**
   * Creates a {@code DenseTensorBuilder} with all values initialized to 0.
   * 
   * @param dimensions
   * @param sizes
   */
  public DenseTensorBuilder(int[] dimensions, int[] sizes) {
    super(dimensions, sizes);
    // Initialize the values of this builder to 0.
    Arrays.fill(values, 0.0);
  }

  /**
   * Creates a {@code DenseTensorBuilder} with all values initialized to
   * {@code initialValue}.
   * 
   * @param dimensions
   * @param sizes
   */
  public DenseTensorBuilder(int[] dimensions, int[] sizes, double initialValue) {
    super(dimensions, sizes);
    Arrays.fill(values, initialValue);
  }

  /**
   * Copy constructor
   * 
   * @param builder
   */
  public DenseTensorBuilder(DenseTensorBase builder) {
    super(builder.getDimensionNumbers(), builder.getDimensionSizes(),
        Arrays.copyOf(builder.values, builder.values.length));
  }

  @Override
  public void put(int[] key, double value) {
    values[dimKeyToIndex(key)] = value;
  }

  @Override
  public void putByKeyNum(long keyNum, double value) {
    values[(int) keyNum] = value;
  }

  @Override
  public void increment(TensorBase other) {
    incrementWithMultiplier(other, 1.0);
  }

  @Override
  public void increment(double amount) {
    for (int i = 0; i < values.length; i++) {
      values[i] += amount;
    }
  }

  @Override
  public void incrementEntry(double amount, int... key) {
    values[dimKeyToIndex(key)] += amount;
  }

  /**
   * {@inheritDoc}
   * 
   * This implementation supports increments when {@code other} has a subset of
   * {@code this}'s dimensions. In this case, the values in {@code other} are
   * implicitly replicated across all dimensions of {@code this} not present in
   * {@code other}.
   */
  @Override
  public void incrementWithMultiplier(TensorBase other, double multiplier) {
    if (Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers())) {
      simpleIncrement(other, multiplier);
    } else {
      repmatIncrement(other, multiplier);
    }
  }

  /**
   * Increment algorithm for the case where both tensors have the same set of
   * dimensions.
   * 
   * @param other
   * @param multiplier
   */
  private void simpleIncrement(TensorBase other, double multiplier) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);
      for (int i = 0; i < values.length; i++) {
        values[i] += otherTensor.values[i] * multiplier;
      }
    } else {
      Iterator<KeyValue> otherKeyValueIterator = other.keyValueIterator();
      while (otherKeyValueIterator.hasNext()) {
        KeyValue otherKeyValue = otherKeyValueIterator.next();
        values[dimKeyToIndex(otherKeyValue.getKey())] += otherKeyValue.getValue() * multiplier;
      }
    }
  }

  /**
   * Replicates the values in {@code tensor} across all dimensions of
   * {@code this}, incrementing each key in this appropriately. This function is
   * similar to summing two tensors after applying the matlab {@code repmat}
   * function.
   * 
   * @param other
   * @return
   */
  private void repmatIncrement(TensorBase other, double multiplier) {
    // Maps a key of other into a partial key of this.
    int[] dimensionMapping = getDimensionMapping(other.getDimensionNumbers());
    int[] partialKey = Arrays.copyOf(getDimensionSizes(), getDimensionSizes().length);
    for (int i = 0; i < dimensionMapping.length; i++) {
      partialKey[dimensionMapping[i]] = 1;
    }

    int numValues = 1;
    for (int i = 0; i < partialKey.length; i++) {
      numValues *= partialKey[i];
    }
    int[] keyOffsets = new int[numValues];
    Iterator<int[]> myKeyIterator = new IntegerArrayIterator(partialKey, new int[0]);
    int ind = 0;
    while (myKeyIterator.hasNext()) {
      keyOffsets[ind] = dimKeyToIndex(myKeyIterator.next());
      ind++;
    }
    Preconditions.checkState(ind == keyOffsets.length);

    Iterator<KeyValue> otherKeyValues = other.keyValueIterator();
    while (otherKeyValues.hasNext()) {
      KeyValue otherKeyValue = otherKeyValues.next();
      int baseOffset = 0;
      for (int i = 0; i < otherKeyValue.getKey().length; i++) {
        baseOffset += otherKeyValue.getKey()[i] * indexOffsets[dimensionMapping[i]];
      }

      for (int i = 0; i < keyOffsets.length; i++) {
        values[baseOffset + keyOffsets[i]] += otherKeyValue.getValue() * multiplier;
      }
    }
  }

  @Override
  public void multiply(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);
      for (int i = 0; i < values.length; i++) {
        values[i] *= otherTensor.values[i];
      }
    } else {
      Iterator<KeyValue> keyValueIter = keyValueIterator();
      while (keyValueIter.hasNext()) {
        KeyValue keyValue = keyValueIter.next();
        values[dimKeyToIndex(keyValue.getKey())] *= other.getByDimKey(keyValue.getKey());
      }
    }
  }

  @Override
  public void multiply(double amount) {
    for (int i = 0; i < values.length; i++) {
      values[i] *= amount;
    }
  }

  @Override
  public void multiplyEntry(double amount, int... key) {
    values[dimKeyToIndex(key)] *= amount;
  }

  /**
   * Sets each {@code key} in {@code this} to the elementwise maximum of
   * {@code this[key]} and {@code other[key]}.
   * 
   * @param other
   */
  public void maximum(TensorBase other) {
    Preconditions.checkArgument(Arrays.equals(other.getDimensionNumbers(), getDimensionNumbers()));
    if (other instanceof DenseTensorBase) {
      DenseTensorBase otherTensor = (DenseTensorBase) other;
      Preconditions.checkArgument(otherTensor.values.length == values.length);
      for (int i = 0; i < values.length; i++) {
        values[i] = Math.max(otherTensor.values[i], values[i]);
      }
    } else {
      Iterator<KeyValue> keyValueIter = keyValueIterator();
      while (keyValueIter.hasNext()) {
        KeyValue keyValue = keyValueIter.next();
        int index = dimKeyToIndex(keyValue.getKey()); 
        values[index] = Math.max(values[index], other.getByDimKey(keyValue.getKey()));
      }
    }
  }

  @Override
  public void exp() {
    for (int i = 0; i < values.length; i++) {
      values[i] = Math.exp(values[i]);
    }
  }

  @Override
  public DenseTensor build() {
    return new DenseTensor(getDimensionNumbers(), getDimensionSizes(), Arrays.copyOf(values, values.length));
  }

  /**
   * Faster version of {@code build()} that does not copy the values into a new
   * array. Use this method instead of {@code build()} when {@code this} is not
   * modified after the call.
   * 
   * @return
   */
  @Override
  public DenseTensor buildNoCopy() {
    return new DenseTensor(getDimensionNumbers(), getDimensionSizes(), values);
  }

  @Override
  public DenseTensorBuilder getCopy() {
    return new DenseTensorBuilder(this);
  }

  @Override
  public String toString() {
    return Arrays.toString(values);
  }

  // ///////////////////////////////////////////////////////////////////
  // Static Methods
  // ///////////////////////////////////////////////////////////////////

  /**
   * Gets a {@code TensorFactory} which creates {@code DenseTensorBuilder}s.
   * 
   * @return
   */
  public static TensorFactory getFactory() {
    return new TensorFactory() {
      @Override
      public TensorBuilder getBuilder(int[] dimNums, int[] dimSizes) {
        return new DenseTensorBuilder(dimNums, dimSizes);
      }
    };
  }

  /**
   * Gets a builder which contains the same key value pairs as {@code tensor}.
   * 
   * @param tensor
   * @return
   */
  public static DenseTensorBuilder copyOf(TensorBase tensor) {
    DenseTensorBuilder builder = new DenseTensorBuilder(tensor.getDimensionNumbers(),
        tensor.getDimensionSizes());
    Iterator<KeyValue> initialWeightIter = tensor.keyValueIterator();
    while (initialWeightIter.hasNext()) {
      KeyValue keyValue = initialWeightIter.next();
      builder.put(keyValue.getKey(), keyValue.getValue());
    }
    return builder;
  }
}