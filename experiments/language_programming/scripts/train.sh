#!/bin/bash -e

TRAINING_DATA=experiments/language_programming/data/math.ccg
RULES=experiments/language_programming/grammar/rules.txt

LEXICON=experiments/language_programming/grammar/lexicon.autogenerated.txt

ALIGNMENT_MODEL=alignment_out.ser
MODEL=out.ser

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.AlignmentLexiconInduction --trainingData $TRAINING_DATA --lexiconOutput $LEXICON --modelOutput $ALIGNMENT_MODEL --maxThreads 1 --emIterations 30 --smoothing 0.01 --useCfg --nGramLength 1

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.TrainSemanticParser --trainingData $TRAINING_DATA --lexicon $LEXICON --rules $RULES --skipWords --batchSize 1 --iterations 10 --output $MODEL --logInterval 100 