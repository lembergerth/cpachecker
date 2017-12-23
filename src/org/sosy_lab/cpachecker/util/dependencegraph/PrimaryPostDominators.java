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

import com.google.common.collect.ForwardingMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Map of {@link CFANode CFANodes} to their primary post-dominators.
 *
 * <p>A set <code>V</code> of nodes with more than one element (<code>|V| > 1</code>) is a primary
 * post-dominator, abbreviated as PPD, of node <code>i</code>, iff both of the following
 * conditions are fulfilled:
 * <ol>
 * <li>set <code>V</code> is a post-dominator of node <code>i</code>, i.e., every path from
 * <code>i</code> to the program exit goes through at least one node
 * <code>v &isin; V</code> and there is no subset of <code>V</code> for which that is true.</li>
 *
 * <li>each node <code>v &isin; V</code> post-dominates at least one immediate successor of
 * <code>i</code>, i.e. for each node <code>v &isin; V</code> there is at least one
 * successor of <code>i</code> for which no path to the program exit exists that does not go
 * through <code>v</code>.</li>
 * </ol></p>
 *
 * <p>More information about this can be found in:
 * <i>Rajiv Gupta: Generalized Dominators and Post-dominators, ACM 1992.</i></p>
 *
 * @see PostDominators
 */
public class PrimaryPostDominators extends ForwardingMap<CFANode, Set<PrimaryPostDominator>> {

  private final Map<CFANode, Set<PrimaryPostDominator>> ppds;

  private PrimaryPostDominators(final Map<CFANode, Set<PrimaryPostDominator>> pPpds) {
    ppds = pPpds;
  }

  @Override
  protected Map<CFANode, Set<PrimaryPostDominator>> delegate() {
    return ppds;
  }


  public static PrimaryPostDominators create(final CFA pCfa) {
    final PostDominators postDominators = PostDominators.create(pCfa);
    return new Generator(pCfa, postDominators).create();
  }

  public static PrimaryPostDominators create(final CFA pCfa, final PostDominators pPostDominators) {
    return new Generator(pCfa, pPostDominators).create();
  }

  private static class Generator {

    private final CFA cfa;
    private final PostDominators pds;

    private Generator(final CFA pCfa, final PostDominators pPds) {
      cfa = pCfa;
      pds = pPds;
    }

    public PrimaryPostDominators create() {
      Multimap<CFANode, PrimaryPostDominator> ppds = HashMultimap.create();

      Set<CFANode> branchingNodes = cfa.getAllNodes().stream()
          .filter(n -> n.getNumLeavingEdges() > 1)
          .collect(Collectors.toSet());

      for (CFANode n : branchingNodes) {
        assert n.getNumLeavingEdges() == 2 : "There are nodes with more than two successors";

        CFANode firstSucc = n.getLeavingEdge(0).getSuccessor();
        CFANode sndSucc = n.getLeavingEdge(1).getSuccessor();

        Set<CFANode> fstSuccsDistinctPds = getDistinctPostDominators(firstSucc, n);
        Set<CFANode> sndSuccsDistinctPds = getDistinctPostDominators(sndSucc, n);

        for (CFANode fstDistinct : fstSuccsDistinctPds) {
          for (CFANode sndDistinct : sndSuccsDistinctPds) {
            Set<CFANode> fstDistinctsPds = pds.getPostDominators(fstDistinct);
            Set<CFANode> sndDistinctsPds = pds.getPostDominators(sndDistinct);

            if (!sndDistinctsPds.contains(fstDistinct) && !fstDistinctsPds.contains(sndDistinct)) {
              ppds.put(n, new PrimaryPostDominator().of(fstDistinct).of(sndDistinct));
            }
          }
        }
      }

      ImmutableMap.Builder<CFANode, Set<PrimaryPostDominator>> ppdMap = ImmutableMap.builder();
      for (CFANode key : ppds.keySet()) {
        ppdMap.put(key, ImmutableSet.copyOf(ppds.get(key)));
      }
      return new PrimaryPostDominators(ppdMap.build());
    }


    private Set<CFANode> getDistinctPostDominators(
        final CFANode pNode,
        final CFANode pMinusNode
    ) {
      Set<CFANode> minusNodesPds = pds.getPostDominators(pMinusNode);

      Set<CFANode> nodesPds = pds.getPostDominators(pNode);
      Set<CFANode> nodesDistinctPds = new HashSet<>(nodesPds);
      nodesDistinctPds.removeAll(minusNodesPds);

      return nodesDistinctPds;
    }

  }
}
