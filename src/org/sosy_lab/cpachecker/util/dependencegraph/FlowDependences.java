/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.util.dependencegraph;

import com.google.common.base.Predicates;
import com.google.common.collect.ForwardingMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.flowdep.FlowDependence;
import org.sosy_lab.cpachecker.cpa.flowdep.FlowDependenceState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState.ProgramDefinitionPoint;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.LiveVariables;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Flow dependences of nodes in a {@link CFA}.
 *
 * <p>A node <code>I</code> is flow dependent on a node <code>J</code> if <code>J</code>
 * represents a variable assignment and the assignment is part of node <code>I</code>'s use-def
 * relation.</p>
 */
public class FlowDependences extends ForwardingMap<CFANode, FlowDependence> {

  // CFANode -> Dependencies of that node
  private Map<CFANode, FlowDependence> dependences;

  private FlowDependences(final Map<CFANode, FlowDependence> pDependences) {
    dependences = pDependences;
  }

  public static Factory factory() {
    return new Factory();
  }

  public FlowDependence getDependences(final CFANode pDependentNode) {
    return dependences.get(pDependentNode);
  }

  @Override
  protected Map<CFANode, Map<MemoryLocation, Set<ProgramDefinitionPoint>>> delegate() {
    return dependences;
  }

  public static class Factory {

    private Factory() { }

    public FlowDependences create(
        final CFA pCfa,
        final LogManager pLogger,
        final ShutdownNotifier pShutdownNotifier
    ) throws InvalidConfigurationException, CPAException, InterruptedException {
      String configFile = "liveDefinitions.properties";

      Configuration config =
          Configuration.builder().loadFromResource(LiveVariables.class, configFile).build();
      ReachedSetFactory reachedFactory = new ReachedSetFactory(config);
      ConfigurableProgramAnalysis cpa =
          new CPABuilder(config, pLogger, pShutdownNotifier, reachedFactory)
              .buildCPAs(pCfa, Specification.alwaysSatisfied(), new AggregatedReachedSets());
      Algorithm algorithm = CPAAlgorithm.create(cpa,
          pLogger,
          config,
          pShutdownNotifier);
      ReachedSet reached = reachedFactory.create();

      AbstractState initialState =
          cpa.getInitialState(pCfa.getMainFunction(), StateSpacePartition.getDefaultPartition());
      Precision initialPrecision =
          cpa.getInitialPrecision(pCfa.getMainFunction(), StateSpacePartition.getDefaultPartition());
      reached.add(initialState, initialPrecision);

      do {
        algorithm.run(reached);
      } while (reached.hasWaitingState());

      Map<CFANode, Set<ProgramDefinitionPoint>> dependencyMap = new HashMap<>();
      for (AbstractState s : reached) {
        assert s instanceof CompositeState;
        CompositeState wrappingState = (CompositeState) s;
        FlowDependenceState liveDefState = getState(wrappingState, FlowDependenceState.class);
        LocationState locationState = getState(wrappingState, LocationState.class);

        CFANode currNode = locationState.getLocationNode();
        if (!dependencyMap.containsKey(currNode)) {
          dependencyMap.put(currNode, new HashSet<>());
        }
        dependencyMap.get(currNode).addAll(liveDefState.getAllDependentDefs());
      }

      return new FlowDependences(dependencyMap);
    }

    @SuppressWarnings("unchecked")
    private <C, T extends Class<C>> C getState(CompositeState pState, T stateClass) {
      Optional<AbstractState> s =
          pState.getWrappedStates().stream().filter(Predicates.instanceOf(stateClass)).findFirst();

      assert s.isPresent()
          : "No " + ReachingDefState.class.getCanonicalName() + " found in composite state: "
          + pState.toString();

      return (C) s.get();
    }
  }
}
