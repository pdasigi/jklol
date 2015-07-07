package com.jayantkrish.jklol.ccg.lexicon;

import java.io.Serializable;
import java.util.List;

import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

/**
 * Scoring function for lexicon entries.
 * 
 * @author jayant
 *
 */
public interface LexiconScorer extends Serializable {

  double getCategoryWeight(int spanStart, int spanEnd, AnnotatedSentence sentence,
      List<String> terminalValue, List<String> posTags, CcgCategory category);
}
