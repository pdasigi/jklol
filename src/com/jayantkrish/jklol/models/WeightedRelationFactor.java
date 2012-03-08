package com.jayantkrish.jklol.models;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.FactorGraphProtos.FactorProto;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A factor representing a deterministic (functional) relationship between two
 * variables. As with {@link DiscreteObjectFactor}, this factor should only be
 * used when the values of this factor cannot be explicitly enumerated. If the
 * values can be enumerated, then a {@link TableFactor} will be more efficient.
 * 
 * This factor wraps a {@code Function} f. Assignments to this factor (x, y)
 * consistent with the function (i.e., f(x) == y) have probability 1.0, and all
 * other assignments have 0 probability.
 * 
 * @author jayant
 */
public abstract class WeightedRelationFactor extends AbstractFactor {

  private final VariableNumMap domainVariable;
  private final VariableNumMap rangeVariable;
  private final VariableNumMap auxiliaryVariables;
  
  private final Factor domainFactor;
  private final Factor rangeFactor;

  /**
   * {@code domainFactor} may be null, in which case this factor represents a
   * uniform probability distribution over the domain variable's values.
   * 
   * @param domainVariable
   * @param rangeVariable
   * @param function
   * @param domainFactor
   * @param rangeVariableFactory determines the type of factor constructed over
   * the range variable.
   */
  public WeightedRelationFactor(VariableNumMap domainVariable, VariableNumMap rangeVariable,
      VariableNumMap auxiliaryVariables, Factor domainFactor, Factor rangeFactor) {
    super(domainVariable.union(rangeVariable));
    this.domainVariable = Preconditions.checkNotNull(domainVariable);
    this.rangeVariable = Preconditions.checkNotNull(rangeVariable);
    this.auxiliaryVariables = Preconditions.checkNotNull(auxiliaryVariables);
    
    this.domainFactor = domainFactor;
    this.rangeFactor = rangeFactor;
    Preconditions.checkArgument(domainFactor == null || domainFactor.getVars().equals(domainVariable));
    Preconditions.checkArgument(rangeFactor.getVars().equals(rangeVariable));
  }
  
  protected abstract DiscreteObjectFactor constructJointDistribution(Factor domainFactor, 
      Factor rangeFactor);
  
  protected abstract Factor replaceFactors(VariableRelabeling variableRelabeling, 
      Factor newDomainFactor, Factor newRangeFactor);
  
  /*
  private double getFactorProbability(Factor factor, Object value) {
    if (factor == null) {
      return 1.0;
    } else {
      return factor.getUnnormalizedProbability(value);
    }
  }
  */

  @Override
  public double getUnnormalizedProbability(Assignment assignment) {
    throw new UnsupportedOperationException();
    /*
    Object inputValue = assignment.getValue(domainVariable.getOnlyVariableNum());
    Object outputValue = assignment.getValue(rangeVariable.getOnlyVariableNum());
    return function.getWeight(inputValue, outputValue) * getFactorProbability(domainFactor, inputValue) 
        * getFactorProbability(rangeFactor, outputValue);
    */
  }
  
  @Override
  public double getUnnormalizedLogProbability(Assignment assignment) {
    return Math.log(getUnnormalizedProbability(assignment));    
  }

  @Override 
  public Set<SeparatorSet> getComputableOutboundMessages(Map<SeparatorSet, Factor> inboundMessages) {
    Preconditions.checkNotNull(inboundMessages);

    Set<SeparatorSet> possibleOutbound = Sets.newHashSet();
    for (Map.Entry<SeparatorSet, Factor> inboundMessage : inboundMessages.entrySet()) {
      if (inboundMessage.getValue() == null) {
        possibleOutbound.add(inboundMessage.getKey());
      }
    }

    if (possibleOutbound.size() == 1 && domainFactor != null) {
      return possibleOutbound;
    } else if (possibleOutbound.size() == 0) {
      return inboundMessages.keySet();
    } else {
      return Collections.emptySet();
    }
  }

  @Override
  public Factor relabelVariables(VariableRelabeling relabeling) {
    Factor newDomainFactor = null;
    Factor newRangeFactor = null;
    if (domainFactor != null) {
      newDomainFactor = domainFactor.relabelVariables(relabeling);
    }
    if (rangeFactor != null) {
      newRangeFactor = rangeFactor.relabelVariables(relabeling);
    }
    
    return replaceFactors(relabeling, newDomainFactor, newRangeFactor);
  }

  @Override
  public Factor conditional(Assignment assignment) {
    if (!getVars().containsAny(assignment.getVariableNums())) {
      return this;
    }
    
    Factor toReturn = this;
    VariableNumMap toEliminate = getVars().intersection(assignment.getVariableNums());
    if (toEliminate.containsAny(domainVariable)) {
      toReturn = toReturn.product(DiscreteObjectFactor.pointDistribution(domainVariable, 
          assignment.intersection(domainVariable)));
    } else {
      toReturn = toReturn.product(DiscreteObjectFactor.pointDistribution(rangeVariable,
          assignment.intersection(rangeVariable)));
    }
    return toReturn.marginalize(toEliminate);
  }

  @Override
  public Factor marginalize(Collection<Integer> varNumsToEliminate) {
    if (domainFactor == null) {
      // If domainFactor is null, some marginals cannot be computed.
      // Other marginals can only be computed lazily.
      Preconditions.checkArgument(!varNumsToEliminate.contains(domainVariable.getOnlyVariableNum()));
      
      if (getVars().intersection(varNumsToEliminate).size() == 0) {
        return this;
      } else if (varNumsToEliminate.contains(rangeVariable.getOnlyVariableNum())
          && varNumsToEliminate.containsAll(auxiliaryVariables.getVariableNums())) {
        return new FilterFactor(domainVariable, this, rangeFactor, false);        
      } else {
        throw new UnsupportedOperationException();
      }
    } else {
      // Easy case: we've received a message in the domain, so we can construct the
      // joint distribution and everything 
      DiscreteObjectFactor thisAsDiscrete = constructJointDistribution(domainFactor, rangeFactor);
      return thisAsDiscrete.marginalize(varNumsToEliminate);
    }
  }

  @Override
  public Factor maxMarginalize(Collection<Integer> varNumsToEliminate) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  @Override
  public Factor add(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor maximum(Factor other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor product(Factor other) {
    Preconditions.checkArgument(other.getVars().size() == 1);
    if (other.getVars().containsAll(domainVariable)) {
      if (this.domainFactor == null) {
        return replaceFactors(VariableRelabeling.identity(getVars()), other, rangeFactor);
      } else {
        return replaceFactors(VariableRelabeling.identity(getVars()), 
            domainFactor.product(other), rangeFactor);
      }
    } else {
      if (this.rangeFactor == null) {
        return replaceFactors(VariableRelabeling.identity(getVars()), domainFactor, other);
      } else {
        return replaceFactors(VariableRelabeling.identity(getVars()), 
            domainFactor, rangeFactor.product(other));
      }
    }
  }

  @Override
  public Factor product(double constant) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Factor inverse() {
    throw new UnsupportedOperationException();
  }

  @Override
  public double size() {
    return domainFactor != null ? domainFactor.size() : 0;
  }

  @Override
  public Assignment sample() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public List<Assignment> getMostLikelyAssignments(int numAssignments) {
    throw new UnsupportedOperationException("Not implemented");
    /*
    List<Assignment> domainAssignments = domainFactor.getMostLikelyAssignments(numAssignments);
    List<Assignment> mostLikely = Lists.newArrayListWithCapacity(domainAssignments.size());
    for (Assignment domainAssignment : domainAssignments) {
      mostLikely.add(mapDomainAssignmentToFactorAssignment(domainAssignment));
    }
    return mostLikely;
    */
  }
  
  @Override
  public FactorProto toProto() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public String toString() {
    return "WeightedRelationFactor(" + domainVariable.toString() + ", " + rangeVariable.toString() + "):"
        + ((domainFactor != null) ? domainFactor.toString() : "uniform") + ", "
        + ((rangeFactor != null) ? rangeFactor.toString() : "uniform");
  }
}
 