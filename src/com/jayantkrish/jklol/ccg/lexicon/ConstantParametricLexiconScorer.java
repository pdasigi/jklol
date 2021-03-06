package com.jayantkrish.jklol.ccg.lexicon;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

/**
 * Wrapper for including lexicon scorers with no parameters
 * in a CCG parser.
 *  
 * @author jayantk
 *
 */
public class ConstantParametricLexiconScorer implements ParametricLexiconScorer{ 
  private static final long serialVersionUID = 1L;

  private final LexiconScorer scorer;
  
  public ConstantParametricLexiconScorer(LexiconScorer scorer) {
    this.scorer = Preconditions.checkNotNull(scorer);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return ListSufficientStatistics.empty();
  }

  @Override
  public LexiconScorer getModelFromParameters(SufficientStatistics parameters) {
    return scorer;
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return "";
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters,
      int numFeatures) {
    return "";
  }

  @Override
  public void incrementLexiconSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, int spanStart, int spanEnd,
      AnnotatedSentence sentence, CcgCategory lexiconEntryCategory, double count) {
    // Don't need to do anything.
  }
}
