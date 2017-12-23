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
package org.sosy_lab.cpachecker.cpa.flowdep;

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * Abstract domain of {@link FlowDependenceCPA}.
 * Each element of the lattice this domain is based on
 * is a set of {@link FlowDependence FlowDependence}s
 * Top is the set of all possible FlowDependences of a program,
 * bottom is the empty set.
 *
 * The domain is based on a power-set lattice.
 */
class FlowDependenceDomain implements AbstractDomain {

  @Override
  public AbstractState join(
      AbstractState pState1, AbstractState pState2) throws CPAException, InterruptedException {

    assert pState1 instanceof FlowDependenceState
        : "Wrong type for first state passed: " + pState1;
    assert pState2 instanceof FlowDependenceState
        : "Wrong type for second state passed: " + pState2;

    return ((FlowDependenceState) pState1).join((FlowDependenceState) pState2);
  }

  @Override
  public boolean isLessOrEqual(
      AbstractState pStateLhs, AbstractState pStateRhs) throws CPAException, InterruptedException {

    assert pStateLhs instanceof FlowDependenceState
        : "Wrong type for first state passed: " + pStateLhs;
    assert pStateRhs instanceof FlowDependenceState
        : "Wrong type for second state passed: " + pStateRhs;

    return ((FlowDependenceState) pStateLhs).isLessOrEqual((FlowDependenceState) pStateRhs);
  }
}
