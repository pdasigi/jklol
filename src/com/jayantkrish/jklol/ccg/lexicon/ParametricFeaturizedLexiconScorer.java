package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.models.ClassifierFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricFeaturizedLexiconScorer implements ParametricLexiconScorer {
  private static final long serialVersionUID = 2L;

  private final String featureVectorAnnotationName;
  private final VariableNumMap syntaxVar;
  private final VariableNumMap featureVectorVar;
  private final ParametricLinearClassifierFactor classifierFamily;

  /**
   * Names of the parameter vectors governing each factor in the lexicon
   * entries.
   */
  public static final String TERMINAL_PARAMETERS = "terminals";
  public static final String TERMINAL_FEATURE_PARAMETERS = "terminalFeatures";
  
  public static final String TERMINAL_FEATURE_VAR_NAME = "terminalFeaturesVar";

  public ParametricFeaturizedLexiconScorer(String featureVectorAnnotationName,
      VariableNumMap syntaxVar, VariableNumMap featureVectorVar,
      ParametricLinearClassifierFactor classifierFamily) {
    this.featureVectorAnnotationName = Preconditions.checkNotNull(featureVectorAnnotationName);
    this.syntaxVar = Preconditions.checkNotNull(syntaxVar);
    this.featureVectorVar = Preconditions.checkNotNull(featureVectorVar);
    this.classifierFamily = Preconditions.checkNotNull(classifierFamily);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return classifierFamily.getNewSufficientStatistics();
  }

  @Override
  public FeaturizedLexiconScorer getModelFromParameters(SufficientStatistics parameters) {
    ClassifierFactor classifier = classifierFamily.getModelFromParameters(parameters);

   return new FeaturizedLexiconScorer(featureVectorAnnotationName, syntaxVar,
       featureVectorVar, classifier);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return classifierFamily.getParameterDescription(parameters, numFeatures);
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      AnnotatedSentence sentence, CcgCategory category, double count) {

    SpanFeatureAnnotation annotation = (SpanFeatureAnnotation) sentence
        .getAnnotation(featureVectorAnnotationName);
    Tensor featureVector = annotation.getFeatureVector(spanStart, spanEnd);
    HeadedSyntacticCategory syntax = category.getSyntax();
    
    Assignment assignment = syntaxVar.outcomeArrayToAssignment(syntax)
        .union(featureVectorVar.outcomeArrayToAssignment(featureVector));

    classifierFamily.incrementSufficientStatisticsFromAssignment(gradient, currentParameters,
        assignment, count);
  }
}
