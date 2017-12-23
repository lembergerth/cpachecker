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

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.NodeCollectingCFAVisitor;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.cpa.flowdep.FlowDependence;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Factory for creating a {@link DependenceGraph} from a {@link CFA}.
 */
@Options()
public class DGBuilder {

  private final CFA cfa;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;

  private Map<CFANode, DGNode> nodes;
  private Set<DGEdge> edges;


  @Option(secure = true, description = "File to export dependence graph to")
  private Path exportDgFile = new File("dg.dot").toPath();

  public DGBuilder(
      final CFA pCfa,
      final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownNotifier pShutdownNotifier
  ) throws InvalidConfigurationException {
    pConfig.inject(this);
    cfa = pCfa;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
  }

  public DependenceGraph create()
      throws InvalidConfigurationException, InterruptedException, CPAException {
    nodes = new HashMap<>();
    edges = new HashSet<>();
    addControlDependencies();
    addFlowDependencies();
    addUnconnectedNodes();
    Set<DGNode> rootNodes = addEdgeInformationToNodes();

    DependenceGraph dg = new DependenceGraph(new HashSet<>(nodes.values()), edges, rootNodes);
    export(dg);
    return dg;
  }

  private void addUnconnectedNodes() {
    for (CFANode n : cfa.getAllNodes()) {
      if (!nodes.containsKey(n)) {
        nodes.put(n, createNode(n));
      }
    }
  }

  /** Returns the set of all root nodes **/
  private Set<DGNode> addEdgeInformationToNodes() {
    Set<DGNode> incomingNodes = new HashSet<>();

    for (DGEdge e : edges) {
      DGNode startNode = e.getStart();
      DGNode endNode = e.getEnd();

      startNode.addOutgoingEdge(e);
      endNode.addIncomingEdge(e);
      incomingNodes.add(endNode);
    }
    Set<DGNode> rootNodes = new HashSet<>(nodes.values());
    rootNodes.removeAll(incomingNodes);
    return rootNodes;
  }


  private void addControlDependencies() throws InterruptedException {
    PostDominators postDoms = PostDominators.create(cfa);
    NodeCollectingCFAVisitor v = new NodeCollectingCFAVisitor();
    CFATraversal.dfs().traverse(cfa.getMainFunction(), v);
    Set<CFANode> branchingNodes = v.getVisitedNodes().stream()
        .filter(n -> n.getNumLeavingEdges() > 1)
        .filter(n -> n.getLeavingEdge(0) instanceof CAssumeEdge)
        .collect(Collectors.toSet());

    for (CFANode branch : branchingNodes) {
      DGNode nodeDependentOn = getDGNode(branch);
      List<CFANode> nodesOnPath = new LinkedList<>();
      Queue<CFANode> waitlist = new ArrayDeque<>(8);
      Set<CFANode> reached = new HashSet<>();
      pushAllSuccessors(waitlist, branch);
      while (!waitlist.isEmpty()) {
        CFANode current = waitlist.poll();
        if (reached.contains(current)) {
          continue;
        }
        reached.add(current);
        if (!postDoms.getPostDominators(branch).contains(current)) {
          if (isPostDomOfAll(current, nodesOnPath, postDoms) && !isBlank(current)) {
            DGNode nodeDepending = getDGNode(current);
            DGEdge controlDependency = new ControlDependenceEdge(nodeDependentOn, nodeDepending);
            edges.add(controlDependency);
            nodes.put(branch, nodeDependentOn);
            nodes.put(current, nodeDepending);
          }
          pushAllSuccessors(waitlist, current);
          nodesOnPath.add(current);

        } else {
          nodesOnPath.clear();
        }
      }

    }
  }

  private boolean isBlank(CFANode pCurrent) {
    for (CFAEdge e : CFAUtils.leavingEdges(pCurrent)) {
      if (e instanceof CFunctionSummaryEdge) {
        continue;
      }
      if (!(e instanceof BlankEdge)) {
        return false;
      }
    }
    return true;
  }

  private void pushAllSuccessors(Queue<CFANode> pStack, CFANode pNode) {
    for (CFANode succ : CFAUtils.successorsOf(pNode)) {
      if (!pStack.contains(succ)) {
        pStack.offer(succ);
      }
    }
  }

  private boolean isPostDomOfAll(
      final CFANode pNode,
      final Collection<CFANode> pNodeSet,
      final PostDominators pPostDominators
  ) {

    for (CFANode n : pNodeSet) {
      if (!pPostDominators.getPostDominators(n).contains(pNode)) {
        return false;
      }
    }
    return true;
  }

  private void addFlowDependencies()
      throws InvalidConfigurationException, InterruptedException, CPAException {
    org.sosy_lab.cpachecker.util.dependencegraph.FlowDependences
        flowDependences = org.sosy_lab.cpachecker.util.dependencegraph.FlowDependences
        .factory().create(cfa, logger, shutdownNotifier);

    for (Entry<CFANode, Set<FlowDependence>> e : flowDependences.entrySet()) {
      CFANode key = e.getKey();
      DGNode dependentNode = getDGNode(key);

      for (FlowDependence d : e.getValue()) {
        for (CFANode nodeDependentOn : d) {
          DGEdge newEdge = new FlowDependenceEdge(getDGNode(nodeDependentOn), dependentNode);
          edges.add(newEdge);
        }
      }
    }
  }

  private DGNode getDGNode(final CFANode pCfaNode) {
    if (!nodes.containsKey(pCfaNode)) {
      nodes.put(pCfaNode, createNode(pCfaNode));
    }
    return nodes.get(pCfaNode);
  }

  private DGNode createNode(final CFANode pCfaNode) {
    if (pCfaNode instanceof FunctionExitNode) {
      return new DGSimpleNode(pCfaNode.getNodeNumber(), "Function exit");

    } else if (pCfaNode instanceof FunctionEntryNode){
      return new DGFunctionCallNode(pCfaNode.getNodeNumber(), pCfaNode.getLeavingEdge(0).getDescription());
    } else {
      Set<CFAEdge> leavingEdges = CFAUtils.leavingEdges(pCfaNode).toSet();

      if (leavingEdges.stream().anyMatch(n -> n instanceof CAssumeEdge)) {
        return new DGAssumptionNode(pCfaNode.getNodeNumber(), getASTString(pCfaNode));

      } else {
        return new DGAssignmentNode(
            pCfaNode.getNodeNumber(), pCfaNode.getLeavingEdge(0).getDescription());
      }
    }
  }

  private String getASTString(CFANode pCfaNode) {
    for (CFAEdge e : CFAUtils.leavingEdges(pCfaNode)) {
      assert e.getEdgeType() == CFAEdgeType.AssumeEdge;
      if (((CAssumeEdge) e).getTruthAssumption()) {
        return e.getDescription();
      }
    }

    throw new AssertionError("There must be at least one truth assumption!");
  }

  private void export(DependenceGraph pDg) {
    try (Writer w = IO.openOutputFile(exportDgFile, Charset.defaultCharset())) {
      DGExporter.generateDOT(w, pDg, cfa);
    } catch (IOException e) {
      logger.logUserException(Level.WARNING, e,
          "Could not write dependence graph to dot file");
      // continue with analysis
    }
  }
}
