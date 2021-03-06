package com.jayantkrish.jklol.experiments.geoquery;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.CcgInference;
import com.jayantkrish.jklol.ccg.CcgLoglikelihoodOracle;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.cli.TrainSemanticParser;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionComparator;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.SimplificationComparator;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.ccg.lexicon.StringContext;
import com.jayantkrish.jklol.ccg.lexinduct.AlignmentExample;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentEmOracle;
import com.jayantkrish.jklol.ccg.lexinduct.CfgAlignmentModel;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree;
import com.jayantkrish.jklol.ccg.lexinduct.ParametricCfgAlignmentModel;
import com.jayantkrish.jklol.ccg.util.SemanticParserExampleLoss;
import com.jayantkrish.jklol.ccg.util.SemanticParserUtils;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.ExpectationMaximization;
import com.jayantkrish.jklol.training.GradientOptimizer;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.Lbfgs;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.CountAccumulator;
import com.jayantkrish.jklol.util.IoUtils;
import com.jayantkrish.jklol.util.PairCountAccumulator;

/**
 * A Geoquery semantic parsing experiment. This experiment
 * learns a CCG lexicon, uses it to train a CCG semantic
 * parser, then makes predictions on a training and test set.
 * The lexicon, parser, and predictions are all output to 
 * disk for later analysis.
 *   
 * @author jayantk
 *
 */
public class GeoqueryInduceLexicon extends AbstractCli {

  private OptionSpec<String> trainingDataFolds;
  private OptionSpec<String> outputDir;
  
  private OptionSpec<String> foldNameOpt;
  private OptionSpec<Void> testOpt;
  
  // Configuration for the alignment model
  private OptionSpec<Integer> emIterations;
  private OptionSpec<Double> smoothingParam;
  private OptionSpec<Integer> nGramLength;
  private OptionSpec<Integer> lexiconNumParses;
  private OptionSpec<Void> loglinear;
  
  // Configuration for the semantic parser.
  private OptionSpec<Integer> parserIterations;
  private OptionSpec<Integer> beamSize;
  private OptionSpec<Double> l2Regularization;
  private OptionSpec<String> additionalLexicon;
  
  public GeoqueryInduceLexicon() {
    super(CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    // Required arguments.
    trainingDataFolds = parser.accepts("trainingDataFolds").withRequiredArg().ofType(String.class).required();
    outputDir = parser.accepts("outputDir").withRequiredArg().ofType(String.class).required();
    
    // Optional option to only run one fold
    foldNameOpt = parser.accepts("foldName").withRequiredArg().ofType(String.class);
    testOpt = parser.accepts("test");

    // Optional arguments
    emIterations = parser.accepts("emIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    smoothingParam = parser.accepts("smoothing").withRequiredArg().ofType(Double.class).defaultsTo(0.01);
    nGramLength = parser.accepts("nGramLength").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    lexiconNumParses = parser.accepts("lexiconNumParses").withRequiredArg().ofType(Integer.class).defaultsTo(-1);
    loglinear = parser.accepts("loglinear");
    
    parserIterations = parser.accepts("parserIterations").withRequiredArg().ofType(Integer.class).defaultsTo(10);
    beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    l2Regularization = parser.accepts("l2Regularization").withRequiredArg().ofType(Double.class).defaultsTo(0.0);
    additionalLexicon = parser.accepts("additionalLexicon").withRequiredArg().ofType(String.class);
  }

  @Override
  public void run(OptionSet options) {
    TypeDeclaration typeDeclaration = GeoqueryUtil.getTypeDeclaration();
    
    List<String> foldNames = Lists.newArrayList();
    List<List<AlignmentExample>> folds = Lists.newArrayList();
    System.out.println("Reading data...");
    readFolds(options.valueOf(trainingDataFolds), foldNames, folds, options.has(testOpt), typeDeclaration);
    System.out.println("\n");
    
    // Read in additional lexicon entries for training the parser.
    // These entries are entity names included with the data set.
    List<String> additionalLexiconEntries = IoUtils.readLines(options.valueOf(additionalLexicon));

    List<String> foldsToRun = Lists.newArrayList();
    if (options.has(foldNameOpt)) {
      foldsToRun.add(options.valueOf(foldNameOpt));
      System.out.println("Running fold: " + options.valueOf(foldNameOpt));
    } else {
      foldsToRun.addAll(foldNames);
    }

    for (String foldName : foldsToRun) {
      int foldIndex = foldNames.indexOf(foldName);

      List<AlignmentExample> heldOut = folds.get(foldIndex);
      List<AlignmentExample> trainingData = Lists.newArrayList();
      for (int j = 0; j < folds.size(); j++) {
        if (j == foldIndex) {
          continue;
        }
        trainingData.addAll(folds.get(j));
      }

      String outputDirString = options.valueOf(outputDir);
      String lexiconOutputFilename = outputDirString + "/lexicon." + foldName + ".txt";
      String alignmentModelOutputFilename = outputDirString + "/alignment." + foldName + ".ser";
      String parserModelOutputFilename = outputDirString + "/parser." + foldName + ".ser";

      String trainingErrorOutputFilename = outputDirString + "/training_error." + foldName + ".json";
      String testErrorOutputFilename = outputDirString + "/test_error." + foldName + ".json";

      runFold(trainingData, heldOut, typeDeclaration, options.valueOf(emIterations),
          options.valueOf(smoothingParam), options.valueOf(nGramLength), options.valueOf(lexiconNumParses),
          options.has(loglinear), options.valueOf(parserIterations), options.valueOf(l2Regularization),
          options.valueOf(beamSize), additionalLexiconEntries, lexiconOutputFilename,
          trainingErrorOutputFilename, testErrorOutputFilename, alignmentModelOutputFilename,
          parserModelOutputFilename);
    }
  }
  
  private static void runFold(List<AlignmentExample> trainingData, List<AlignmentExample> testData,
      TypeDeclaration typeDeclaration, int emIterations, double smoothingAmount, int nGramLength, int lexiconNumParses,
      boolean loglinear, int parserIterations, double l2Regularization, int beamSize, List<String> additionalLexiconEntries,
      String lexiconOutputFilename, String trainingErrorOutputFilename, String testErrorOutputFilename,
      String alignmentModelOutputFilename, String parserModelOutputFilename) {

    // Treat the additional lexicon entries as entity names. These
    // names can be generated as bigrams by the lexicon learning
    // model.
    Set<List<String>> entityNames = Sets.newHashSet();
    for (LexiconEntry lexiconEntry : LexiconEntry.parseLexiconEntries(additionalLexiconEntries)) {
      entityNames.add(lexiconEntry.getWords());
    }

    // Train the lexicon learning model and generate
    // lexicon entries. The method returns counts
    // for each lexicon entry.
    PairCountAccumulator<List<String>, LexiconEntry> alignments = trainAlignmentModel(trainingData,
        entityNames, typeDeclaration, smoothingAmount, emIterations, nGramLength, lexiconNumParses, loglinear, false);
    
    CountAccumulator<String> wordCounts = CountAccumulator.create();
    for (AlignmentExample trainingExample : trainingData) {
      wordCounts.incrementByOne(trainingExample.getWords());
    }

    // Log the generated lexicon.
    Collection<LexiconEntry> allEntries = alignments.getKeyValueMultimap().values();
    List<String> lexiconEntryLines = Lists.newArrayList();
    lexiconEntryLines.addAll(additionalLexiconEntries);
    for (LexiconEntry lexiconEntry : allEntries) {
      Expression2 lf = lexiconEntry.getCategory().getLogicalForm();
      if (lf.isConstant()) {
        // Add an additional head to entity lexicon entries to 
        // create a type-based backoff dependency feature. 
        String type = lf.getConstant().split(":")[1];
        String newHead = "entity:" + type;
        String oldHeadString = "\"0 " + lexiconEntry.getCategory().getSemanticHeads().get(0) + "\"";
        String newHeadString = "\"0 " + newHead + "\",\"0 " + lf.getConstant() + "\"";
        String lexString = lexiconEntry.toCsvString().replace(oldHeadString, newHeadString);
        lexiconEntryLines.add(lexString);
      } else {
        lexiconEntryLines.add(lexiconEntry.toCsvString());
      }
    }

    Collections.sort(lexiconEntryLines);
    IoUtils.writeLines(lexiconOutputFilename, lexiconEntryLines);
    // IoUtils.serializeObjectToFile(model, alignmentModelOutputFilename);
    
    // Reformat the data to a format suitable for the CCG parser.
    List<CcgExample> ccgTrainingExamples = alignmentExamplesToCcgExamples(trainingData);

    // The parser uses string context features from adjacent words.
    // Add an annotation to each question with the string context
    // feature vectors generated by featureGen.
    List<StringContext> contexts = StringContext.getContextsFromExamples(ccgTrainingExamples);
    FeatureVectorGenerator<StringContext> featureGen = DictionaryFeatureVectorGenerator
        .createFromData(contexts, new GeoqueryFeatureGenerator(), true);
    ccgTrainingExamples = SemanticParserUtils.annotateFeatures(ccgTrainingExamples, featureGen,
        GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    
    // A CcgFeatureFactory defines the set of features used in
    // the CCG parser. Many different kinds of features can be
    // included, such as lexicon entry features, dependency 
    // features, and dependency distance features.
    CcgFeatureFactory featureFactory = new GeoqueryFeatureFactory(true, true, false, false,
        GeoqueryUtil.FEATURE_ANNOTATION_NAME, featureGen.getFeatureDictionary(),
        LexiconEntry.parseLexiconEntries(additionalLexiconEntries));

    // The parser is trained by comparing predicted logical forms
    // with the labeled logical forms. ExpressionComparator 
    // implements this comparison, which in this case is done by
    // simplifying both logical forms to a canonical representation
    // then comparing equality.
    ExpressionSimplifier simplifier = GeoqueryUtil.getExpressionSimplifier();
    ExpressionComparator comparator = new SimplificationComparator(simplifier);

    // Inference algorithm for training the parser. This is CKY-style
    // chart parsing with a beam search.
    CcgInference inferenceAlgorithm = new CcgCkyInference(null, beamSize,
        -1, Integer.MAX_VALUE, Runtime.getRuntime().availableProcessors());

    // The parser can use lexicon entries for unknown words and 
    // also arbitrary binary and unary rules. The set of rules can't be
    // empty, so create a dummy rule.
    List<String> unknownLexiconEntryLines = Lists.newArrayList();
    List<String> ruleEntries = Arrays.asList("\"DUMMY{0} DUMMY{0}\",\"(lambda ($L) $L)\"");
    
    // Train the parser
    System.out.println("\nTraining CCG parser.");
    CcgParser ccgParser = trainSemanticParser(ccgTrainingExamples, lexiconEntryLines,
        unknownLexiconEntryLines, ruleEntries, featureFactory, inferenceAlgorithm, comparator,
        parserIterations, l2Regularization);

    // Save the trained parser and feature generator to disk.
    GeoqueryModel model = new GeoqueryModel(ccgParser, featureGen);
    IoUtils.serializeObjectToFile(model, parserModelOutputFilename);
    
    // Make training and test predictions and store them to
    // disk as json files.
    List<SemanticParserExampleLoss> trainingExampleLosses = Lists.newArrayList();    
    SemanticParserUtils.testSemanticParser(ccgTrainingExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, trainingExampleLosses, false);
    SemanticParserExampleLoss.writeJsonToFile(trainingErrorOutputFilename, trainingExampleLosses);

    List<CcgExample> ccgTestExamples = alignmentExamplesToCcgExamples(testData);
    ccgTestExamples = SemanticParserUtils.annotateFeatures(ccgTestExamples, featureGen, GeoqueryUtil.FEATURE_ANNOTATION_NAME);
    List<SemanticParserExampleLoss> testExampleLosses = Lists.newArrayList();    
    SemanticParserUtils.testSemanticParser(ccgTestExamples, ccgParser,
        inferenceAlgorithm, simplifier, comparator, testExampleLosses, false);
    SemanticParserExampleLoss.writeJsonToFile(testErrorOutputFilename, testExampleLosses);
  }

  public static PairCountAccumulator<List<String>, LexiconEntry> trainAlignmentModel(
      List<AlignmentExample> trainingData, Set<List<String>> entityNames, TypeDeclaration typeDeclaration,
      double smoothingAmount, int emIterations, int nGramLength, int lexiconNumParses, 
      boolean loglinear, boolean convex) {
    // Add all unigrams to the model.
    Set<List<String>> terminalVarValues = Sets.newHashSet();
    for (AlignmentExample example : trainingData) {
      terminalVarValues.addAll(example.getNGrams(1));
    }
    
    // Add any entity names that appear in the training set.
    Set<List<String>> attestedEntityNames = Sets.newHashSet();
    for (AlignmentExample example : trainingData) {
      attestedEntityNames.addAll(example.getNGrams(example.getWords().size()));
    }
    attestedEntityNames.retainAll(entityNames);
    terminalVarValues.addAll(attestedEntityNames);

    ParametricCfgAlignmentModel pam = ParametricCfgAlignmentModel.buildAlignmentModel(
        trainingData, terminalVarValues, typeDeclaration, loglinear);
    SufficientStatistics smoothing = pam.getNewSufficientStatistics();
    smoothing.increment(smoothingAmount);

    SufficientStatistics initial = pam.getNewSufficientStatistics();
    // If running the loglinear model, create a gradient optimizer  
    // to use in the M-step to re-estimate the loglinear model parameters.
    // If not running the loglinear model, initialize all model
    // parameters to the uniform distribution.
    GradientOptimizer optimizer = null;
    if (!loglinear) {
      initial.increment(1);
    } else {
      int numIterations = 1000;
      optimizer = new Lbfgs(numIterations, 10, 1e-6, new NullLogFunction());
    }

    System.out.println("\nTraining lexicon learning model with Expectation Maximization...");
    // Train the alignment model with EM.
    ExpectationMaximization em = new ExpectationMaximization(emIterations, new DefaultLogFunction(1, false));
    // Train a convex model.
    SufficientStatistics trainedParameters = em.train(new CfgAlignmentEmOracle(pam, smoothing, optimizer, true),
        initial, trainingData);

    if (!convex) {
      System.out.println("\nRetraining lexicon learning model with nonconvex objective");
      // Train a nonconvex model initializing the parameters using the convex model.
      trainedParameters = em.train(new CfgAlignmentEmOracle(pam, smoothing, optimizer, convex),
          trainedParameters, trainingData);
    }
    CfgAlignmentModel model = pam.getModelFromParameters(trainedParameters);

    return model.generateLexicon(trainingData, lexiconNumParses, typeDeclaration);
  }

  public static CcgParser trainSemanticParser(List<CcgExample> trainingExamples,
      List<String> lexiconEntryLines, List<String> unknownLexiconEntryLines,
      List<String> ruleEntries, CcgFeatureFactory featureFactory,
      CcgInference inferenceAlgorithm, ExpressionComparator comparator,
      int iterations, double l2Penalty) {
    // A ParametricCcgParser represents a parametric family of CCG parsers,
    // i.e., a function from parameter vectors to CCG parsers. This class
    // is instantiated by providing a lexicon, additional parser rules,
    // and a featurization. Additional options control the default combinators
    // included in the parser.
    ParametricCcgParser family = ParametricCcgParser.parseFromLexicon(lexiconEntryLines,
        unknownLexiconEntryLines, ruleEntries, featureFactory,
        CcgExample.getPosTagVocabulary(trainingExamples), true, null, true);

    // Train the parser by optimizing the loglikelihood of predicting a
    // logical form that is correct according to comparator. 
    GradientOracle<CcgParser, CcgExample> oracle = new CcgLoglikelihoodOracle(family, comparator,
        inferenceAlgorithm);

    // Optimize loglikelihood with stochastic gradient descent.
    int numIterations = trainingExamples.size() * iterations;
    GradientOptimizer trainer = StochasticGradientTrainer.createWithL2Regularization(numIterations, 1,
        1.0, true, true, Double.MAX_VALUE, l2Penalty, new DefaultLogFunction(100, false));
    SufficientStatistics parameters = trainer.train(oracle, oracle.initializeGradient(),
        trainingExamples);

    // Optionally, log the trained parameters.
    // System.out.println("final parameters:");
    // System.out.println(family.getParameterDescription(parameters));
    
    return family.getModelFromParameters(parameters);
  }

  public static List<CcgExample> alignmentExamplesToCcgExamples(
      List<AlignmentExample> alignmentExamples) {
    // Convert data to CCG training data.
    List<CcgExample> ccgExamples = Lists.newArrayList();
    for (AlignmentExample example : alignmentExamples) {
      List<String> words = example.getWords();
      List<String> posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
      AnnotatedSentence supertaggedSentence = new AnnotatedSentence(words, posTags);

      ccgExamples.add(new CcgExample(supertaggedSentence, null, null,
          example.getTree().getExpression()));
    }
    return ccgExamples;
  }

  public static void readFolds(String foldDir, List<String> foldNames, List<List<AlignmentExample>> folds,
      boolean test, TypeDeclaration typeDeclaration) {
    File dir = new File(foldDir);
    File[] files = dir.listFiles();
    
    for (int i = 0; i < files.length; i++) {
      String name = files[i].getName();
      if (!test && name.startsWith("fold")) {
        foldNames.add(name);
        
        List<AlignmentExample> foldData = readTrainingData(files[i].getAbsolutePath(), typeDeclaration);
        folds.add(foldData);
      } else if (test && (name.startsWith("all_folds") || name.startsWith("test"))) {
        foldNames.add(name);
        
        List<AlignmentExample> foldData = readTrainingData(files[i].getAbsolutePath(), typeDeclaration);
        folds.add(foldData);
      }
    }
  }

  public static List<AlignmentExample> readTrainingData(String trainingDataFile, TypeDeclaration typeDeclaration) {
    List<CcgExample> ccgExamples = TrainSemanticParser.readCcgExamples(trainingDataFile);
    List<AlignmentExample> examples = Lists.newArrayList();

    ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("and:<t*,t>")));
    Set<String> constantsToIgnore = Sets.newHashSet("and:<t*,t>");

    System.out.println("  " + trainingDataFile);
    int totalTreeSize = 0; 
    for (CcgExample ccgExample : ccgExamples) {
      ExpressionTree tree = ExpressionTree.fromExpression(ccgExample.getLogicalForm(),
          simplifier, typeDeclaration, constantsToIgnore, 0, 2, 3);
      examples.add(new AlignmentExample(ccgExample.getSentence().getWords(), tree));
      totalTreeSize += tree.size();
    }
    // System.out.println("  average tree size: " + (totalTreeSize / examples.size()));
    return examples;
  }

  public static void main(String[] args) {
    new GeoqueryInduceLexicon().run(args);
  }
}
