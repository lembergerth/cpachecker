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

import com.google.common.collect.ForwardingSet;

import org.sosy_lab.cpachecker.cfa.model.CFANode;

import java.util.HashSet;
import java.util.Set;

/**
 * A primary post dominator. There is no connection to any node, so on their own objects of this
 * class are quite useless.
 *
 * <p>A set <code>V</code> of nodes with more than one element (<code>|V| > 1</code>) is a primary
 * post-dominator, abbreviated as PDD, of node <code>i</code>, iff both of the following
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
 * @see PrimaryPostDominators
 */

class PrimaryPostDominator extends ForwardingSet<CFANode> {
  private final Set<CFANode> nodes;

  public PrimaryPostDominator() {
    nodes = new HashSet<>();
  }

  private PrimaryPostDominator(final Set<CFANode> pNodes) {
    nodes = pNodes;
  }

  public PrimaryPostDominator of(final CFANode pElement) {
    PrimaryPostDominator newPpd = new PrimaryPostDominator(nodes);
    newPpd.add(pElement);
    return newPpd;
  }

  @Override
  protected Set<CFANode> delegate() {
    return nodes;
  }
}
