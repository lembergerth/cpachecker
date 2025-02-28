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
package org.sosy_lab.cpachecker.cpa.smg.graphs;

import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgePointsTo;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;

/** An immutable collection of points-to-edges. */
public interface SMGPointsToEdges extends Iterable<SMGEdgePointsTo> {

  // Modifying methods

  public SMGPointsToEdges addAndCopy(SMGEdgePointsTo pEdge);

  public SMGPointsToEdges removeAndCopy(SMGEdgePointsTo pEdge);

  public SMGPointsToEdges removeAllEdgesOfObjectAndCopy(SMGObject pObj);

  public SMGPointsToEdges removeEdgeWithValueAndCopy(int pValue);

  // Querying methods

  public boolean containsEdgeWithValue(Integer pValue);

  public @Nullable SMGEdgePointsTo getEdgeWithValue(Integer pValue);

  public int size();

}
