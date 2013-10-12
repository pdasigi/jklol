package com.jayantkrish.jklol.ccg.supertag;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cli.TrainCcg;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.pos.WordPrefixSuffixFeatureGenerator;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerators;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.ConvertingLocalContext;
import com.jayantkrish.jklol.sequence.FactorGraphSequenceTagger;
import com.jayantkrish.jklol.sequence.ListTaggedSequence;
import com.jayantkrish.jklol.sequence.LocalContext;
import com.jayantkrish.jklol.sequence.TaggedSequence;
import com.jayantkrish.jklol.sequence.TaggerUtils;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Trains a CCG supertagger. The supertagger takes as input a
 * POS-tagged sentence, and predicts a sequence of CCG syntactic
 * categories.
 * 
 * @author jayantk
 */
public class TrainSupertagger extends AbstractCli {

  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> modelOutput;
  private OptionSpec<String> syntaxMap;

  // Model construction options.
  private OptionSpec<Void> noTransitions;
  private OptionSpec<Void> maxMargin;
  private OptionSpec<Integer> commonWordCountThreshold;
  private OptionSpec<Integer> prefixSuffixFeatureCountThreshold; 

  public TrainSupertagger() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.LBFGS, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();

    syntaxMap = parser.accepts("syntaxMap").withRequiredArg().ofType(String.class);
    noTransitions = parser.accepts("noTransitions");
    maxMargin = parser.accepts("maxMargin");
    commonWordCountThreshold = parser.accepts("commonWordThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(5);
    prefixSuffixFeatureCountThreshold = parser.accepts("commonWordThreshold").withRequiredArg()
        .ofType(Integer.class).defaultsTo(35);
  }

  @Override
  public void run(OptionSet options) {
    // Read in the training data as sentences, to use for
    // feature generation.
    System.out.println("Reading training data...");
    List<CcgExample> ccgExamples = TrainCcg.readTrainingData(options.valueOf(trainingFilename),
        true, true, options.valueOf(syntaxMap));
    System.out.println("Reformatting training data...");
    List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> trainingData =
        reformatTrainingExamples(ccgExamples, true);

    System.out.println("Generating features...");
    FeatureVectorGenerator<LocalContext<WordAndPos>> featureGen =
        buildFeatureVectorGenerator(trainingData, options.valueOf(commonWordCountThreshold),
            options.valueOf(prefixSuffixFeatureCountThreshold));

    Set<HeadedSyntacticCategory> validCategories = Sets.newHashSet();
    for (TaggedSequence<WordAndPos, HeadedSyntacticCategory> trainingDatum : trainingData) {
      validCategories.addAll(trainingDatum.getLabels());
    }

    System.out.println(validCategories.size() + " CCG categories");
    System.out.println(featureGen.getNumberOfFeatures() + " word/CCG category features");

    // Build the factor graph.
    ParametricFactorGraph sequenceModelFamily = TaggerUtils.buildFeaturizedSequenceModel(
        validCategories, featureGen.getFeatureDictionary(), options.has(noTransitions));
    GradientOptimizer trainer = createGradientOptimizer(trainingData.size());
    FactorGraphSequenceTagger<WordAndPos, HeadedSyntacticCategory> tagger = TaggerUtils.trainSequenceModel(
        sequenceModelFamily, trainingData, HeadedSyntacticCategory.class, featureGen, trainer,
        options.has(maxMargin));

    // Save model to disk.
    System.out.println("Serializing trained model...");
    FactorGraphSupertagger supertagger = new FactorGraphSupertagger(tagger.getModelFamily(),
        tagger.getParameters(), tagger.getInstantiatedModel(), tagger.getFeatureGenerator());
    IoUtils.serializeObjectToFile(supertagger, options.valueOf(modelOutput));
  }

  /**
   * Converts {@code ccgExamples} into word sequences tagged with
   * syntactic categories.
   * 
   * @param ccgExamples
   * @return
   */
  public static List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> reformatTrainingExamples(
      Collection<CcgExample> ccgExamples, boolean ignoreInvalid) {
    List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> examples = Lists.newArrayList();
    for (CcgExample example : ccgExamples) {
      Preconditions.checkArgument(example.hasSyntacticParse());
      List<WordAndPos> taggedWords = WordAndPos.createExample(example.getWords(), example.getPosTags());
      List<HeadedSyntacticCategory> syntacticCategories = example.getSyntacticParse().getAllSpannedHeadedSyntacticCategories();

      if (!ignoreInvalid || !syntacticCategories.contains(null)) {
        examples.add(new ListTaggedSequence<WordAndPos, HeadedSyntacticCategory>(taggedWords, syntacticCategories));
      } else {
        List<SyntacticCategory> unheadedCategories = example.getSyntacticParse().getAllSpannedLexiconEntries();
        System.out.println("Discarding sentence: " + taggedWords);
        for (int i = 0; i < taggedWords.size(); i++) {
          if (syntacticCategories.get(i) == null) {
            System.out.println("No headed syntactic category for: " + taggedWords.get(i) + " " + unheadedCategories.get(i));
          }
        }
      }
    }
    return examples;
  }

  private static FeatureVectorGenerator<LocalContext<WordAndPos>> buildFeatureVectorGenerator(
      List<TaggedSequence<WordAndPos, HeadedSyntacticCategory>> trainingData,
      int commonWordCountThreshold, int prefixSuffixCountThreshold) {
    List<LocalContext<WordAndPos>> contexts = TaggerUtils.extractContextsFromData(trainingData);
    CountAccumulator<String> wordCounts = CountAccumulator.create();
    for (LocalContext<WordAndPos> context : contexts) {
      wordCounts.increment(context.getItem().getWord(), 1.0);
    }
    Set<String> commonWords = Sets.newHashSet(wordCounts.getKeysAboveCountThreshold(
        commonWordCountThreshold));

    // Build a dictionary of words and POS tags which occur frequently
    // enough in the data set.
    FeatureGenerator<LocalContext<WordAndPos>, String> wordGen = new
        WordAndPosContextFeatureGenerator(new int[] { -2, -1, 0, 1, 2 }, commonWords);
    CountAccumulator<String> wordPosFeatureCounts = FeatureGenerators.getFeatureCounts(wordGen, contexts);

    // Generate prefix/suffix features for common prefixes and suffixes.
    final WordAndPosToWord wordPosConverter = new WordAndPosToWord();
    FeatureGenerator<LocalContext<WordAndPos>, String> prefixGen = 
        FeatureGenerators.convertingFeatureGenerator(new WordPrefixSuffixFeatureGenerator(4, 4, commonWords),
            new Function<LocalContext<WordAndPos>, LocalContext<String>>() {
          @Override
          public LocalContext<String> apply(LocalContext<WordAndPos> context) {
            return new ConvertingLocalContext<WordAndPos, String>(context, wordPosConverter);
          }
        });

    // Count feature occurrences and discard infrequent features.
    CountAccumulator<String> prefixFeatureCounts = FeatureGenerators.getFeatureCounts(prefixGen, contexts);
    IndexedList<String> featureDictionary = IndexedList.create();
    Set<String> frequentWordFeatures = wordPosFeatureCounts.getKeysAboveCountThreshold(commonWordCountThreshold);
    Set<String> frequentPrefixFeatures = prefixFeatureCounts.getKeysAboveCountThreshold(prefixSuffixCountThreshold);
    featureDictionary.addAll(frequentWordFeatures);
    featureDictionary.addAll(frequentPrefixFeatures);

    System.out.println(frequentWordFeatures.size() + " word and POS features");
    System.out.println(frequentPrefixFeatures.size() + " prefix/suffix features");

    @SuppressWarnings("unchecked")
    FeatureGenerator<LocalContext<WordAndPos>, String> featureGen = FeatureGenerators
          .combinedFeatureGenerator(wordGen, prefixGen);

    return new DictionaryFeatureVectorGenerator<LocalContext<WordAndPos>, String>(
        featureDictionary, featureGen, true);
  }

  public static void main(String[] args) {
    new TrainSupertagger().run(args);
  }

  private static class WordAndPosToWord implements Function<WordAndPos, String> {
    @Override
    public String apply(WordAndPos wordAndPos) {
      return wordAndPos.getWord();
    }
  }
}
