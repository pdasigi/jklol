package com.jayantkrish.jklol.tensor;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

/**
 * Hash table representation for storing key/value pairs
 * of a sparse tensor. This representation facilitates fast
 * random access.
 * 
 * @author jayantk
 *
 */
public class SparseTensorHash implements TensorHash {
  private static final long serialVersionUID = 1L;

  private final long[] keys;
  private final double[] values;

  // Number of bins in keys.
  private final int numBins;
  // Number of keys in each bin.
  private final int binSize;
  
  private SparseTensorHash(long[] keys, double[] values, int binSize) {
    this.keys = Preconditions.checkNotNull(keys);
    this.values = Preconditions.checkNotNull(values);
    Preconditions.checkArgument(keys.length == values.length);
    
    this.binSize = binSize;
    Preconditions.checkArgument(keys.length % binSize == 0);
    this.numBins = keys.length / binSize;
  }
  
  public static SparseTensorHash fromTensor(SparseTensor tensor) {
    if (tensor.size() == 0) {
      return new SparseTensorHash(new long[] { -1 }, new double[] { 0 }, 1);
    }
    
    int numBins = tensor.size() * 2;
    int[] keyCounts = new int[numBins];
    Arrays.fill(keyCounts, 0);
    long[] keys = tensor.getKeyNums();
    double[] values = tensor.getValues();
    for (int i = 0; i < keys.length; i++) {
      int hashKey = hash(keys[i], numBins);
      keyCounts[hashKey] += 1;
    }

    int binSize = Ints.max(keyCounts);
    
    long[] hashKeys = new long[numBins * binSize];
    Arrays.fill(hashKeys, -1);
    double[] hashValues = new double[numBins * binSize];
    for (int i = 0; i < keys.length; i++) {
      int hashKey = hash(keys[i], numBins) * binSize;
      while (hashKeys[hashKey] != -1) {
        hashKey++;
      }
      hashKeys[hashKey] = keys[i];
      hashValues[hashKey] = values[i];
    }
    return new SparseTensorHash(hashKeys, hashValues, binSize);
  }

  public double get(long key) {
    int hashKey = hash(key, numBins) * binSize;

    for (int i = hashKey; i < hashKey + binSize; i++) {
      if (keys[i] == key) {
        return values[i];
      }
    }

    // This is a sparse tensor, so keys not in the tensor
    // have value 0
    return 0.0;
  }
  
  private static final int hash(long key, int numBins) {
    return (int) ((key * 31) % numBins); 
  }
}
