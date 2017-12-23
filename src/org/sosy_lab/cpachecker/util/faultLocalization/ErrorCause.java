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
package org.sosy_lab.cpachecker.util.faultLocalization;

import com.google.common.collect.ForwardingList;
import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.counterexample.CFAPathWithAssumptions;

/**
 * Possible cause of a property violation in a program.
 */
public class ErrorCause extends ForwardingList<CFAEdge> {

  private CFAPathWithAssumptions fullErrorPath;
  private List<CFAEdge> relevantEdges;
  private Set<FileLocation> relevantLocations = new HashSet<>();

  public ErrorCause(
      final CFAPathWithAssumptions pErrorPath,
      final List<CFAEdge> pFaultCausingEdges
  ) {
    fullErrorPath = pErrorPath;
    for (CFAEdge e : pFaultCausingEdges) {
      relevantLocations.add(e.getFileLocation());
    }
    relevantEdges = ImmutableList.copyOf(pFaultCausingEdges);
  }

  public boolean contains(final CFAEdge pEdge) {
    return relevantLocations.contains(pEdge.getFileLocation());
  }

  public CFAPathWithAssumptions getErrorPath() {
    return fullErrorPath;
  }

  @Override
  protected List<CFAEdge> delegate() {
    return relevantEdges;
  }
}
