/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cmdline;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import com.google.common.io.CharStreams;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ResourceInfo;
import com.google.common.testing.TestLogHandler;
import com.google.common.truth.Expect;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.configuration.TimeSpanOption;
import org.sosy_lab.common.configuration.converters.FileTypeConverter;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.ConsoleLogFormatter;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.core.CPAchecker;

/**
 * Test that the bundled configuration files are all valid.
 */
@RunWith(Parameterized.class)
public class ConfigurationFileChecks {

  private static final Pattern ALLOWED_WARNINGS =
      Pattern.compile(
          ".*File .* does not exist.*"
              + "|The following configuration options were specified but are not used:.*"
              + "|MathSAT5 is available for research and evaluation purposes only.*"
              + "|Using unsound approximation of (ints with (unbounded integers|rationals))?( and )?(floats with (unbounded integers|rationals))? for encoding program semantics."
              + "|Handling of pointer aliasing is disabled, analysis is unsound if aliased pointers exist."
              + "|Finding target locations was interrupted.*"
              + "|.*One of the parallel analyses has finished successfully, cancelling all other runs.*"
              + "|.*Witness file is missing in specification.*",
          Pattern.DOTALL);

  private static final Pattern PARALLEL_ALGORITHM_ALLOWED_WARNINGS_AFTER_SUCCESS =
      Pattern.compile(
          ".*Skipping one analysis because the configuration file .* could not be read.*",
          Pattern.DOTALL);

  private static final ImmutableList<String> UNUSED_OPTIONS =
      ImmutableList.of(
          // always set by this test
          "java.sourcepath",
          // handled by code outside of CPAchecker class
          "output.disable",
          "limits.time.cpu",
          "limits.time.cpu::required",
          "limits.time.cpu.thread",
          "memorysafety.config",
          "overflow.config",
          "termination.config",
          "witness.validation.violation.config",
          "witness.validation.correctness.config",
          "pcc.proofgen.doPCC",
          "pcc.strategy",
          "pcc.cmc.configFiles",
          "pcc.cmc.file",
          // only handled if specification automaton is additionally specified
          "cpa.automaton.breakOnTargetState",
          "WitnessAutomaton.cpa.automaton.treatErrorsAsTargets",
          "witness.stopNotBreakAtSinkStates",
          // handled by component that is loaded lazily on demand
          "invariantGeneration.config",
          "invariantGeneration.kInduction.async",
          "invariantGeneration.kInduction.guessCandidatesFromCFA",
          "invariantGeneration.kInduction.terminateOnCounterexample",
          // irrelevant if other solver is used
          "solver.z3.requireProofs",
          // present in many config files that explicitly disable counterexample checks
          "counterexample.checker",
          "counterexample.checker.config",
          // present in config files that derive their PCC validation configuration from the
          // analysis configuration
          "ARGCPA.cpa",
          "cegar.refiner",
          "cpa.predicate.refinement.performInitialStaticRefinement",
          // options set with inject(...,...)
          "pcc.proof",
          "pcc.partial.stopAddingAtReachedSetSize");

  @Options
  private static class OptionsWithSpecialHandlingInTest {

    @Option(secure = true, description = "C, Java, or LLVM IR?")
    private Language language = Language.C;

    @Option(
      secure = true,
      name = "analysis.restartAfterUnknown",
      description = "restart the analysis using a different configuration after unknown result"
    )
    private boolean useRestartingAlgorithm = false;

    @Option(
      secure = true,
      name = "analysis.useParallelAnalyses",
      description =
          "Use analyses parallely. The resulting reachedset is the one of the first"
              + " analysis finishing in time. All other analyses are terminated."
    )
    private boolean useParallelAlgorithm = false;

    @Option(secure=true, name="limits.time.cpu::required",
        description="Enforce that the given CPU time limit is set as the value of limits.time.cpu.")
    @TimeSpanOption(codeUnit=TimeUnit.NANOSECONDS,
        defaultUserUnit=TimeUnit.SECONDS,
        min=-1)
    private TimeSpan cpuTimeRequired = TimeSpan.ofNanos(-1);
  }

  private static final Path CONFIG_DIR = Paths.get("config");

  @Parameters(name = "{0}")
  public static Object[] getConfigFiles() throws IOException {
    Stream<URL> configResources =
        ClassPath.from(ConfigurationFileChecks.class.getClassLoader())
            .getResources()
            .stream()
            .filter(resource -> resource.getResourceName().endsWith(".properties"))
            .filter(resource -> resource.getResourceName().contains("cpachecker"))
            .map(ResourceInfo::url);
    try (Stream<Path> configFiles =
        Files.walk(CONFIG_DIR)
            .filter(path -> path.getFileName().toString().endsWith(".properties"))
            .sorted()) {
      return Stream.concat(configResources, configFiles).toArray();
    }
  }

  @Parameter(0)
  public @Nullable Object configFile;

  @Test
  @SuppressWarnings("CheckReturnValue")
  public void parse() throws URISyntaxException {
    try {
      parse(configFile).build();
    } catch (InvalidConfigurationException | IOException e) {
      assert_()
          .fail("Error during parsing of configuration file %s : %s", configFile, e.getMessage());
    }
  }

  private static ConfigurationBuilder parse(Object pConfigFile)
      throws IOException, InvalidConfigurationException, URISyntaxException {
    Path configFile;
    if (pConfigFile instanceof Path) {
      configFile = (Path) pConfigFile;
    } else if (pConfigFile instanceof URL) {
      configFile = Paths.get(((URL) pConfigFile).toURI());
    } else {
      throw new AssertionError("Unexpected config file " + pConfigFile);
    }
    return Configuration.builder().loadFromFile(configFile);
  }

  @Rule public final Expect expect = Expect.create();

  @Test
  public void checkUndesiredOptions() {
    Configuration config;
    try {
      config = parse(configFile).build();
    } catch (InvalidConfigurationException | IOException | URISyntaxException e) {
      assume().fail(e.getMessage());
      throw new AssertionError();
    }
    assume()
        .withMessage("Test configs (which are loaded from URL resources) may contain any option")
        .that(configFile)
        .isNotInstanceOf(URL.class);

    // All the following options encode some information about the program itself,
    // so they need to be specified by the user and may occur only in certain configurations
    // for specific use cases (e.g., SV-COMP).
    // If you add config files for specific use cases (and this is clear from the config's name!),
    // you can whitelist it here.
    // Otherwise consider changing the default value of the option if the value makes sense in
    // general, or remove it from the config file.

    checkOption(config, "analysis.entryFunction");
    checkOption(config, "analysis.programNames");
    checkOption(config, "java.classpath");
    checkOption(config, "java.sourcepath");
    checkOption(config, "java.version");
    checkOption(config, "parser.usePreprocessor");

    if (!configFile.toString().contains("ldv")) {
      // LDV configs are specific to their use case, so these options are allowed

      checkOption(config, "analysis.machineModel");

      if (!configFile.toString().contains("svcomp")) {
        checkOption(config, "cpa.predicate.memoryAllocationsAlwaysSucceed");

        // Should not be changed for SV-COMP configs, but was in 2016 and 2017
        checkOption(config, "cpa.smg.arrayAllocationFunctions");
        checkOption(config, "cpa.smg.deallocationFunctions");
        checkOption(config, "cpa.smg.memoryAllocationFunctions");
        checkOption(config, "cpa.smg.zeroingMemoryAllocation");
      }

      checkOption(config, "cfa.assumeFunctions");
      checkOption(config, "cpa.predicate.memoryAllocationFunctions");
      checkOption(config, "cpa.predicate.memoryAllocationFunctionsWithZeroing");
      checkOption(config, "cpa.predicate.memoryAllocationFunctionsWithSuperfluousParameters");
      checkOption(config, "cpa.predicate.memoryAllocationFunctions");
      checkOption(config, "cpa.predicate.memoryFreeFunctionName");
      checkOption(config, "cpa.predicate.nondetFunctionsRegexp");
      checkOption(config, "cpa.smg.externalAllocationFunction");
    }
  }

  @SuppressWarnings("deprecation") // for tests this usage is ok
  private void checkOption(Configuration config, String option) {
    if (config.hasProperty(option)) {
      expect.fail(
          "Configuration has value for option %s with value '%s', which should usually not be present in config files",
          option, config.getProperty(option));
    }
  }

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  @BeforeClass
  public static void createDummyInputFiles() throws IOException {
    // Create files that some analyses expect as input files.
    try (Reader r =
            Files.newBufferedReader(Paths.get("test/config/automata/AssumptionAutomaton.spc"));
        Writer w =
            IO.openOutputFile(
                Paths.get("output/AssumptionAutomaton.txt"), StandardCharsets.UTF_8)) {
      CharStreams.copy(r, w);
    }
  }

  @Before
  public void createDummyInputAutomatonFiles() throws IOException {
    // Create files that some analyses expect as input files.

    try (Reader r =
            Files.newBufferedReader(Paths.get("config/specification/AssumptionGuidingAutomaton.spc"));
        Writer w =
            IO.openOutputFile(
                Paths.get(tempFolder.newFolder("config").getAbsolutePath()+"/specification/AssumptionGuidingAutomaton.spc"), StandardCharsets.UTF_8)) {
      CharStreams.copy(r, w);
    }
    try (Reader r =
        Files.newBufferedReader(Paths.get("test/config/automata/AssumptionAutomaton.spc"));
        Writer w =
            IO.openOutputFile(
                Paths.get(tempFolder.newFolder("output").getAbsolutePath()+"/AssumptionAutomaton.txt"), StandardCharsets.UTF_8)) {
      CharStreams.copy(r, w);
    }
  }

  @Test
  public void instantiate_and_run() throws IOException, InvalidConfigurationException {
    // exclude files not meant to be instantiated
    if (configFile instanceof Path) {
      assume()
          .that((Iterable<?>) configFile)
          .containsNoneOf(Paths.get("includes"), Paths.get("pcc"));
    }

    final OptionsWithSpecialHandlingInTest options = new OptionsWithSpecialHandlingInTest();
    Configuration config = createConfigurationForTestInstantiation();
    config.inject(options);
    if (options.cpuTimeRequired.compareTo(TimeSpan.empty()) >= 0) {
      ConfigurationBuilder configBuilder = Configuration.builder().copyFrom(config);
      configBuilder.setOption("limits.time.cpu", options.cpuTimeRequired.toString());
      configBuilder.copyOptionFromIfPresent(config, "limits.time.cpu");
      config = configBuilder.build();
    }
    final boolean isJava = options.language == Language.JAVA;

    final TestLogHandler logHandler = new TestLogHandler();
    logHandler.setLevel(Level.ALL);
    final LogManager logger = BasicLogManager.createWithHandler(logHandler);

    final CPAchecker cpachecker;
    try {
      cpachecker = new CPAchecker(config, logger, ShutdownManager.create());
    } catch (InvalidConfigurationException e) {
      assert_()
          .fail("Invalid configuration in configuration file %s : %s", configFile, e.getMessage());
      return;
    }

    try {
      cpachecker.run(ImmutableList.of(createEmptyProgram(isJava)), ImmutableSet.of());
    } catch (IllegalArgumentException e) {
      if (isJava) {
        assume().fail("Java frontend has a bug and cannot be run twice");
      }
      throw e;
    } catch (UnsatisfiedLinkError e) {
      assume().fail(e.getMessage());
      return;
    }

    Stream<String> severeMessages = getSevereMessages(options, logHandler);

    if (severeMessages.count() > 0) {
      assert_()
          .fail(
              "Not true that log for config %s does not contain messages with level WARNING or higher:\n%s",
              configFile,
              logHandler
                  .getStoredLogRecords()
                  .stream()
                  .map(ConsoleLogFormatter.withoutColors()::format)
                  .collect(Collectors.joining())
                  .trim());
    }

    if (!(options.useParallelAlgorithm || options.useRestartingAlgorithm)) {
      // TODO find a solution how to check for unused properties correctly even with RestartAlgorithm
      Set<String> unusedOptions = new TreeSet<>(config.getUnusedProperties());
      unusedOptions.removeAll(UNUSED_OPTIONS);
      assertThat(unusedOptions).named("unused options specified in " + configFile).isEmpty();
    }
  }

  private Configuration createConfigurationForTestInstantiation() {
    try {
      FileTypeConverter fileTypeConverter =
          FileTypeConverter.create(
              Configuration.builder()
                  .setOption("rootDirectory", tempFolder.getRoot().toString())
                  .build());
      Configuration.getDefaultConverters().put(FileOption.class, fileTypeConverter);

      return parse(configFile)
          .addConverter(FileOption.class, fileTypeConverter)
          .setOption("java.sourcepath", tempFolder.getRoot().toString())
          .build();
    } catch (InvalidConfigurationException | IOException | URISyntaxException e) {
      assume().fail(e.getMessage());
      throw new AssertionError();
    }
  }

  private String createEmptyProgram(boolean isJava) throws IOException {
    String program;
    if (isJava) {
      IO.writeFile(
          tempFolder.newFile("Main.java").toPath(),
          StandardCharsets.US_ASCII,
          "public class Main { public static void main(String... args) {} }");
      program = "Main";
    } else {
      File cFile = tempFolder.newFile("program.i");
      IO.writeFile(cFile.toPath(), StandardCharsets.US_ASCII, "void main() {}");
      program = cFile.toString();
    }
    return program;
  }

  private static Stream<String> getSevereMessages(OptionsWithSpecialHandlingInTest pOptions, final TestLogHandler pLogHandler) {
    // After one component of a parallel algorithm finishes successfully,
    // other components are interrupted, potentially causing warnings that can be ignored.
    // One such example is if another component uses a RestartAlgorithm that is interrupted
    // during the parsing of configuration files
    Stream<LogRecord> logRecords = pLogHandler.getStoredLogRecords().stream();
    if (pOptions.useParallelAlgorithm) {
      Iterator<LogRecord> logRecordIterator = new Iterator<LogRecord>() {

        private Iterator<LogRecord> underlyingIterator = pLogHandler.getStoredLogRecords().iterator();

        private boolean oneComponentSuccessful = false;

        @Override
        public boolean hasNext() {
          return underlyingIterator.hasNext();
        }

        @Override
        public LogRecord next() {
          LogRecord result = underlyingIterator.next();
          if (!oneComponentSuccessful && result.getLevel() == Level.INFO ) {
            if (result.getMessage().endsWith("finished successfully.")) {
              oneComponentSuccessful = true;
              underlyingIterator = Iterators.filter(
                  underlyingIterator,
                  r -> r.getLevel() != Level.WARNING
                    || !PARALLEL_ALGORITHM_ALLOWED_WARNINGS_AFTER_SUCCESS.matcher(r.getMessage()).matches());
            }
          }
          return result;
        }

      };
      logRecords = Streams.stream(logRecordIterator);
    }
    return logRecords
            .filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
            .map(LogRecord::getMessage)
            .filter(s -> !ALLOWED_WARNINGS.matcher(s).matches());
  }
}
