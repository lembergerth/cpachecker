/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.parallel_bam;

import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.graph.Traverser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm.CPAAlgorithmFactory;
import org.sosy_lab.cpachecker.core.algorithm.parallel_bam.ParallelBAMAlgorithm.ParallelBAMStatistics;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bam.BAMCPAWithoutReachedSetCreation;
import org.sosy_lab.cpachecker.cpa.bam.BlockSummaryMissingException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer.TimerWrapper;

/**
 * A wrapper for a single reached-set and the corresponding data-structures. We assume that each
 * reachedset is contained in only one ReachedSetExecutor and that each instance of
 * ReachedSetExecutor is only executed by a single thread, because this guarantees us
 * single-threaded access to the reached-sets.
 */
class ReachedSetExecutor {

  private static final Level level = Level.ALL;
  private static final Runnable NOOP = () -> {};

  /** the working reached-set, single-threaded access. */
  private final ReachedSet rs;

  /** the block for the working reached-set. */
  private final Block block;

  /** the working algorithm for the reached-set, single-threaded access. */
  private final CPAAlgorithm algorithm;

  /** flag that causes termination if enabled. */
  private boolean targetStateFound = false;

  /** main reached-set is used for checking termination of the algorithm. */
  private final ReachedSet mainReachedSet;

  /**
   * important central data structure, shared over all threads, need to be synchronized directly.
   */
  private final Map<ReachedSet, Pair<ReachedSetExecutor, CompletableFuture<Void>>>
      reachedSetMapping;

  private final ExecutorService pool;

  private final BAMCPAWithoutReachedSetCreation bamcpa;
  private final CPAAlgorithmFactory algorithmFactory;
  private final ShutdownNotifier shutdownNotifier;
  private final ParallelBAMStatistics stats;
  private final AtomicReference<Throwable> error;
  private final AtomicBoolean terminateAnalysis;
  private final LogManager logger;

  int execCounter = 0; // statistics
  private final TimerWrapper threadTimer;
  private final TimerWrapper addingStatesTimer;
  private final TimerWrapper terminationCheckTimer;

  /**
   * This set contains all sub-reached-sets that have to be finished before the current one. The
   * state is unique. Synchronized access guaranteed by only instance-local access in the current
   * {@link ReachedSetExecutor}!
   */
  private final Set<AbstractState> dependsOn = new LinkedHashSet<>();

  /**
   * This mapping contains all {@link ReachedSetExecutor}s (known as parents) that wait for the
   * current one. The abstract state is the non-reduced initial state of the parent reached-set, and
   * it must be re-added when the child terminates. The current reached-set has to be finished
   * before parent reached-set. The state is unique, RSE is not. Synchronized access needed!
   */
  private final Multimap<ReachedSetExecutor, AbstractState> dependingFrom =
      LinkedHashMultimap.create();

  public ReachedSetExecutor(
      BAMCPAWithoutReachedSetCreation pBamCpa,
      ReachedSet pRs,
      Block pBlock,
      ReachedSet pMainReachedSet,
      Map<ReachedSet, Pair<ReachedSetExecutor, CompletableFuture<Void>>> pReachedSetMapping,
      ExecutorService pPool,
      CPAAlgorithmFactory pAlgorithmFactory,
      ShutdownNotifier pShutdownNotifier,
      ParallelBAMStatistics pStats,
      AtomicReference<Throwable> pError,
      AtomicBoolean pTerminateAnalysis,
      LogManager pLogger) {
    bamcpa = pBamCpa;
    rs = pRs;
    block = pBlock;
    mainReachedSet = pMainReachedSet;
    reachedSetMapping = pReachedSetMapping;
    pool = pPool;
    algorithmFactory = pAlgorithmFactory;
    shutdownNotifier = pShutdownNotifier;
    stats = pStats;
    error = pError;
    terminateAnalysis = pTerminateAnalysis;
    logger = pLogger;

    algorithm = algorithmFactory.newInstance();

    logger.logf(level, "%s :: creating RSE", this);

    assert pBlock == getBlockForState(pRs.getFirstState());
    assert !pReachedSetMapping.containsKey(pRs);

    threadTimer = stats.threadTime.getNewTimer();
    addingStatesTimer = stats.addingStatesTime.getNewTimer();
    terminationCheckTimer = stats.terminationCheckTime.getNewTimer();
  }

  public Runnable asRunnable() {
    return asRunnable(ImmutableSet.of());
  }

  public Runnable asRunnable(Collection<AbstractState> pStatesToBeAdded) {
    // copy needed, because access to pStatesToBeAdded is done in the future
    ImmutableSet<AbstractState> copy = ImmutableSet.copyOf(pStatesToBeAdded);
    return () -> apply(copy);
  }

  /**
   * This method contains the main function of the RSE: It analyzes the reached-set, handles blocks
   * and updates dependencies.
   *
   * <p>This method should be synchronized by design of the algorithm. There exists a mapping of
   * ReachedSet to ReachedSetExecutor that guarantees single-threaded access to each ReachedSet.
   */
  private void apply(Collection<AbstractState> pStatesToBeAdded) {
    threadTimer.start();
    int running = stats.numActiveThreads.incrementAndGet();
    stats.histActiveThreads.insertValue(running);
    stats.numMaxRSE.accumulate(reachedSetMapping.size());
    execCounter++;

    try { // big try-block to catch all exceptions

      if (shutdownNotifier.shouldShutdown()) {
        terminateAnalysis.set(true);
        pool.shutdownNow();
        return;
      }

      logger.logf(
          level,
          "%s :: starting, target=%s, statesToBeAdded=%s",
          this,
          targetStateFound,
          id(pStatesToBeAdded));

      addingStatesTimer.start();
      updateStates(pStatesToBeAdded);
      addingStatesTimer.stop();

      // handle finished reached-set after refinement
      // TODO checking this once on RSE-creation would be sufficient
      checkForTargetState();

      if (!targetStateFound) {
        // further analysis of the reached-set, sub-analysis is scheduled if necessary
        try {
          @SuppressWarnings("unused")
          AlgorithmStatus tmpStatus = algorithm.run(rs);
        } catch (BlockSummaryMissingException bsme) {
          handleMissingBlock(bsme);
        }
      }

      terminationCheckTimer.start();
      handleTermination();
      terminationCheckTimer.stop();

      logger.logf(level, "%s :: exiting, targetStateFound=%s", this, targetStateFound);

    } catch (Throwable e) { // catch everything to avoid deadlocks after a problem.
      logger.logException(level, e, e.getClass().getName());
      terminateAnalysis.set(true);
      error.set(e);
      pool.shutdownNow();
    } finally {
      stats.numActiveThreads.decrementAndGet();
      threadTimer.stop();
    }
  }

  private void checkForTargetState() {
    boolean endsWithTargetState = endsWithTargetState();

    if (targetStateFound) {
      Preconditions.checkState(
          endsWithTargetState,
          "when a target was found before, it should remain as target of the reached-set");
      Preconditions.checkState(
          terminateAnalysis.get(),
          "when a target was found before, we want to stop further scheduling");
    }

    if (endsWithTargetState) {
      targetStateFound = true;
      terminateAnalysis.set(true);
    }
  }
  private static String id(final Collection<AbstractState> states) {
    return Collections2.transform(states, s -> id(s)).toString();
  }

  private static String id(final AbstractState state) {
    return ((ARGState) state).getStateId() + "@" + AbstractStates.extractLocation(state);
  }

  private static String id(ReachedSet pRs) {
    return id(pRs.getFirstState());
  }

  private String idd() {
    return id(rs);
  }

  /**
   * This method re-adds states to the waitlist. The states were removed due to missing blocks, and
   * we re-add them when the missing block is finished. The states are at block-start locations.
   */
  private void updateStates(Collection<AbstractState> pStatesToBeAdded) {
    for (AbstractState state : pStatesToBeAdded) {
      rs.reAddToWaitlist(state);
      dependsOn.remove(state);
    }
  }

  private boolean endsWithTargetState() {
    return rs.getLastState() != null && AbstractStates.isTargetState(rs.getLastState());
  }

  boolean isTargetStateFound() {
    return targetStateFound;
  }

  /** check whether we have to update any depending reached-set. */
  private void handleTermination() {

    checkForTargetState();

    boolean isFinished = dependsOn.isEmpty();
    if (isFinished) {
      if (rs.getWaitlist().isEmpty() || targetStateFound) {
        updateCache(targetStateFound);
      } else {
        // otherwise we have an unfinished reached-set and do not cache the incomplete result.
      }
      reAddStatesToDependingReachedSets();

      if (rs == mainReachedSet) {
        logger.logf(level, "%s :: mainRS finished, shutdown threadpool", this);
        pool.shutdown();
      }

      // we never need to execute this RSE again,
      // thus we can clean up and avoid a (small) memory-leak
      synchronized (reachedSetMapping) {
        reachedSetMapping.remove(rs);
        stats.executionCounter.insertValue(execCounter);
        // no need to wait for p.getSecond(), we assume a error-free exit after this point.
      }
    }

    logger.logf(
        level, "%s :: finished=%s, targetStateFound=%s", this, isFinished, targetStateFound);
  }

  private void updateCache(boolean pEndsWithTargetState) {
    if (rs == mainReachedSet) {
      // we do not cache main reached set, because it should not be used internally
      return;
    }

    AbstractState reducedInitialState = rs.getFirstState();
    Precision reducedInitialPrecision = rs.getPrecision(reducedInitialState);
    Block innerBlock = getBlockForState(reducedInitialState);
    final Collection<AbstractState> exitStates =
        extractExitStates(pEndsWithTargetState, innerBlock);
    Pair<ReachedSet, Collection<AbstractState>> check =
        bamcpa.getCache().get(reducedInitialState, reducedInitialPrecision, innerBlock);
    assert check.getFirst() == rs
        : String.format(
            "reached-set for initial state should be unique: current rs = %s, cached entry = %s",
            id(rs), check.getFirst());
    if (!exitStates.equals(check.getSecond())) {
      assert check.getSecond() == null
          : String.format(
              "result-states already registered for reached-set %s: current = %s, cached = %s",
              id(rs),
              Collections2.transform(exitStates, s -> id(s)),
              Collections2.transform(check.getSecond(), s -> id(s)));
      bamcpa
          .getCache()
          .put(reducedInitialState, reducedInitialPrecision, innerBlock, exitStates, null);
    }
  }

  private Collection<AbstractState> extractExitStates(boolean pEndsWithTargetState, Block pBlock) {
    if (pEndsWithTargetState) {
      assert AbstractStates.isTargetState(rs.getLastState());
      return Collections.singletonList(rs.getLastState());
    } else {
      return AbstractStates.filterLocations(rs, pBlock.getReturnNodes())
          .filter(s -> ((ARGState) s).getChildren().isEmpty())
          .toList();
    }
  }

  private void reAddStatesToDependingReachedSets() {
    // first lock is only against deadlock of locks for 'reachedSetMapping' and 'dependingFrom'.
    // TODO optimize lock/unlock behavior if performance is too bad
    synchronized (reachedSetMapping) {
    synchronized (dependingFrom) {
      logger.logf(level, "%s :: %s -> %s", this, this, dependingFrom.keys());
      for (Entry<ReachedSetExecutor, Collection<AbstractState>> parent :
          dependingFrom.asMap().entrySet()) {
        registerJob(parent.getKey(), parent.getKey().asRunnable(parent.getValue()));
      }
      dependingFrom.clear();
    }
    }
  }

  private void addDependencies(
      BlockSummaryMissingException pBsme, final ReachedSetExecutor subRse) {
    logger.logf(level, "%s :: %s -> %s", this, this, subRse);
    dependsOn.add(pBsme.getState());
    synchronized (subRse.dependingFrom) {
      subRse.dependingFrom.put(this, pBsme.getState());
    }
  }

  /**
   * When a block summary is missing, the BAM-CPA throws a {@link BlockSummaryMissingException} and
   * the CPA-algorithm terminates. Then we use the info from the exception to handle the missing
   * block summary here, such that we
   * <li>remove the initial state from the reached-set,
   * <li>create a new {@link ReachedSetExecutor} for the missing block,
   * <li>add a dependency between the sub-analysis and the current one.
   *
   * @throws UnsupportedCodeException when finding a recursive function call
   */
  private void handleMissingBlock(BlockSummaryMissingException pBsme)
      throws UnsupportedCodeException {
    logger.logf(level, "%s :: missing block, bsme=%s", this, id(pBsme.getState()));

    if (targetStateFound) {
      logger.logf(Level.SEVERE, "%s :: after finding a missing block, we should not get new states", this);
      throw new AssertionError("after finding a missing block, we should not get new states");
    }

    final CFANode entryLocation = AbstractStates.extractLocation(pBsme.getState());
    if (hasRecursion(entryLocation)) {
      // we directly abort when finding recursion, instead of asking {@link CallstackCPA}
      throw new UnsupportedCodeException("recursion", entryLocation.getLeavingEdge(0));
    }

    if (shutdownNotifier.shouldShutdown() || terminateAnalysis.get()) {
      // if an error was found somewhere, we do not longer schedule new sub-analyses
      logger.logf(level, "%s :: exiting on demand", this);
      return;
    }

    // register new sub-analysis as asynchronous/parallel/future work, if not existent
    synchronized (reachedSetMapping) {
      ReachedSet newRs = createAndRegisterNewReachedSet(pBsme);
      Pair<ReachedSetExecutor, CompletableFuture<Void>> p = reachedSetMapping.get(newRs);

      if (p == null) {
        // BSME interleaved with termination of sub-reached-set analysis,
        // cache-update was too slow, but cleanup of RSE in reachedSetMapping was too fast.
        // Restarting the procedure once should be sufficient,
        // such that the analysis tries a normal cache-access again.
        // --> nothing to do
        logger.logf(
            level, "%s :: interleaved with another thread, bsme=%s", this, id(pBsme.getState()));

      } else {
        scheduleSubAnalysis(pBsme, p);
      }
    }

    if (rs.getWaitlist().isEmpty()) {
      // optimization: if no further states are waiting, no need to schedule the current RSE.
      // when sub-analysis is finished, the current analysis is re-started.
      logger.logf(level, "%s :: not scheduling self, emtpy waitlist", this);
    } else {
      // register current RSE for further analysis.
      // this step results in 'parallel' execution of current analysis and sub-analysis.
      registerJob(this, this.asRunnable());
    }
  }

  /** We need to traverse the RSEs whether there is a cyclic dependency. */
  private boolean hasRecursion(CFANode pEntryLocation) {
    synchronized (reachedSetMapping) {
      // TODO correct lock? we need to avoid RSE-creation during traversal.
      return Iterables.any(
          Traverser.<ReachedSetExecutor>forGraph(rse -> rse.dependingFrom.keys())
              .breadthFirst(this),
          rse -> rse.block.getCallNodes().contains(pEntryLocation));
    }
  }

  private void scheduleSubAnalysis(BlockSummaryMissingException pBsme,
      Pair<ReachedSetExecutor, CompletableFuture<Void>> p) {
    // remove current state from waitlist to avoid exploration until all sub-blocks are done.
    // The state was removed for exploration,
    // but re-added by CPA-algorithm when throwing the exception
    assert rs.contains(pBsme.getState()) : "parent reachedset must contain entry state";
    rs.removeOnlyFromWaitlist(pBsme.getState());

    // register dependencies to wait for results and to get results, asynchronous
    ReachedSetExecutor subRse = p.getFirst();
    addDependencies(pBsme, subRse);

    // register callback to get results of terminated analysis
    registerJob(subRse, subRse.asRunnable());
  }

  /**
   * Get the reached-set for the missing block's analysis. If we already have a valid reached-set,
   * we return it. If the reached-set was missing when throwing the exception, we check the cache
   * again. If the reached-set is missing, we create a new reached-set and the ReachedSetExecutor.
   *
   * @return a valid reached-set to be analyzed
   */
  private ReachedSet createAndRegisterNewReachedSet(BlockSummaryMissingException pBsme) {
    ReachedSet newRs = pBsme.getReachedSet();
    if (newRs == null) {
      // We are only synchronized in the current method. Thus, we need to check
      // the cache again, maybe another thread already created the needed reached-set.
      final Pair<ReachedSet, Collection<AbstractState>> pair =
          bamcpa
              .getCache()
              .get(pBsme.getReducedState(), pBsme.getReducedPrecision(), pBsme.getBlock());
      newRs = pair.getFirst(); // @Nullable
    }

    // now we can be sure, whether the sub-reached-set exists or not.
    if (newRs == null) {
      // we have not even cached a partly computed reached-set,
      // so we must compute the subgraph specification from scratch
      newRs =
          bamcpa
              .getData()
              .createAndRegisterNewReachedSet(
                  pBsme.getReducedState(), pBsme.getReducedPrecision(), pBsme.getBlock());
    }

    if (!reachedSetMapping.containsKey(newRs)) {
      // we have a partial (or even finished) reached-set,
      // so we schedule it for further exploration
      ReachedSetExecutor subRse =
          new ReachedSetExecutor(
              bamcpa,
              newRs,
              pBsme.getBlock(),
              mainReachedSet,
              reachedSetMapping,
              pool,
              algorithmFactory,
              shutdownNotifier,
              stats,
              error,
              terminateAnalysis,
              logger);
      // register NOOP here. Callback for results is registered later, we have "lazy" computation.
      logger.logf(level, "%s :: register subRSE %s", this, id(newRs));
      CompletableFuture<Void> future =
          CompletableFuture.runAsync(NOOP, pool).exceptionally(new ExceptionHandler(subRse));
      assert !reachedSetMapping.containsKey(newRs)
          : "should not happen, we are in synchronized context";
      reachedSetMapping.put(newRs, Pair.of(subRse, future));
    }

    Preconditions.checkState(
        reachedSetMapping.containsKey(newRs),
        "scheduling unregistered reached-set will be difficult");
    return newRs;
  }

  /**
   * build a chain of jobs, append a new job after the last registered job for the given
   * reached-set.
   */
  private void registerJob(ReachedSetExecutor pRse, Runnable r) {
    synchronized (reachedSetMapping) {
      Pair<ReachedSetExecutor, CompletableFuture<Void>> p = reachedSetMapping.get(pRse.rs);
      assert p.getFirst() == pRse;
      logger.logf(level, "%s :: scheduling RSE: %s", this, pRse);
      CompletableFuture<Void> future =
          p.getSecond().thenRunAsync(r, pool).exceptionally(new ExceptionHandler(pRse));
      reachedSetMapping.put(pRse.rs, Pair.of(pRse, future));
    }
  }

  private Block getBlockForState(AbstractState state) {
    CFANode location = extractLocation(state);
    assert bamcpa.getBlockPartitioning().isCallNode(location)
        : "root of reached-set must be located at block entry.";
    return bamcpa.getBlockPartitioning().getBlockForCallNode(location);
  }

  @Override
  public String toString() {
    return "RSE " + idd();
  }

  /** for debugging */
  String getDependenciesAsDot() {
    final List<String> dependencies = new ArrayList<>();
    synchronized (reachedSetMapping) {
      for (ReachedSetExecutor rse :
          Collections2.transform(reachedSetMapping.values(), Pair::getFirst)) {
        for (ReachedSetExecutor dependentRse : rse.dependingFrom.keys()) {
          dependencies.add(String.format("\"%s\" -> \"%s\"", rse, dependentRse));
        }
      }
    }
    Collections.sort(dependencies); // for deterministic dot-graphs
    return "digraph DEPENDENCIES {\n  " + Joiner.on(";\n  ").join(dependencies) + ";\n}\n";
  }

  class ExceptionHandler implements Function<Throwable, Void> {

    private final ReachedSetExecutor rse;

    public ExceptionHandler(ReachedSetExecutor pRse) {
      rse = pRse;
    }

    @Override
    public Void apply(Throwable e) {
      if (e instanceof RejectedExecutionException || e instanceof CompletionException) {
        // pool will shutdown on forced termination after timeout and throw lots of them.
        // we can ignore those exceptions.
        logger.logException(level, e, e.getClass().getSimpleName());
      } else {
        logger.logException(Level.WARNING, e, e.getClass().getSimpleName());
        error.compareAndSet(null, e);
        rse.terminateAnalysis.set(true);
      }

      return null;
    }
  }
}
