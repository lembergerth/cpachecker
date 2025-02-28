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
package org.sosy_lab.cpachecker.cfa.ast.java;

import java.util.List;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.types.java.JClassType;

/**
 * This class represents the super constructor invocation statement AST node type.
 *
 * SuperConstructorInvocation:
 *    [ Expression . ]
 *        [ < Type { , Type } > ]
 *        super ( [ Expression { , Expression } ] ) ;
 *
 */
public final class JSuperConstructorInvocation extends JClassInstanceCreation {

  private static final long serialVersionUID = 1241406733020430434L;

  public JSuperConstructorInvocation(FileLocation pFileLocation, JClassType pType, JExpression pFunctionName,
      List<? extends JExpression> pParameters, JConstructorDeclaration pDeclaration) {
    super(pFileLocation, pType, pFunctionName, pParameters, pDeclaration);

  }

  @Override
  public String toASTString() {
    return getExpressionType().toASTString("super");
  }

  @Override
  public int hashCode() {
    int prime = 31;
    int result = 7;
    return prime * result + super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof JSuperConstructorInvocation)) {
      return false;
    }

    return super.equals(obj);
  }
}
