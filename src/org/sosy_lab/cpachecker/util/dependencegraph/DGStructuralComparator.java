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
package org.sosy_lab.cpachecker.util.dependencegraph;

import com.google.common.collect.ImmutableSet;

import org.sosy_lab.cpachecker.util.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comparator that compares two {@link DependenceGraph DependenceGraphs} by their structure.
 *
 *
 */
public class DGStructuralComparator implements Scorer {

  private static final int EDGE_EXISTS_SCORE = 2;
  private static final int EDGE_MISSING_SCORE = -1;
  private static final int WORST_SCORE = Integer.MIN_VALUE;

  private final DGNode reference;

  public DGStructuralComparator(final DependenceGraph pReferenceGraph) {
    assert pReferenceGraph.hasStartNode();
    reference = pReferenceGraph.getStartNode();
  }

  @Override
  public double score(final DependenceGraph pGraph) {
    assert pGraph.hasStartNode();
    final DGNode start = pGraph.getStartNode();
    final Pair<Set<DGNode>, Set<DGNode>> tintedNodes =
        Pair.of(ImmutableSet.of(start), ImmutableSet.of(reference));
    return score0(start, reference, tintedNodes);
  }

  private double score0(
      final DGNode pOther,
      final DGNode pReference,
      final Pair<Set<DGNode>, Set<DGNode>> pTinted
  ) {
    // If two nodes are not of the same type, we assign the worst score possible.
    if (!pOther.getClass().equals(pReference.getClass())) {
      return WORST_SCORE;
    }

    double score = 0;
    Set<DGEdge> referenceOutgoing = pReference.getOutgoingEdges();
    Set<DGEdge> referenceIncoming = pReference.getIncomingEdges();
    score += scoreEdges(pOther.getOutgoingEdges(), referenceOutgoing);
    score += scoreEdges(pOther.getIncomingEdges(), referenceIncoming);

    Set<DGNode> tintedOthers = pTinted.getFirst();
    Set<DGNode> tintedRefs = pTinted.getSecond();

    double maxScore = Double.NEGATIVE_INFINITY;
    for (DGEdge otherEdge : pOther.getOutgoingEdges()) {
      DGNode o = otherEdge.getEnd();
      if (tintedOthers.contains(o)) {
        continue;
      }

      for (DGEdge refEdge : referenceOutgoing) {
        DGNode r = refEdge.getEnd();
        if (tintedRefs.contains(r)) {
          continue;

        } else if (otherEdge.getClass().equals(refEdge.getClass())) {
          double currScore = score0(o, r, addTinted(pTinted, o, r));
          if (currScore >= maxScore) {
            maxScore = currScore;
          }
        }
      }
    }

    if (Double.isInfinite(maxScore)) {
      maxScore = 0;
    }

    return score + maxScore;
  }

  private Pair<Set<DGNode>, Set<DGNode>> addTinted(
      final Pair<Set<DGNode>, Set<DGNode>> pTinted,
      final DGNode pO,
      final DGNode pR
  ) {
    Set<DGNode> other = new HashSet<>(pTinted.getFirst());
    Set<DGNode> ref = new HashSet<>(pTinted.getSecond());
    other.add(pO);
    ref.add(pR);
    return Pair.of(other, ref);
  }

  private double scoreEdges(final Set<DGEdge> pOther, final Set<DGEdge> pReference) {
    double score = 0;
    Set<DGEdge> refEdgesLeft = new HashSet<>(pReference);
    for (DGEdge e : pOther) {
     List<DGEdge> matches = refEdgesLeft
         .stream()
         .filter(x -> x.getClass().equals(e.getClass()))
         .collect(Collectors.toList());

      if (!matches.isEmpty()) {
        refEdgesLeft.remove(matches.get(0));
        score += EDGE_EXISTS_SCORE;
      } else {
        score += EDGE_MISSING_SCORE; // Edge in other but not in reference
      }
    }
    score += EDGE_MISSING_SCORE * refEdgesLeft.size(); // Edge in reference but not in other
    return score;
  }


}
