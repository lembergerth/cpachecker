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

/**
 * Formats nodes for the GraphViz dot format.
 */
public class DGNodeDotFormatter implements DGNodeVisitor<String> {

  @Override
  public String visit(DGAssignmentNode pNode) {
    return format(pNode, "circle");
  }

  @Override
  public String visit(DGAssumptionNode pNode) {
    return format(pNode, "diamond");
  }

  @Override
  public String visit(DGFunctionCallNode pNode) {
    return format(pNode, "doublecircle");
  }

  @Override
  public String visit(DGSimpleNode pNode) {
    return format(pNode, "box");
  }

  private String format(DGNode pNode, String pShape) {
    return "N" + pNode.getCfaNodeNumber() + " [shape=\"" + pShape + "\","
        + "label=\"" + escape(pNode.getStatement()) + "\"" + "]";
  }

  private String escape(String pStatement) {
    return pStatement.replaceAll("\\\"", "\\\\\"");
  }

}
