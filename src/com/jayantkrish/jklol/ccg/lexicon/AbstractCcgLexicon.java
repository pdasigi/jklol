package com.jayantkrish.jklol.ccg.lexicon;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.chart.CcgChart;
import com.jayantkrish.jklol.ccg.chart.ChartEntry;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.ListLocalContext;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Implementations of common {@code CcgLexicon} methods.
 * 
 * @author jayant
 *
 */
public abstract class AbstractCcgLexicon implements CcgLexicon {
  private static final long serialVersionUID = 3L;
  
  // Weights and word -> ccg category mappings for the
  // lexicon (terminals in the parse tree).
  private final VariableNumMap terminalVar;

  private final FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator;

  public AbstractCcgLexicon(VariableNumMap terminalVar,
      FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.featureGenerator = featureGenerator;
  }

  @Override
  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }

  @Override
  public List<LexiconEntry> getLexiconEntriesWithUnknown(String word, String posTag) {
    return getLexiconEntriesWithUnknown(Arrays.asList(word), Arrays.asList(posTag));
  }

  @Override
  public List<LexiconEntry> getLexiconEntriesWithUnknown(List<String> originalWords, List<String> posTags) {
    Preconditions.checkArgument(originalWords.size() == posTags.size());
    List<String> words = preprocessInput(originalWords);
    if (terminalVar.isValidOutcomeArray(words)) {
      return getLexiconEntries(words);
    } else if (words.size() == 1) {
      List<String> posTagBackoff = preprocessInput(Arrays.asList(CcgLexicon.UNKNOWN_WORD_PREFIX + posTags.get(0)));
      return getLexiconEntries(posTagBackoff);
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Initializes {@code chart} with entries from the CCG lexicon for
   * {@code input}.
   * 
   * @param input
   * @param chart
   * @param parser
   */
  @Override
  public void initializeChartTerminals(SupertaggedSentence input, CcgChart chart, CcgParser parser) {
    initializeChartWithDistribution(input, chart, getTerminalVar(), true, parser);
  }

  /**
   * This method is a hack.
   * 
   * @param terminals
   * @param posTags
   * @param chart
   * @param terminalVar
   * @param ccgCategoryVar
   * @param terminalDistribution
   * @param useUnknownWords
   * @param parser
   */
  private void initializeChartWithDistribution(SupertaggedSentence input, CcgChart chart,
      VariableNumMap terminalVar, boolean useUnknownWords, CcgParser parser) {
    List<String> terminals = input.getWords();
    List<String> posTags = input.getPosTags();

    List<String> preprocessedTerminals = preprocessInput(terminals);
    List<WordAndPos> ccgWordList = WordAndPos.createExample(terminals, posTags);
    List<Tensor> featureVectors = null;
    if (featureGenerator != null) {
      featureVectors = Lists.newArrayList();
      for (int i = 0; i < preprocessedTerminals.size(); i++) {
        LocalContext<WordAndPos> context = new ListLocalContext<WordAndPos>(ccgWordList, i);
        featureVectors.add(featureGenerator.apply(context));
      }
    }

    for (int i = 0; i < preprocessedTerminals.size(); i++) {
      for (int j = i; j < preprocessedTerminals.size(); j++) {
        List<String> terminalValue = preprocessedTerminals.subList(i, j + 1);
        String posTag = posTags.get(j);
        int numAdded = addChartEntriesForTerminal(terminals, preprocessedTerminals, posTags,
            ccgWordList, featureVectors, terminalValue, posTag, i, j, chart, terminalVar, parser);
        if (numAdded == 0 && i == j && useUnknownWords) {
          // Backoff to POS tags if the input is unknown.
          terminalValue = preprocessInput(Arrays.asList(CcgLexicon.UNKNOWN_WORD_PREFIX + posTags.get(i)));
          addChartEntriesForTerminal(terminals, preprocessedTerminals, posTags, ccgWordList, 
              featureVectors, terminalValue, posTag, i, j, chart, terminalVar, parser);
        }
      }
    }
  }

  private int addChartEntriesForTerminal(List<String> originalTerminals, List<String> preprocessedTerminals,
      List<String> posTags, List<WordAndPos> ccgWordList, List<Tensor> featureVectors,
      List<String> terminalValue, String posTag, int spanStart, int spanEnd, CcgChart chart,
      VariableNumMap terminalVar, CcgParser parser) {
    
    List<LexiconEntry> lexiconEntries = getLexiconEntries(terminalValue);
    int numEntries = 0;
    for (LexiconEntry lexiconEntry : lexiconEntries) {
      CcgCategory category = lexiconEntry.getCategory();

      // Look up how likely this syntactic entry is according to
      // any additional parameters in subclasses.
      double subclassProb = getCategoryWeight(originalTerminals, preprocessedTerminals,
          posTags, ccgWordList, featureVectors, spanStart, spanEnd, terminalValue, category);

      // Add all possible chart entries to the ccg chart.
      ChartEntry entry = parser.ccgCategoryToChartEntry(terminalValue, category, spanStart, spanEnd);
      chart.addChartEntryForSpan(entry, subclassProb, spanStart, spanEnd, parser.getSyntaxVarType());
      numEntries++;
    }
    chart.doneAddingChartEntriesForSpan(spanStart, spanEnd);
    return numEntries;
  }

  /**
   * Gets the possible lexicon entries for {@code wordSequence} from
   * {@code terminalDistribution}, a distribution over CCG categories
   * given word sequences.
   * 
   * @param wordSequence
   * @param terminalDistribution
   * @param terminalVar
   * @param ccgCategoryVar
   * @return
   */
  public static List<LexiconEntry> getLexiconEntriesFromFactor(List<String> wordSequence,
      DiscreteFactor terminalDistribution, VariableNumMap terminalVar, VariableNumMap ccgCategoryVar) {
    List<LexiconEntry> lexiconEntries = Lists.newArrayList();
    if (terminalVar.isValidOutcomeArray(wordSequence)) {
      Assignment assignment = terminalVar.outcomeArrayToAssignment(wordSequence);

      Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
      while (iterator.hasNext()) {
        Outcome bestOutcome = iterator.next();
        CcgCategory ccgCategory = (CcgCategory) bestOutcome.getAssignment().getValue(
            ccgCategoryVar.getOnlyVariableNum());

        lexiconEntries.add(new LexiconEntry(wordSequence, ccgCategory));
      }
    }
    return lexiconEntries;
  }

  protected static List<String> preprocessInput(List<String> terminals) {
    List<String> preprocessedTerminals = Lists.newArrayList();
    for (String terminal : terminals) {
      preprocessedTerminals.add(terminal.toLowerCase());
    }
    return preprocessedTerminals;
  }
}
