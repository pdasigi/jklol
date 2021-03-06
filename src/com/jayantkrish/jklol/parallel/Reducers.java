package com.jayantkrish.jklol.parallel;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

/**
 * Utilities for manipulating {@code Reducer}s and creating common
 * {@code Reducer}s.
 * 
 * @author jayantk
 */
public class Reducers {

  /**
   * Gets a {@code Reducer} which aggregates items of type {@code T} into a
   * collection of type {@code C}.
   * 
   * @param supplier
   * @return
   */
  public static <T, C extends Collection<T>> Reducer<T, C> getAggregatingReducer(
      Supplier<C> supplier) {
    return new AggregatingReducer<T, C>(supplier);
  }
  
  public static <T> Reducer<T, List<T>> getAggregatingListReducer() {
    return getAggregatingReducer(new Supplier<List<T>>() {
      @Override
      public List<T> get() {
        return Lists.newArrayList();
      }
    });
  }

  /**
   * Aggregates items of some type into a {@code Collection}.
   * 
   * @author jayantk
   * @param <T> item type to be aggregated
   * @param <C> collection type storing item
   */
  private static class AggregatingReducer<T, C extends Collection<T>> implements Reducer<T, C> {
    private final Supplier<C> constructor;

    public AggregatingReducer(Supplier<C> constructor) {
      this.constructor = Preconditions.checkNotNull(constructor);
    }

    @Override
    public C getInitialValue() {
      return constructor.get();
    }

    @Override
    public C reduce(T item, C accumulated) {
      accumulated.add(item);
      return accumulated;
    }

    @Override
    public C combine(C other, C accumulated) {
      accumulated.addAll(other);
      return accumulated;
    }
  }
  
  public static class FilterReducer<T> implements Reducer<T, List<T>> {
    private final Predicate<T> predicate;
    
    public FilterReducer(Predicate<T> predicate) {
      this.predicate = Preconditions.checkNotNull(predicate);
    }
    
    @Override
    public List<T> getInitialValue() {
      return Lists.newArrayList();
    }

    @Override
    public List<T> reduce(T item, List<T> accumulated) {
      if (predicate.apply(item)) {
        accumulated.add(item);
      }
      return accumulated;
    }

    @Override
    public List<T> combine(List<T> other, List<T> accumulated) {
      accumulated.addAll(other);
      return accumulated;
    }
  }
}