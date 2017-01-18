package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.ConstraintSet.SubtypeConstraint;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.Scope;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.ScopeSet;

public class TypeInference {

  private final Expression2 expression;
  private final TypeDeclaration typeDeclaration;
  
  private Map<Integer, Type> expressionTypes;
  private ScopeSet scopes;
  private int typeVarCounter = 0;
  private ConstraintSet rootConstraints = null;
  private ConstraintSet solved = null;
  
  public TypeInference(Expression2 expression, TypeDeclaration typeDeclaration) {
    this.expression = Preconditions.checkNotNull(expression);
    this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
  }
  
  public Map<Integer, Type> getExpressionTypes() {
    return expressionTypes;
  }

  public void infer() {
    scopes = StaticAnalysis.getScopes(expression);
    expressionTypes = Maps.newHashMap();
    typeVarCounter = 0;
    for (Scope scope : scopes.getScopes()) {
      for (String variable : scope.getBoundVariables()) {
        int location = scope.getBindingIndex(variable);
        if (!expressionTypes.containsKey(location)) {
          expressionTypes.put(location, getFreshTypeVar());
        }
      }
    }

    rootConstraints = populateExpressionTypes(0);
    // TODO: root type.
    // System.out.println("root");
    // System.out.println(rootConstraints);
    ConstraintSet atomicConstraints = rootConstraints.makeAtomic(typeVarCounter);
    // System.out.println("atomic");
    // System.out.println(atomicConstraints);

    solved = atomicConstraints.solveAtomic(typeDeclaration);
    // System.out.println("solved");
    // System.out.println(solved);

    for (int k : expressionTypes.keySet()) {
      expressionTypes.put(k, expressionTypes.get(k).substitute(solved.getBindings()));
    }
  }

  public ConstraintSet getConstraints() {
    return rootConstraints;
  }

  public ConstraintSet getSolvedConstraints() {
    return solved;
  }

  public Type getFreshTypeVar() {
    return Type.createTypeVariable(typeVarCounter++);
  }
  
  private Type upcast(Type t, List<SubtypeConstraint> constraints, boolean positiveCtx, boolean doTypeVars) {
    if (t.isAtomic()) {
      if (!t.hasTypeVariables() || doTypeVars) {
        Type v = getFreshTypeVar();
        if (positiveCtx) {
          constraints.add(new SubtypeConstraint(t, v));
        } else {
          constraints.add(new SubtypeConstraint(v, t));
        }
        return v;
      } else {
        return t;
      }
    } else {
      Type argType = upcast(t.getArgumentType(), constraints, !positiveCtx, doTypeVars);
      Type retType = upcast(t.getReturnType(), constraints, positiveCtx, doTypeVars);
      return Type.createFunctional(argType, retType, t.acceptsRepeatedArguments());
    }
  }

  private ConstraintSet populateExpressionTypes(int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    if (subexpression.isConstant()) {
      Scope scope = scopes.getScope(index);
      int bindingIndex = scope.getBindingIndex(subexpression.getConstant());
      if (bindingIndex == -1) {
        // Get the type of this constant if it is declared
        // and it's not a lambda variable.
        Type type = typeDeclaration.getType(subexpression.getConstant());
        if (type.hasTypeVariables()) {
          Map<Integer, Type> substitutions = Maps.newHashMap();
          Set<Integer> usedTypeVars = type.getTypeVariables();
          for (int var : usedTypeVars) {
            Type fresh = getFreshTypeVar();
            while (usedTypeVars.contains(fresh.getAtomicTypeVar())) {
              fresh = getFreshTypeVar();
            }
            substitutions.put(var, fresh);
          }
          type = type.substitute(substitutions);
        }
        
        expressionTypes.put(index, type);
        return ConstraintSet.EMPTY;

        /*
        if (expression.getParentExpressionIndex(index) == index - 1) {
          // Subexpression is the first expression in an application.
          // Don't upcast it.
          
        } else {
          List<SubtypeConstraint> constraints = Lists.newArrayList();
          Type supertype = upcast(type, constraints, true, false);
          expressionTypes.put(index, supertype);
          return new ConstraintSet(Lists.newArrayList(), constraints, Maps.newHashMap(), true);
        }
        */
      } else {
        // Get the type variable for this binding and add it here.
        Type boundType = expressionTypes.get(bindingIndex);
        expressionTypes.put(index, boundType);
        return ConstraintSet.EMPTY;
        /*
        List<SubtypeConstraint> constraints = Lists.newArrayList();
        Type supertype = upcast(boundType, constraints, true, true);
        expressionTypes.put(index, supertype);
        ConstraintSet constraintSet = new ConstraintSet(Lists.newArrayList(),
            constraints, Maps.newHashMap(), true);
        // System.out.println(index);
        // System.out.println(constraintSet);
        return constraintSet; 
        */
      }
    } else if (StaticAnalysis.isLambda(subexpression)) {
      int[] childIndexes = expression.getChildIndexes(index);
      int bodyIndex = childIndexes[childIndexes.length - 1];
      int[] argIndexes = expression.getChildIndexes(childIndexes[1]);

      ConstraintSet bodyConstraints = populateExpressionTypes(bodyIndex);
      Type lambdaType = expressionTypes.get(bodyIndex);
      for (int i = argIndexes.length - 1; i >= 0; i--) {
        // Lambda arguments have prepopulated expression types
        // from the initialization.
        lambdaType = lambdaType.addArgument(expressionTypes.get(argIndexes[i]));
      }

      expressionTypes.put(index, lambdaType);
      return bodyConstraints;
    } else {
      // Function application
      int[] childIndexes = expression.getChildIndexes(index);
      int functionIndex = childIndexes[0];

      ConstraintSet constraints = populateExpressionTypes(functionIndex);
      Type functionType = expressionTypes.get(functionIndex);

      Type returnType = getFreshTypeVar();
      List<SubtypeConstraint> subtypeConstraints = Lists.newArrayList();
      Type supertype = returnType;
      for (int i = childIndexes.length - 1; i >= 1; i--) {
        ConstraintSet argConstraints = populateExpressionTypes(childIndexes[i]);
        constraints = constraints.union(argConstraints);
        
        Type argType = expressionTypes.get(childIndexes[i]);
        Type argSupertype = upcast(argType, subtypeConstraints, true, true);
        
        supertype = supertype.addArgument(argSupertype);
      }
      // System.out.println(subexpression);

      // Hack to handle types with vararg parameters.
      if (functionType.acceptsRepeatedArguments()) {
        Type instantiated = functionType.getReturnType();
        for (int i = childIndexes.length - 1; i >= 1; i--) {
          instantiated = instantiated.addArgument(functionType.getArgumentType());
        }
        functionType = instantiated;
        expressionTypes.put(functionIndex, functionType);
      }

      constraints = constraints.addEquality(functionType, supertype);
      constraints = constraints.addAll(subtypeConstraints);
      // constraints = constraints.solveIncremental();
      expressionTypes.put(index, returnType);
      
      // System.out.println(constraints);
      return constraints;
    }
  }
}
