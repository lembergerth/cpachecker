/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.util.faultLocalization.invariants;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.counterexample.CFAEdgeWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.CFAPathWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.faultLocalization.ErrorCause;
import org.sosy_lab.cpachecker.util.faultLocalization.FaultLocator;
import org.sosy_lab.cpachecker.util.predicates.interpolation.CounterexampleTraceInfo;
import org.sosy_lab.cpachecker.util.predicates.interpolation.InterpolationManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaConverter;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaTypeHandler;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.FormulaEncodingOptions;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;
import org.sosy_lab.cpachecker.util.variableclassification.VariableClassification;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Formula;
import org.sosy_lab.java_smt.api.InterpolatingProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

/**
 * {@link FaultLocator} based on Error Invariants.
 * Error Invariants were proposed by Ermis, Schaef, Wies in Error invariants, FM 2012.
 */
@Options(prefix="faults")
public class ErrorInvariantsFaultLocator implements FaultLocator, StatisticsProvider {

  private enum PostConditionType { SINGLE, ALL }
  private enum PathFormulaCreator { ANALYSIS, BOOL }
  private enum InvariantCreation { SEQUENTIAL, SEPARATE }

  @Option(description = "Type of the post condition") // TODO explain better
  private PostConditionType postCondition = PostConditionType.SINGLE;

  @Option(description = "Way to create path formulas for error traces")
  private PathFormulaCreator formulaCreationMethod = PathFormulaCreator.BOOL;

  @Option(description = "How to create invariants on error trace")
  private InvariantCreation invariantCreationMethod = InvariantCreation.SEQUENTIAL;

  private final FormulaManagerView manager;
  private final CtoFormulaConverter converter;

  private final InterpolationManager interpolator;

  private final LogManager logger;

  private StatCounter performedLocalizations = new StatCounter("Successful fault localizations");
  private StatTimer timeForLocalizations = new StatTimer("Time for localizations");

  public static FaultLocator create(ConfigurableProgramAnalysis pCpa)
      throws InvalidConfigurationException {
    ValueAnalysisCPA cpa = CPAs.retrieveCPA(pCpa, ValueAnalysisCPA.class);

    CFA cfa = cpa.getCFA();
    Configuration config = cpa.getConfiguration();
    LogManager logger = cpa.getLogger();
    ShutdownNotifier shutdownNotifier = cpa.getShutdownNotifier();

    Solver solver = Solver.create(config, logger, shutdownNotifier);
    FormulaManagerView manager = solver.getFormulaManager();

    MachineModel machineModel = cfa.getMachineModel();
    Optional<LoopStructure> loopStructure = cfa.getLoopStructure();
    Optional<VariableClassification> varClassification = cfa.getVarClassification();

    CtoFormulaConverter converter = new CtoFormulaConverter(new FormulaEncodingOptions(config),
        manager, machineModel, varClassification, logger, shutdownNotifier, new
        CtoFormulaTypeHandler(logger, machineModel), AnalysisDirection.FORWARD);

    InterpolationManager interpolator = new InterpolationManager(
        new PathFormulaManagerImpl(
            manager, config, logger, shutdownNotifier, cfa, AnalysisDirection.FORWARD),
        solver, loopStructure, varClassification, config, shutdownNotifier, logger);

    return new ErrorInvariantsFaultLocator(
        manager, converter, config, logger, shutdownNotifier, interpolator);
  }


  public ErrorInvariantsFaultLocator(
      final FormulaManagerView pManager,
      final CtoFormulaConverter pConverter,
      final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownNotifier pShutdownNotifier,
      final InterpolationManager pInterpolator
  ) throws InvalidConfigurationException {
    pConfig.inject(this);
    manager = pManager;
    converter = pConverter;
    interpolator = pInterpolator;

    logger = pLogger;
  }

  @Override
  public CounterexampleInfo performLocalization(
      final CounterexampleInfo pInfo,
      final ARGPath pPath,
      final CFAPathWithAssumptions pAssumptionsPath
  ) throws CPAException, InterruptedException, SolverException {
    timeForLocalizations.start();
    SSAMapBuilder ssa = SSAMap.emptySSAMap().builder();
    BooleanFormula pathPostCondition = getPostCondition(pAssumptionsPath, ssa);
    Set<ErrorCause> possibleCauses = locateAll(pPath, pAssumptionsPath, pathPostCondition, ssa);
    timeForLocalizations.stop();
    return CounterexampleInfo.withFaultAnnotation(pInfo, possibleCauses);
  }

  @Override
  public void extractFixInformation() throws CPAException {

  }

  private BooleanFormula getPostCondition(final CFAPathWithAssumptions pErrorPath, final SSAMapBuilder pSsa)
      throws CPATransferException, InterruptedException {
    switch (postCondition) {
      case SINGLE:
        return getSingleAssumptionPostCondition(pErrorPath, pSsa);
      case ALL:
        return getAllAssumptionsPostCondition(pErrorPath, pSsa);
      default:
        throw new AssertionError("Handling of post condition type " + postCondition + " not "
            + "implemented");
    }
  }

  private BooleanFormula getSingleAssumptionPostCondition(
      final CFAPathWithAssumptions pErrorPath,
      final SSAMapBuilder pSsa
  ) throws CPATransferException, InterruptedException {
    int lastAssumeIdx = pErrorPath.size();
    CFAEdge lastAssumeEdge;
    do {
      lastAssumeIdx--;
      lastAssumeEdge = pErrorPath.get(lastAssumeIdx).getCFAEdge();
    }
    while (!lastAssumeEdge.getEdgeType().equals(CFAEdgeType.AssumeEdge));

    BooleanFormula lastAssumption = converter.makePredicate(
        ((CAssumeEdge) lastAssumeEdge).getExpression(),
        lastAssumeEdge,
        "main",
        pSsa);
    return manager.makeNot(lastAssumption);
  }

  private BooleanFormula getAllAssumptionsPostCondition(
      final CFAPathWithAssumptions pErrorPath,
      final SSAMapBuilder pSsa
  ) throws CPATransferException, InterruptedException {
    BooleanFormulaManagerView bfmgr = manager.getBooleanFormulaManager();
    BooleanFormula allAssumptionsPostCondition = bfmgr.makeTrue();

    CFAEdge currentAssumeEdge;
    BooleanFormula currentAssumption;
    for (CFAEdgeWithAssumptions e : pErrorPath) {
      currentAssumeEdge = e.getCFAEdge();

      if (currentAssumeEdge.getEdgeType() == CFAEdgeType.AssumeEdge) {
        currentAssumption = converter.makePredicate(((CAssumeEdge) currentAssumeEdge)
            .getExpression(),
            currentAssumeEdge,
            "main",
            pSsa);
        allAssumptionsPostCondition = bfmgr.and(allAssumptionsPostCondition, currentAssumption);
      }
    }

    return allAssumptionsPostCondition;
  }

  private Set<ErrorCause> locateAll(
      final ARGPath pArgPath,
      final CFAPathWithAssumptions pAssumptionsPath,
      final BooleanFormula pPostCondition,
      final SSAMapBuilder pSsa
  ) throws SolverException, InterruptedException, CPAException {
        switch (invariantCreationMethod) {
          case SEQUENTIAL:
            return locateAllSequentially(pAssumptionsPath, pPostCondition, pSsa);
          default:
            throw new AssertionError("Invariants creation method " + invariantCreationMethod + " "
                + "not implemented");
        }
  }

  private Set<ErrorCause> locateAllSequentially(
      final CFAPathWithAssumptions pErrorPath,
      final BooleanFormula pPostCondition,
      final SSAMapBuilder pSsa
  ) throws CPAException, InterruptedException {
    List<CFAEdgeWithAssumptions> newEdges = pErrorPath;
    List<CFAEdgeWithAssumptions> oldEdges;
    Optional<List<CFAEdge>> causingEdges;
    Set<ErrorCause> errorCauses = new HashSet<>();
    do {
      oldEdges = newEdges;
      causingEdges = locate(oldEdges, pPostCondition, pSsa);

      if (causingEdges.isPresent()) {
        errorCauses.add(new ErrorCause(pErrorPath, causingEdges.get()));
        performedLocalizations.inc();
        newEdges = cleanErrorPath(oldEdges, causingEdges.get());
      }
    } while (causingEdges.isPresent());

    if (errorCauses.isEmpty()) {
      logger.log(Level.WARNING, "Error could not be localized since error is not always reached");
    }

    return errorCauses;
  }

  private Optional<List<CFAEdge>> locate(
      final List<CFAEdgeWithAssumptions> pErrorPathEdges,
      final BooleanFormula pPostCondition,
      final SSAMapBuilder pSsa
  ) throws InterruptedException, CPAException {
    List<Pair<BooleanFormula, CFAEdge>> pathFormulasAndEdges = getPathFormula(pErrorPathEdges, pSsa);

    return locateOnPath(pathFormulasAndEdges, pPostCondition, pSsa);
  }

  private Optional<List<CFAEdge>> locate(
      final ARGPath pPath,
      final BooleanFormula pPostCondition,
      final SSAMapBuilder pSsa
  ) throws CPAException, InterruptedException {
    List<Pair<BooleanFormula, CFAEdge>> pathFormulasAndEdges = getPathFormula(pPath, pSsa);

    return locateOnPath(pathFormulasAndEdges, pPostCondition, pSsa);
  }

  private Optional<List<CFAEdge>> locateOnPath(
      final List<Pair<BooleanFormula, CFAEdge>> pPathFormulasAndEdges,
      final BooleanFormula pPostCondition,
      final SSAMapBuilder pSsa
  ) throws InterruptedException, CPAException {
    List<BooleanFormula> pathFormula = new ArrayList<>(pPathFormulasAndEdges.size());
    for (Pair<BooleanFormula, CFAEdge> p : pPathFormulasAndEdges) {
      pathFormula.add(p.getFirst());
    }
    pathFormula.add(manager.instantiate(pPostCondition, pSsa.build()));

    CounterexampleTraceInfo traceInfo = interpolator.buildCounterexampleTrace(pathFormula);

    if (traceInfo.isSpurious()) {
      List<BooleanFormula> interpolants = traceInfo.getInterpolants();
      List<Integer> relevantOpIndices = getFaultRelevantIndices(interpolants);

      List<CFAEdge> relevantEdges = new ArrayList<>(relevantOpIndices.size());
      for (Integer i : relevantOpIndices) {
        relevantEdges.add(pPathFormulasAndEdges.get(i).getSecond());
      }
      return Optional.of(relevantEdges);
    } else {
      return Optional.empty();
    }
  }

  private List<Pair<BooleanFormula, CFAEdge>> getPathFormula(
      final ARGPath pErrorPath,
      final SSAMapBuilder pSsa
  ) throws InterruptedException, CPATransferException {
    List<CFAEdge> cfaEdges = pErrorPath.getFullPath();
    List<Pair<BooleanFormula, CFAEdge>> formulas = new ArrayList<>(cfaEdges.size());

    for (CFAEdge edge : cfaEdges) {
      Formula f = converter.makeTerm(edge, pSsa);

      if (f instanceof BooleanFormula) {
        BooleanFormula bf = (BooleanFormula) f;
        formulas.add(Pair.of(bf, edge));
      } else {
        logger.log(Level.WARNING, "Edge ", edge, " ignored in fault localization.");
      }
    }

    return formulas;
  }

  private List<Pair<BooleanFormula, CFAEdge>> getPathFormula(
      final List<CFAEdgeWithAssumptions> pErrorPath,
      final SSAMapBuilder pSsa
  ) throws InterruptedException, CPATransferException {
    switch (formulaCreationMethod) {
      case BOOL:
        return getRealPathFormula(pErrorPath, pSsa);
      case ANALYSIS:
        return getAnalysisPathFormula(pErrorPath, pSsa);
      default:
        throw new AssertionError(
            "Formula creation method " + formulaCreationMethod + " not implemented");
    }
  }

  private List<Pair<BooleanFormula, CFAEdge>> getRealPathFormula(
      final List<CFAEdgeWithAssumptions> pErrorPath,
      final SSAMapBuilder pSsa
  ) throws InterruptedException, UnrecognizedCFAEdgeException, UnrecognizedCCodeException {

    List<Pair<BooleanFormula, CFAEdge>> formulas = new ArrayList<>(pErrorPath.size());
    for (CFAEdgeWithAssumptions e : pErrorPath) {
      CFAEdge edge = e.getCFAEdge();
      Formula f = converter.makeTerm(edge, pSsa);

      if (f instanceof BooleanFormula) {
        BooleanFormula bf = (BooleanFormula) f;

        if (manager.getBooleanFormulaManager().isTrue(bf)) {
          Collection<AExpressionStatement> analysisFormulas = e.getExpStmts();
          bf = getFormula(analysisFormulas, edge, pSsa);
        }

        formulas.add(Pair.of(bf, edge));
      } else {
        logger.log(Level.WARNING, "Edge ", e, " ignored in fault localization.");
      }
    }

    return formulas;
  }

  private BooleanFormula getFormula(
      Collection<AExpressionStatement> pAnalysisFormulas,
      CFAEdge pEdge,
      SSAMapBuilder pSsa
  ) throws UnrecognizedCCodeException, InterruptedException {

    final String functionName = pEdge.getPredecessor().getFunctionName();
    final BooleanFormulaManagerView bfmgr = manager.getBooleanFormulaManager();

    BooleanFormula edgeFormula = bfmgr.makeTrue();
    for (AExpressionStatement s : pAnalysisFormulas) {
      CExpression assignment = (CExpression) s.getExpression();
      BooleanFormula f = converter.makePredicate(assignment, pEdge, functionName, pSsa);
      edgeFormula = bfmgr.and(edgeFormula, f);
    }
    return edgeFormula;
  }

  private List<Pair<BooleanFormula, CFAEdge>> getAnalysisPathFormula(
      final List<CFAEdgeWithAssumptions> pErrorPath,
      final SSAMapBuilder pSsa
  ) throws InterruptedException, CPATransferException {
    List<Pair<BooleanFormula, CFAEdge>> formulas = new ArrayList<>(pErrorPath.size());
    for (CFAEdgeWithAssumptions e : pErrorPath) {
      Collection<AExpressionStatement> assumptions = e.getExpStmts();
      CFAEdge edge = e.getCFAEdge();

      formulas.add(Pair.of(getFormula(assumptions, edge, pSsa), edge));
    }

    return formulas;
  }

  private List<CFAEdgeWithAssumptions> cleanErrorPath(
      final List<CFAEdgeWithAssumptions> pErrorPathEdges,
      final List<CFAEdge> pRelevantEdges
  ) {
    List<CFAEdgeWithAssumptions> cleanedErrorPath = new ArrayList<>(pErrorPathEdges);
    List<Integer> relevantOpIndices = new ArrayList<>(pRelevantEdges.size());
    for (ListIterator<CFAEdgeWithAssumptions> it = cleanedErrorPath.listIterator(); it.hasNext();) {
      CFAEdgeWithAssumptions currEdge = it.next();
      if (pRelevantEdges.contains(currEdge.getCFAEdge())) {
        relevantOpIndices.add(it.previousIndex());
      }
    }
    for (Integer idx : relevantOpIndices) {

      CFAEdge faultyEdge = cleanedErrorPath.get(idx).getCFAEdge();
      assert pRelevantEdges.contains(faultyEdge);

      CFAEdge noopEdge = new BlankEdge(
          faultyEdge.getRawStatement(),
          faultyEdge.getFileLocation(),
          faultyEdge.getPredecessor(),
          faultyEdge.getSuccessor(),
          "sliced in previous error invariant"
      );
      CFAEdgeWithAssumptions noopEdgeWithAssumptions =
          new CFAEdgeWithAssumptions(noopEdge, Collections.emptySet(), noopEdge.getDescription());

      cleanedErrorPath.set(
          idx,
          noopEdgeWithAssumptions);
    }
    return cleanedErrorPath;
  }

  private List<Integer> getFaultRelevantIndices(final List<BooleanFormula> pInterpolants) {
    BooleanFormula previousInterpolant = pInterpolants.get(0);
    List<Integer> relevantOpIndices = new ArrayList<>(5);
    int currentOpIndex = 0;
    for (BooleanFormula i : pInterpolants) {
      if (!previousInterpolant.equals(i)) {
        relevantOpIndices.add(currentOpIndex);
        previousInterpolant = i;
      }
      currentOpIndex++;
    }

    return relevantOpIndices;
  }

  private <T> List<BooleanFormula> interpolate(
      final CounterexampleInfo pInfo,
      final List<T> pItpStack,
      final List<CFAEdge> pEdges,
      final InterpolatingProverEnvironment<T> pItpProver
  ) throws SolverException, InterruptedException {
    List<BooleanFormula> interpolants = new ArrayList<>();

    int prefixEnd = 0;
    for (CFAEdge e : pEdges) {
      prefixEnd++;
      interpolants.add(pItpProver.getInterpolant(pItpStack.subList(0, prefixEnd)));
    }

    return interpolants;
  }

  @Override
  public void collectStatistics(Collection<Statistics> statsCollection) {
    statsCollection.add(new Statistics() {

      @Override
      public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
        StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(pOut);
        writer.put(performedLocalizations)
            .put(timeForLocalizations);
      }

      @Nullable
      @Override
      public String getName() {
        return ErrorInvariantsFaultLocator.this.getClass().getSimpleName();
      }
    });
  }
}
