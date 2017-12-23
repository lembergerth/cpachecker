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
package org.sosy_lab.cpachecker.cpa.flowdep;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractWrapperState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState.ProgramDefinitionPoint;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Abstract state of {@link FlowDependenceCPA}.
 * Contains a set of all currently live definitions
 * and definitions used .
 *
 * <p>All objects of this class are immutable.</p>
 */
public class FlowDependenceState
    implements AbstractState,  AbstractWrapperState, LatticeAbstractState<FlowDependenceState> {

  private final Multimap<MemoryLocation, ProgramDefinitionPoint> usedDefs;
  private final Multimap<String, MemoryLocation> idsOfFunction;

  private ReachingDefState reachDefState;

  FlowDependenceState(ReachingDefState pReachDefState) {
    usedDefs = HashMultimap.create();
    idsOfFunction = HashMultimap.create();

    reachDefState = pReachDefState;
  }

  private FlowDependenceState(final FlowDependenceState pOld) {
    usedDefs = HashMultimap.create(pOld.usedDefs);
    idsOfFunction = pOld.idsOfFunction;
    reachDefState = pOld.reachDefState;
  }

  public Collection<ProgramDefinitionPoint> getDependentDefs(final MemoryLocation pIdentifier) {
    return ImmutableSet.copyOf(usedDefs.get(pIdentifier));
  }

  public Collection<ProgramDefinitionPoint> getAllDependentDefs() {
    return ImmutableSet.copyOf(usedDefs.values());
  }

  ReachingDefState getReachDefState() {
    return reachDefState;
  }

  @Override
  public FlowDependenceState join(FlowDependenceState other) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isLessOrEqual(FlowDependenceState other)
      throws CPAException, InterruptedException {
    return equals(other);
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO == null || getClass() != pO.getClass()) {
      return false;
    }
    FlowDependenceState that = (FlowDependenceState) pO;
    return Objects.equals(usedDefs, that.usedDefs);
  }

  @Override
  public int hashCode() {
    return Objects.hash(usedDefs);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("{");
    if (!usedDefs.isEmpty()) {
      sb.append("\n");
    }
    for (Map.Entry<MemoryLocation, ProgramDefinitionPoint> e : usedDefs.entries()) {
      sb.append("\t");
      sb.append(e.getKey().toString()).append(" <- ").append(e.getValue());
      sb.append("\n");
    }
    sb.append("}");
    return sb.toString();
  }

  @Override
  public Iterable<AbstractState> getWrappedStates() {
    return ImmutableSet.of(reachDefState);
  }
}
