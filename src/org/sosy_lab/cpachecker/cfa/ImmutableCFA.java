/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.LiveVariables;
import org.sosy_lab.cpachecker.util.LoopStructure;
import org.sosy_lab.cpachecker.util.dependencegraph.DependenceGraph;
import org.sosy_lab.cpachecker.util.variableclassification.VariableClassification;

/**
 * This class represents a CFA after it has been fully created (parsing, linking
 * of functions, etc.).
 */
class ImmutableCFA implements CFA, Serializable {

  private static final long serialVersionUID = 5399965350156780812L;
  private final MachineModel machineModel;
  private final ImmutableSortedMap<String, FunctionEntryNode> functions;
  private final ImmutableSortedSet<CFANode> allNodes;
  private final FunctionEntryNode mainFunction;
  private final @Nullable LoopStructure loopStructure;
  private final @Nullable VariableClassification varClassification;
  private final @Nullable LiveVariables liveVariables;
  private final @Nullable DependenceGraph dependenceGraph;
  private final ImmutableList<Path> fileNames;
  private final Language language;

  ImmutableCFA(
      MachineModel pMachineModel,
      Map<String, FunctionEntryNode> pFunctions,
      SetMultimap<String, CFANode> pAllNodes,
      FunctionEntryNode pMainFunction,
      Optional<LoopStructure> pLoopStructure,
      Optional<VariableClassification> pVarClassification,
      Optional<LiveVariables> pLiveVariables,
      Optional<DependenceGraph> pDependenceGraph,
      List<Path> pFileNames,
      Language pLanguage) {

    machineModel = pMachineModel;
    functions = ImmutableSortedMap.copyOf(pFunctions);
    allNodes = ImmutableSortedSet.copyOf(pAllNodes.values());
    mainFunction = checkNotNull(pMainFunction);
    loopStructure = pLoopStructure.orElse(null);
    varClassification = pVarClassification.orElse(null);
    liveVariables = pLiveVariables.orElse(null);
    dependenceGraph = pDependenceGraph.orElse(null);
    fileNames = ImmutableList.copyOf(pFileNames);
    language = pLanguage;

    checkArgument(functions.get(mainFunction.getFunctionName()) == mainFunction);
  }

  private ImmutableCFA(MachineModel pMachineModel, Language pLanguage) {
    machineModel = pMachineModel;
    functions = ImmutableSortedMap.of();
    allNodes = ImmutableSortedSet.of();
    mainFunction = null;
    loopStructure = null;
    varClassification = null;
    liveVariables = null;
    dependenceGraph = null;
    fileNames = ImmutableList.of();
    language = pLanguage;
  }

  static ImmutableCFA empty(MachineModel pMachineModel, Language pLanguage) {
    return new ImmutableCFA(pMachineModel, pLanguage);
  }

  @Override
  public MachineModel getMachineModel() {
    return machineModel;
  }

  @Override
  public boolean isEmpty() {
    return functions.isEmpty();
  }

  @Override
  public int getNumberOfFunctions() {
    return functions.size();
  }

  @Override
  public ImmutableSortedSet<String> getAllFunctionNames() {
    return functions.keySet();
  }

  @Override
  public ImmutableCollection<FunctionEntryNode> getAllFunctionHeads() {
    return functions.values();
  }

  @Override
  public FunctionEntryNode getFunctionHead(String name) {
    return functions.get(name);
  }

  @Override
  public ImmutableSortedMap<String, FunctionEntryNode> getAllFunctions() {
    return functions;
  }

  @Override
  public ImmutableSortedSet<CFANode> getAllNodes() {
    return allNodes;
  }

  @Override
  public FunctionEntryNode getMainFunction() {
    return mainFunction;
  }

  @Override
  public Optional<LoopStructure> getLoopStructure() {
    return Optional.ofNullable(loopStructure);
  }

  @Override
  public Optional<ImmutableSet<CFANode>> getAllLoopHeads() {
    if (loopStructure != null) {
      return Optional.of(loopStructure.getAllLoopHeads());
    }
    return Optional.empty();
  }

  @Override
  public Optional<VariableClassification> getVarClassification() {
    return Optional.ofNullable(varClassification);
  }

  @Override
  public Optional<LiveVariables> getLiveVariables() {
    return Optional.ofNullable(liveVariables);
  }

  @Override
  public Optional<DependenceGraph> getDependenceGraph() {
    return Optional.ofNullable(dependenceGraph);
  }

  @Override
  public Language getLanguage() {
    return language;
  }

  @Override
  public List<Path> getFileNames() {
    return fileNames;
  }

  private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {

    // write default stuff
    s.defaultWriteObject();

    // we have to keep the order of edges 'AS IS'
    final List<CFAEdge> enteringEdges = new ArrayList<>();
    for (CFANode node : allNodes) {
      Iterables.addAll(enteringEdges, CFAUtils.enteringEdges(node));
    }
    s.writeObject(enteringEdges);

    // we have to keep the order of edges 'AS IS'
    final List<CFAEdge> leavingEdges = new ArrayList<>();
    for (CFANode node : allNodes) {
      Iterables.addAll(leavingEdges, CFAUtils.leavingEdges(node));
    }
    s.writeObject(leavingEdges);
  }

  @SuppressWarnings("unchecked")
  private void readObject(java.io.ObjectInputStream s)
      throws java.io.IOException, ClassNotFoundException {

    // read default stuff
    s.defaultReadObject();

    // read entering edges, we have to keep the order of edges 'AS IS'
    for (CFAEdge edge : (List<CFAEdge>) s.readObject()) {
      edge.getSuccessor().addEnteringEdge(edge);
    }

    // read leaving edges, we have to keep the order of edges 'AS IS'
    for (CFAEdge edge : (List<CFAEdge>) s.readObject()) {
      edge.getPredecessor().addLeavingEdge(edge);
    }
  }
}
