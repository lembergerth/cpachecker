/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.smg.evaluator;

import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGAddressAndState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

class RHSLValueAssignmentVisitor extends LValueAssignmentVisitor {

  public RHSLValueAssignmentVisitor(SMGExpressionEvaluator pSmgExpressionEvaluator, CFAEdge pEdge, SMGState pSmgState) {
    super(pSmgExpressionEvaluator, pEdge, pSmgState);
  }

  @Override
  public List<SMGAddressAndState> visit(CPointerExpression pLValue)
      throws CPATransferException {
    List<SMGAddressAndState> results = new ArrayList<>();
    for (SMGAddressAndState address : super.visit(pLValue)) {
      if (address.getObject().isUnknown()) {
        address = SMGAddressAndState.of(address.getSmgState().setUnknownDereference());
      }
      results.add(address);
    }
    return results;
  }
}