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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.CFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.NodeCollectingCFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;
import org.sosy_lab.cpachecker.util.CFAUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Map of {@link CFANode CFANodes} to their post-dominators.
 *
 * <p>
 *   Node <code>I</code> is post-dominated by node <code>J</code> if every path from
 *   <code>I</code> to the program exit goes
 *   through <code>J</code>.
 * </p>
 *
 * @see PrimaryPostDominators
 */
public class PostDominators {

  private Map<CFANode, Set<CFANode>> postDominatorMap;

  private PostDominators(final Map<CFANode, Set<CFANode>> pPostDominatorMap) {
    postDominatorMap = pPostDominatorMap;
  }

  public static PostDominators create(final CFA pCfa) {
    ComputationRun singleComputationRun = new ComputationRun(pCfa);
    do {
      singleComputationRun.resetChanged();
      CFAVisitor cfaVisitor = new NodeCollectingCFAVisitor(singleComputationRun);
      CFATraversal.dfs().backwards()
          .traverse(pCfa.getMainFunction().getExitNode(), cfaVisitor);
    } while (singleComputationRun.didChangeOccur());

    return new PostDominators(singleComputationRun.getPostDominators());
  }

  /**
   * Get all post-dominators of the given node.
   *
   * <p>
   *   Node <code>I</code> is post-dominated by node <code>J</code> if every path from
   *   <code>I</code> to the program exit goes
   *   through <code>J</code>.
   * </p>
   *
   * <p>That means that every program path from the given node to the program exit has to go
   * through each node that is in the returned collection</p>
   *
   */
  public Set<CFANode> getPostDominators(final CFANode pNode) {
    checkState(postDominatorMap.containsKey(pNode), "Node " + pNode + " not in post-dominator map");
    return postDominatorMap.get(pNode);
  }

  private static class ComputationRun implements CFAVisitor {

    private Map<CFANode, Set<CFANode>> postDoms = new HashMap<>();
    private boolean candidatesGotChanged = false;
    private final Set<CFANode> allNodes;
    private final CFANode exitNode;

    public ComputationRun(final CFA pCfa) {
      allNodes = ImmutableSet.copyOf(pCfa.getAllNodes());
      exitNode = pCfa.getMainFunction().getExitNode();
    }

    public Map<CFANode, Set<CFANode>> getPostDominators() {
      checkState(!postDoms.isEmpty());
      // Build an immutable map of the mutable existing one.
      ImmutableMap.Builder<CFANode, Set<CFANode>> mapBuilder = ImmutableMap.builder();

      for (Map.Entry<CFANode, Set<CFANode>> e : postDoms.entrySet()) {
        mapBuilder.put(e.getKey(), ImmutableSet.copyOf(e.getValue()));
      }

      return mapBuilder.build();
    }

    public boolean didChangeOccur() {
      return candidatesGotChanged;
    }

    @Override
    public TraversalProcess visitEdge(final CFAEdge edge) {
      return TraversalProcess.CONTINUE;
    }

    /**
     * Assign a set of candidate post-dominators to the given node.
     *
     * <p>The set <code>PD(i)</code> of candidate post-dominators for a node <code>i</code> is
     * <code>PD(i) = {i} &cup; \u22C2 PD(succs(i)) </code> where <code>succs(i)</code> is the
     * set of all successors of <code>i</code>.
     * Initially, <code>PD(j)</code> contains all nodes.</p>
     */
    @Override
    public TraversalProcess visitNode(final CFANode pNode) {
      Set<CFANode> oldPostDoms;
      if (postDoms.containsKey(pNode)) {
        oldPostDoms = postDoms.get(pNode);
      } else {
        oldPostDoms = null;
      }

      FluentIterable<CFANode> succs = CFAUtils.allSuccessorsOf(pNode);
      Set<CFANode> newPostDoms;
      if (pNode.equals(exitNode)) {
        // The exit node always only contains the exit node.
        // Otherwise, we won't reduce the initial set of all nodes for all other nodes.
        newPostDoms = Collections.singleton(pNode);
      } else {
        // Otherwise, initialize with all nodes so that the first iteration in the loop below
        // doesn't have to
        // be handled in a special way and we don't have to handle the initial round, in which
        // all nodes are initialized with the set of all nodes, in a separate case.
        newPostDoms = allNodes;
      }
      // Get the intersection of the post-dominators of all successors of 'node'
      for (CFANode s : succs) {
        // if a successor is not yet initialized, we just initialize it.
        if (!postDoms.containsKey(s)) {
          postDoms.put(s, allNodes);
        }

        newPostDoms = new HashSet<>(Sets.intersection(newPostDoms, postDoms.get(s)));
      }

      if (!newPostDoms.contains(pNode)) {
        newPostDoms.add(pNode); // every node post-dominates itself in our notion
      }
      postDoms.put(pNode, newPostDoms);

      // If the node was changed, consider (re-)computing its successors.
      // Otherwise, they can be skipped.
      if (oldPostDoms == null || !oldPostDoms.equals(newPostDoms)) {
        candidatesGotChanged = true;
      }
      return TraversalProcess.CONTINUE;
    }

    public void resetChanged() {
      candidatesGotChanged = false;
    }
  }
}
