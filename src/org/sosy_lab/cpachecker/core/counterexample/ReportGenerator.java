package org.sosy_lab.cpachecker.core.counterexample;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.FluentIterable.from;
import static java.util.logging.Level.WARNING;
import static org.sosy_lab.cpachecker.util.AbstractStates.IS_TARGET_STATE;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;

import org.sosy_lab.common.JSON;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.export.DOTBuilder2;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

import javax.annotation.Nullable;

@Options
public class ReportGenerator {

  private static final Splitter LINE_SPLITTER = Splitter.on('\n');
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults();

  private static final Path SCRIPTS = Paths.get("scripts");
  private static final Path HTML_TEMPLATE = SCRIPTS.resolve("report_template.html");

  private final Configuration config;
  private final LogManager logger;

  @Option(
    secure = true,
    name = "analysis.programNames",
    description = "A String, denoting the programs to be analyzed"
  )
  private String programs;

  @Option(secure = true, name = "log.file", description = "name of the log file")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path logFile = Paths.get("CPALog.txt");

  @Option(
    secure = true,
    name = "report.export",
    description = "Generate HTML report with analysis result."
  )
  private boolean generateReport = true;

  @Option(secure = true, name = "report.file", description = "File name for analysis report in case no counterexample was found.")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path reportFile = Paths.get("Report.html");

  @Option(
    secure = true,
    name = "counterexample.export.report",
    description = "File name for analysis report in case a counterexample was found."
  )
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate counterExampleFiles = PathTemplate.ofFormatString("Counterexample.%d.html");

  private final List<String> sourceFiles;

  public ReportGenerator(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    config = checkNotNull(pConfig);
    logger = checkNotNull(pLogger);
    config.inject(this);
    sourceFiles = COMMA_SPLITTER.splitToList(programs);
  }

  public boolean generate(CFA pCfa, UnmodifiableReachedSet pReached, String pStatistics) {
    checkNotNull(pCfa);
    checkNotNull(pReached);
    checkNotNull(pStatistics);

    if (!generateReport) {
      return false;
    }

    Iterable<CounterexampleInfo> counterExamples =
        Optional.presentInstances(
            from(pReached)
                .filter(IS_TARGET_STATE)
                .filter(ARGState.class)
                .transform(new ExtractCounterExampleInfo()));

    if (!counterExamples.iterator().hasNext()) {
      if (reportFile != null) {
        DOTBuilder2 dotBuilder = new DOTBuilder2(pCfa);
        fillOutTemplate(null, reportFile, pCfa, dotBuilder, pStatistics);
        return true;
      } else {
        return false;
      }

    } else if (counterExampleFiles != null) {
      DOTBuilder2 dotBuilder = new DOTBuilder2(pCfa);
      for (CounterexampleInfo counterExample : counterExamples) {
        fillOutTemplate(
            counterExample,
            counterExampleFiles.getPath(counterExample.getUniqueId()),
            pCfa,
            dotBuilder,
            pStatistics);
      }
      return true;
    } else {
      return false;
    }
  }

  private static class ExtractCounterExampleInfo
      implements Function<ARGState, Optional<CounterexampleInfo>> {

    @Override
    public Optional<CounterexampleInfo> apply(ARGState state) {
      return state.getCounterexampleInformation();
    }
  }

  private void fillOutTemplate(
      @Nullable CounterexampleInfo counterExample,
      Path reportPath,
      CFA cfa,
      DOTBuilder2 dotBuilder,
      String statistics) {
    try {
      Files.createParentDirs(reportPath);
    } catch (IOException e) {
      logger.logUserException(WARNING, e, "Could not create report.");
      return;
    }

    try (BufferedReader template =
            new BufferedReader(
                new InputStreamReader(
                    new FileInputStream(HTML_TEMPLATE.toFile()), Charset.defaultCharset()));
        BufferedWriter report =
            new BufferedWriter(
                new OutputStreamWriter(
                    new FileOutputStream(reportPath.toFile()), Charset.defaultCharset()))) {

      String line;
      while (null != (line = template.readLine())) {
        if (line.contains("CONFIGURATION")) {
          insertConfiguration(report);
        } else if (line.contains("STATISTICS")) {
          insertStatistics(report, statistics);
        } else if (line.contains("SOURCE_CONTENT")) {
          insertSources(report);
        } else if (line.contains("LOG")) {
          insertLog(report);
        } else if (line.contains("ERRORPATH") && counterExample != null) {
          insertErrorPathData(counterExample, report);
        } else if (line.contains("FUNCTIONS")) {
          insertFunctionNames(report, cfa);
        } else if (line.contains("SOURCE_FILE_NAMES")) {
          insertSourceFileNames(report);
        } else if (line.contains("COMBINEDNODES")) {
          insertCombinedNodesData(report, dotBuilder);
        } else if (line.contains("CFAINFO")) {
          insertCfaInfoData(report, dotBuilder);
        } else if (line.contains("FCALLEDGES")) {
          insertFCallEdges(report, dotBuilder);
        } else if (line.contains("TITLE")) {
          insertTitle(counterExample, report);
        } else if (line.contains("REPORT_NAME")) {
            insertReportName(counterExample, report);
        } else {
          report.write(line + "\n");
        }
      }

    } catch (IOException e) {
      logger.logUserException(
          WARNING, e, "Could not create report: Procesing of HTML template failed.");
    }
  }

  private void insertReportName(@Nullable CounterexampleInfo counterExample, Writer report) throws IOException {
    if (counterExample == null) {
      report.write("<h3>" + sourceFiles.get(0) +  "</h3>");

    } else {
      String title = String.format(
          "<h3>%s (Counterexample %s)</h3>",
          sourceFiles.get(0),
          counterExample.getUniqueId());
      report.write(title);
    }
  }

  private void insertTitle(@Nullable CounterexampleInfo counterExample, Writer report) throws IOException {
    if (counterExample == null) {
      report.write("<title>CPAchecker Report: " + sourceFiles.get(0) + "</title>");

    } else {
      String title = String.format(
          "<title>CPAchecker Counterexample Report %s: %s</title>",
          sourceFiles.get(0),
          counterExample.getUniqueId());
      report.write(title);
    }
  }

  private void insertStatistics(Writer report, String statistics) throws IOException {
    int iterator = 0;
    for (String line : LINE_SPLITTER.split(statistics)) {
      line = "<pre id=\"statistics-" + iterator + "\">" + line + "</pre>\n";
      report.write(line);
      iterator++;
    }
  }

  private void insertSources(Writer report) throws IOException {
    int index = 0;
    for (String sourceFile : sourceFiles) {
      insertSource(Paths.get(sourceFile), report, index);
      index++;
    }
  }

  private void insertSource(Path sourcePath, Writer report, int sourceFileNumber)
      throws IOException {

    if (sourcePath.exists()) {

      int iterator = 0;
      try (BufferedReader source =
          new BufferedReader(
              new InputStreamReader(
                  new FileInputStream(sourcePath.toFile()), Charset.defaultCharset()))) {

        report.write(
            "<table class=\"sourceContent\" ng-show = \"sourceFileIsSet("
                + sourceFileNumber
                + ")\">\n");

        String line;
        while (null != (line = source.readLine())) {
          line = "<td><pre class=\"prettyprint\">" + line + "  </pre></td>";
          report.write(
              "<tr id=\"source-"
                  + iterator
                  + "\"><td><pre>"
                  + iterator
                  + "</pre></td>"
                  + line
                  + "</tr>\n");
          iterator++;
        }

        report.write("</table>\n");

      } catch (IOException e) {
        logger.logUserException(
            WARNING, e, "Could not create report: Inserting source code failed.");
      }

    } else {
      report.write("<p>No Source-File available</p>");
    }
  }


  private void insertConfiguration(Writer report) throws IOException {

    Iterable<String> lines = LINE_SPLITTER.split(config.asPropertiesString());

    int iterator = 0;
    for (String line : lines) {
      line = "<pre id=\"config-" + iterator + "\">" + line + "</pre>\n";
      report.write(line);
      iterator++;
    }
  }

  private void insertLog(Writer bufferedWriter) throws IOException {
    if (logFile != null && logFile.exists()) {
      try (BufferedReader log =
          new BufferedReader(
              new InputStreamReader(
                  new FileInputStream(logFile.toFile()), Charset.defaultCharset()))) {

        int iterator = 0;
        String line;
        while (null != (line = log.readLine())) {
          line = "<pre id=\"log-" + iterator + "\">" + line + "</pre>\n";
          bufferedWriter.write(line);
          iterator++;
        }

      } catch (IOException e) {
        logger.logUserException(WARNING, e, "Could not create report: Adding log failed.");
      }

    } else {
      bufferedWriter.write("<p>No Log-File available</p>");
    }
  }

  private void insertFCallEdges(Writer report, DOTBuilder2 dotBuilder) throws IOException {
    report.write("var fCallEdges = ");
    dotBuilder.writeFunctionCallEdges(report);
    report.write(";\n");
  }

  private void insertCombinedNodesData(Writer report, DOTBuilder2 dotBuilder) throws IOException {
    report.write("var combinedNodes = ");
    dotBuilder.writeCombinedNodes(report);
    report.write(";\n");
  }

  private void insertCfaInfoData(Writer report, DOTBuilder2 dotBuilder) throws IOException {
    report.write("var cfaInfo = ");
    dotBuilder.writeCfaInfo(report);
    report.write(";\n");
  }

  private void insertErrorPathData(CounterexampleInfo counterExample, Writer report)
      throws IOException {
    report.write("var errorPathData = ");
    counterExample.toJSON(report);
    report.write(";\n");
  }

  private void insertFunctionNames(BufferedWriter report, CFA cfa) {
    try {
      report.write("var functions = ");
      JSON.writeJSONString(cfa.getAllFunctionNames(), report);
      report.write(";\n");

    } catch (IOException e) {
      logger.logUserException(
          WARNING, e, "Could not create report: Insertion of function names failed.");
    }
  }

  private void insertSourceFileNames(Writer report) {
    try{
      report.write("var sourceFiles = ");
      JSON.writeJSONString(sourceFiles, report);
      report.write(";\n");

    } catch (IOException e) {
      logger.logUserException(
          WARNING, e, "Could not create report: Insertion of source file names failed.");
    }
  }
}