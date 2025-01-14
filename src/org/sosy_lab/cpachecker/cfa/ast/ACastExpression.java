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
package org.sosy_lab.cpachecker.cfa.ast;

import java.util.Objects;
import org.sosy_lab.cpachecker.cfa.types.Type;

/**
 * This is the abstract Class for  Casted Expressions.
 *
 */
public abstract class ACastExpression extends AbstractLeftHandSide {

  private static final long serialVersionUID = 7047818239785351507L;
  private final AExpression operand;
  private final Type     castType;


  public ACastExpression(FileLocation pFileLocation, Type castExpressionType, AExpression pOperand) {
    super(pFileLocation, castExpressionType);

    operand = pOperand;
    castType = castExpressionType;
  }

  public AExpression getOperand() {
    return operand;
  }

  @Override
  public String toASTString() {
    return "(" + getExpressionType().toASTString("") + ")" + operand.toParenthesizedASTString();
  }

  public Type getCastType() {
    return castType;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 7;
    result = prime * result + Objects.hashCode(castType);
    result = prime * result + Objects.hashCode(operand);
    result = prime * result + super.hashCode();
    return result;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof ACastExpression)
        || !super.equals(obj)) {
      return false;
    }

    ACastExpression other = (ACastExpression) obj;

    return Objects.equals(other.operand, operand)
            && Objects.equals(other.castType, castType);
  }

}
