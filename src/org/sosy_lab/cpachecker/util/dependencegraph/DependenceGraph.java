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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.util.Pair;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Dependence graph that describes flow dependence and control dependence between
 * expressions and assignments of a program.
 *
 * <p>
 *   A dependence graph G = (V, E) is a directed graph.
 *   His nodes V are statements and expressions of the program.
 *   Given two nodes i and j, if i is dependent on j, a directed edge (j, i) from j to i is in E.
 * </p>
 */
public class DependenceGraph implements Serializable {

  private static final long serialVersionUID = 1;

  public enum SubgraphDirection { FORWARD, BACKWARD, FULL }


  // CFA node number -> DGNode
  private final ImmutableMap<Integer, DGNode> nodes;
  private final ImmutableSet<DGEdge> edges;
  private final ImmutableSet<DGNode> rootNodes;

  private final DGNode startNode;

  public DependenceGraph(
      final Set<DGNode> pNodes,
      final Set<DGEdge> pEdges,
      final Set<DGNode> pRootNodes
  ) {
    this(pNodes, pEdges, pRootNodes, null);
  }

  public DependenceGraph(
      final Set<DGNode> pNodes,
      final Set<DGEdge> pEdges,
      final Set<DGNode> pRootNodes,
      final DGNode pStartNode
  ) {

    edges = ImmutableSet.copyOf(pEdges);
    rootNodes = ImmutableSet.copyOf(pRootNodes);
    ImmutableMap.Builder<Integer, DGNode> nodeMapBuilder = ImmutableMap.builder();
    for (DGNode n : pNodes) {
      nodeMapBuilder.put(n.getCfaNodeNumber(), n);
    }
    nodes = nodeMapBuilder.build();
    startNode = pStartNode;
  }

  public Set<DGEdge> getEdges() {
    return edges;
  }

  public boolean contains(final DGNode pStartingNode) {
    return nodes.containsKey(pStartingNode.getCfaNodeNumber());
  }

  public Collection<DGNode> getNodes() {
    return nodes.values();
  }

  public DGNode getNode(CFANode pLocation) {
    return nodes.get(pLocation.getNodeNumber());
  }

  public boolean hasStartNode() {
    return startNode != null;
  }

  public DGNode getStartNode() {
    return checkNotNull(startNode, "Start node is queried, but no start node is defined");
  }

  public DependenceGraph getSubgraph(DGNode pRootNode, SubgraphDirection pDirection) {
    return getSubgraph(pRootNode, pDirection, edges.size());
  }

  public DependenceGraph getSubgraph(DGNode pRootNode, SubgraphDirection pDirection, int pDepth) {
    DGNode root = nodes.get(pRootNode.getCfaNodeNumber());
    Pair<Set<DGNode>, Set<DGEdge>> allNodesAndEdges = getSubgraph0(root, pDirection, 0, pDepth);
    Set<DGNode> subgraphNodes = allNodesAndEdges.getFirst();
    Set<DGEdge> subgraphEdges = allNodesAndEdges.getSecond();

    return new DependenceGraph(
        subgraphNodes, subgraphEdges, computeRootNodes(subgraphEdges, subgraphNodes), pRootNode);
  }

  private Pair<Set<DGNode>, Set<DGEdge>> getSubgraph0(
      final DGNode pRoot,
      final SubgraphDirection pDirection,
      final int pCurrentDepth,
      final int pMaxDepth
  ) {
    Set<DGNode> subgraphNodes = new HashSet<>();
    Set<DGEdge> subgraphEdges = new HashSet<>();
    subgraphNodes.add(pRoot);

    assert pCurrentDepth <= pMaxDepth;
    if (pCurrentDepth < pMaxDepth) {
      Collection<DGEdge> adjacentEdges = getAdjacentEdges(pRoot, pDirection);
      for (DGEdge e : adjacentEdges) {
        subgraphEdges.add(e);
        if (!subgraphNodes.contains(e.getEnd())) {
          Pair<Set<DGNode>, Set<DGEdge>> nextSgPair =
              getSubgraph0(e.getEnd(), pDirection, pCurrentDepth + 1, pMaxDepth);
          subgraphNodes.addAll(nextSgPair.getFirst());
          subgraphEdges.addAll(nextSgPair.getSecond());
        }
      }
    }

    return Pair.of(subgraphNodes, subgraphEdges);
  }

  private Collection<DGEdge> getAdjacentEdges(DGNode pNode, SubgraphDirection pDirection) {
    switch (pDirection) {
      case FORWARD:
        return pNode.getOutgoingEdges();
      case BACKWARD:
        return pNode.getIncomingEdges();
      case FULL:
        Collection<DGEdge> all = pNode.getOutgoingEdges();
        all.addAll(pNode.getIncomingEdges());
        return all;
      default:
        throw new AssertionError("Unhandled direction " + pDirection);
    }
  }

  public DependenceGraph getInverse() {
    Set<DGEdge> newEdges = new HashSet<>();
    for (DGEdge e : edges) {
      DGEdge newE = e.getInverse();
      newEdges.add(newE);
    }
    Set<DGNode> rootNodesOfInverse = computeRootNodes(newEdges, nodes.values());
    return new DependenceGraph(new HashSet<>(nodes.values()), newEdges, rootNodesOfInverse);
  }

  public DependenceGraph join(final DependenceGraph pOther) {
    Set<DGEdge> newEdges = new HashSet<>(edges);
    newEdges.addAll(pOther.getEdges());

    Set<DGNode> newNodes = new HashSet<>(getNodes());
    newNodes.addAll(pOther.getNodes());
    Set<DGNode> newRootNodes = new HashSet<>(newNodes);

    for (DGEdge e : newEdges) {
      newRootNodes.remove(e.getEnd());
    }

    return new DependenceGraph(newNodes, newEdges, newRootNodes);
  }

  private Set<DGNode> computeRootNodes(
      final Set<DGEdge> pSubgraphEdges,
      final Collection<DGNode> pSubgraphNodes
  ) {
    Set<DGNode> rootNodesOfSubgraph = new HashSet<>(pSubgraphNodes);
    for (DGEdge e : pSubgraphEdges) {
      rootNodesOfSubgraph.remove(e.getEnd());
    }
    return rootNodesOfSubgraph;
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO == null || getClass() != pO.getClass()) {
      return false;
    }
    DependenceGraph that = (DependenceGraph) pO;
    // If these equal, the root nodes have to equal, too.
    return Objects.equals(nodes, that.nodes) && Objects.equals(edges, that.edges);
  }

  @Override
  public int hashCode() {
    return Objects.hash(nodes, edges);
  }
}
