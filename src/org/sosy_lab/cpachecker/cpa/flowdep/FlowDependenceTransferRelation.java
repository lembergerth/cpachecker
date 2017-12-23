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
package org.sosy_lab.cpachecker.cpa.flowdep;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayRangeDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNodeVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDefDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JStatement;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefState;
import org.sosy_lab.cpachecker.cpa.reachdef.ReachingDefTransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.dependencegraph.UsedIdsCollector;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Transfer relation of {@link FlowDependenceCPA}.
 */
class FlowDependenceTransferRelation
    extends SingleEdgeTransferRelation {

  private final DefinitionsCollector definitionsCollector = new DefinitionsCollector();
  private UsesCollector usesCollector = new UsesCollector();

  private final ReachingDefTransferRelation reachDefRelation;

  FlowDependenceTransferRelation(ReachingDefTransferRelation pReachDefRelation) {
    reachDefRelation = pReachDefRelation;
  }

  @Override
  protected FlowDependenceState handleDeclarationEdge(
      final CDeclarationEdge pCfaEdge,
      final CDeclaration pDecl
  ) throws CPATransferException {
    return handleAstNode(pCfaEdge, pDecl);
  }

  private FlowDependenceState handleAstNode(final CFAEdge pCfaEdge, final CAstNode pNode)
      throws CPATransferException {

    String currFunctionName = getFunctionName();
    Set<MemoryLocation> usedLocs = getUsedMemLocs(pNode, currFunctionName);

    FlowDependence dependence = new FlowDependence();
    for (MemoryLocation use : usedLocs) {
      dependence.put(use, state.getDefinitionPoint(use));
    }

    Optional<MemoryLocation> maybeMemLoc = getDefinedMemLoc(pNode, currFunctionName);
    if (maybeMemLoc.isPresent()) {
      return state.putNewDefinition(maybeMemLoc.get(), pCfaEdge);
    } else {
      return state;
    }
  }

  private Set<MemoryLocation> getUsedMemLocs(final CAstNode pNode, final String pFuncName)
      throws CPATransferException {
    Set<CSimpleDeclaration> declarationsOfUsed = pNode.accept(usesCollector);
    Set<MemoryLocation> usedMemLocs = new HashSet<>();
    for (CSimpleDeclaration d : declarationsOfUsed) {
      MemoryLocation memLoc = MemoryLocation.valueOf(d.getQualifiedName());
      usedMemLocs.add(memLoc);
    }

    return usedMemLocs;
  }

  private Optional<MemoryLocation> getDefinedMemLoc(final CAstNode pNode, final String pFuncName)
      throws CPATransferException {

    Optional<CSimpleDeclaration> maybeDeclaration = pNode.accept(definitionsCollector);
    if (maybeDeclaration.isPresent()) {
      CSimpleDeclaration decl = maybeDeclaration.get();
      MemoryLocation memLoc = MemoryLocation.valueOf(decl.getQualifiedName());
      return Optional.of(memLoc);

    } else {
      return Optional.empty();
    }
  }

  @Override
  protected FlowDependenceState handleDeclarationEdge(
      JDeclarationEdge cfaEdge, JDeclaration decl) throws CPATransferException {
    // TODO implement for java
    return super.handleDeclarationEdge(cfaEdge, decl);
  }

  @Override
  protected FlowDependenceState handleStatementEdge(
      final CStatementEdge pCfaEdge,
      final CStatement pStatement
  ) throws CPATransferException {
    return handleAstNode(pCfaEdge, pStatement);
  }

  @Override
  protected FlowDependenceState handleStatementEdge(
      JStatementEdge cfaEdge, JStatement statement) throws CPATransferException {
    // TODO implement for java
    return super.handleStatementEdge(cfaEdge, statement);
  }

  @Override
  protected FlowDependenceState handleReturnStatementEdge(AReturnStatementEdge cfaEdge)
      throws CPATransferException {
    return super.handleReturnStatementEdge(cfaEdge);
  }

  @Override
  protected FlowDependenceState handleReturnStatementEdge(CReturnStatementEdge pCfaEdge)
      throws CPATransferException {
    com.google.common.base.Optional<CAssignment> returnAssignment = pCfaEdge.asAssignment();
    if (returnAssignment.isPresent()) {
      return handleAstNode(pCfaEdge, returnAssignment.get());
    } else {
      return state;
    }
  }

  @Override
  protected FlowDependenceState handleReturnStatementEdge(JReturnStatementEdge cfaEdge)
      throws CPATransferException {
    // TODO implement for java
    return super.handleReturnStatementEdge(cfaEdge);
  }

  @Nullable
  @Override
  protected FlowDependenceState handleAssumption(
      CAssumeEdge cfaEdge, CExpression expression, boolean truthAssumption)
      throws CPATransferException {
    return handleAstNode(cfaEdge, expression);
  }

  @Override
  protected FlowDependenceState handleFunctionCallEdge(
      CFunctionCallEdge pCfa,
      List<CExpression> pArguments,
      List<CParameterDeclaration> pParameters,
      String pCalledFunctionName
  ) throws CPATransferException {
    FlowDependenceState newState = state;
    for (CParameterDeclaration d : pParameters) {
      MemoryLocation memLoc = MemoryLocation.valueOf(pCalledFunctionName, d.getName());
      newState = newState.putNewDefinition(memLoc, pCfa);
    }
    return newState;
  }

  @Override
  protected FlowDependenceState handleFunctionReturnEdge(
      CFunctionReturnEdge cfaEdge,
      CFunctionSummaryEdge fnkCall,
      CFunctionCall summaryExpr,
      String callerFunctionName
  ) throws CPATransferException {
    String leftFunctionName = cfaEdge.getPredecessor().getFunctionName();
    Optional<MemoryLocation> maybeMemLoc = getDefinedMemLoc(summaryExpr, callerFunctionName);
    if (maybeMemLoc.isPresent()) {
      return state.putNewDefinition(maybeMemLoc.get(), fnkCall).dropFunction(leftFunctionName);
    } else {
      return state;
    }
  }

  @Override
  protected FlowDependenceState handleBlankEdge(BlankEdge cfaEdge) {
    return state;
  }

  @Override
  protected FlowDependenceState handleFunctionSummaryEdge(FunctionSummaryEdge cfaEdge)
      throws CPATransferException {
    return state;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {

    FlowDependenceState oldState = (FlowDependenceState) state;
    Collection<? extends AbstractState> computedReachDefStates =
        reachDefRelation.getAbstractSuccessorsForEdge(oldState.getReachDefState(), precision, cfaEdge);

    if (computedReachDefStates.isEmpty()) {
      return Collections.emptySet();
    } else {
      ReachingDefState newReachDefState =
          (ReachingDefState) Iterables.getOnlyElement(computedReachDefStates);
    }

    FlowDependenceState nextState;
    switch (cfaEdge.getEdgeType()) {
      case DeclarationEdge:
        CDeclarationEdge declEdge = (CDeclarationEdge) cfaEdge;
        nextState = handleDeclarationEdge(declEdge, declEdge.getDeclaration());
        break;
      case StatementEdge:
        CStatementEdge stmtEdge = (CStatementEdge) cfaEdge;
        nextState = handleStatementEdge(stmtEdge, stmtEdge.getStatement());
        break;
      default:
        nextState = null;
        break;
    }

    if (nextState != null) {
      return ImmutableSet.of(nextState);
    } else {
      return ImmutableSet.of(oldState);
    }
  }

  /**
   *
   * Visitor that returns the declaration of the defined identifier, if one is defined in the
   * visited node.
   */
  private static class DefinitionsCollector
      implements CAstNodeVisitor<Optional<CSimpleDeclaration>, CPATransferException> {

    @Override
    public Optional<CSimpleDeclaration> visit(CFunctionDeclaration pDecl)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CComplexTypeDeclaration pDecl)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CTypeDefDeclaration pDecl)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CVariableDeclaration pDecl)
        throws CPATransferException {
      return Optional.of(pDecl);
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CParameterDeclaration pDecl)
        throws CPATransferException {
      return pDecl.asVariableDeclaration().accept(this);
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CEnumerator pDecl) throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CExpressionStatement pIastExpressionStatement)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CExpressionAssignmentStatement pStmt)
        throws CPATransferException {
      CLeftHandSide lhs = pStmt.getLeftHandSide();
      if (lhs instanceof CIdExpression) {
        return Optional.of(((CIdExpression) lhs).getDeclaration());
      } else {
        return Optional.empty();
      }
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CFunctionCallAssignmentStatement pStmt)
        throws CPATransferException {
      CLeftHandSide lhs = pStmt.getLeftHandSide();
      if (lhs instanceof CIdExpression) {
        return Optional.of(((CIdExpression) lhs).getDeclaration());
      } else {
        return Optional.empty();
      }
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CFunctionCallStatement pIastFunctionCallStatement)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CArrayDesignator pArrayDesignator)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CArrayRangeDesignator pArrayRangeDesignator)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CFieldDesignator pFieldDesignator)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CArraySubscriptExpression pIastArraySubscriptExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CFieldReference pIastFieldReference)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CIdExpression pIastIdExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CPointerExpression pointerExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CComplexCastExpression complexCastExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CInitializerExpression pInitializerExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CInitializerList pInitializerList)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CDesignatedInitializer pCStructInitializerPart)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CFunctionCallExpression pIastFunctionCallExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CBinaryExpression pIastBinaryExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CCastExpression pIastCastExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CCharLiteralExpression pIastCharLiteralExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CFloatLiteralExpression pIastFloatLiteralExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CIntegerLiteralExpression pIastIntegerLiteralExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CStringLiteralExpression pIastStringLiteralExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CTypeIdExpression pIastTypeIdExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CUnaryExpression pIastUnaryExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CImaginaryLiteralExpression PIastLiteralExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CAddressOfLabelExpression pAddressOfLabelExpression)
        throws CPATransferException {
      return Optional.empty();
    }

    @Override
    public Optional<CSimpleDeclaration> visit(CReturnStatement pNode) throws CPATransferException {
      com.google.common.base.Optional<CAssignment> assignment = pNode.asAssignment();
      return assignment.isPresent() ? assignment.get().accept(this) : Optional.empty();
    }
  }

  private static class UsesCollector
      implements CAstNodeVisitor<Set<CSimpleDeclaration>, CPATransferException> {

    private final UsedIdsCollector idCollector = new UsedIdsCollector();

    @Override
    public Set<CSimpleDeclaration> visit(CExpressionStatement pStmt)
        throws CPATransferException {
      return pStmt.getExpression().accept(new UsedIdsCollector());
    }

    @Override
    public Set<CSimpleDeclaration> visit(CExpressionAssignmentStatement pStmt)
        throws CPATransferException {
      Set<CSimpleDeclaration> used = new HashSet<>();
      used.addAll(pStmt.getRightHandSide().accept(this));
      if (!(pStmt.getLeftHandSide() instanceof CIdExpression)) {
        used.addAll(pStmt.getLeftHandSide().accept(this));
      }

      return used;
    }

    @Override
    public Set<CSimpleDeclaration> visit(CFunctionCallAssignmentStatement pStmt)
        throws CPATransferException {
      Set<CSimpleDeclaration> used = new HashSet<>();
      used.addAll(pStmt.getRightHandSide().accept(this));
      if (!(pStmt.getLeftHandSide() instanceof CIdExpression)) {
        used.addAll(pStmt.getLeftHandSide().accept(this));
      }

      return used;
    }

    @Override
    public Set<CSimpleDeclaration> visit(CFunctionCallStatement pStmt)
        throws CPATransferException {
      Set<CSimpleDeclaration> paramDecls = new HashSet<>();
     for (CExpression p : pStmt.getFunctionCallExpression().getParameterExpressions()) {
       paramDecls.addAll(p.accept(this));
     }
     return paramDecls;
    }

    @Override
    public Set<CSimpleDeclaration> visit(CArrayDesignator pArrayDesignator)
        throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<CSimpleDeclaration> visit(CArrayRangeDesignator pArrayRangeDesignator)
        throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<CSimpleDeclaration> visit(CFieldDesignator pFieldDesignator)
        throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<CSimpleDeclaration> visit(CArraySubscriptExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CFieldReference pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CIdExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CPointerExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CComplexCastExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CInitializerExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CInitializerList pInitializerList)
        throws CPATransferException {
      Set<CSimpleDeclaration> uses = new HashSet<>();
      for (CInitializer i : pInitializerList.getInitializers()) {
        uses.addAll(i.accept(this));
      }
      return uses;
    }

    @Override
    public Set<CSimpleDeclaration> visit(CDesignatedInitializer pExp)
        throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<CSimpleDeclaration> visit(CFunctionCallExpression pExp)
        throws CPATransferException {
      Set<CSimpleDeclaration> useds = new HashSet<>();
      for (CExpression p : pExp.getParameterExpressions()) {
        useds.addAll(p.accept(idCollector));
      }
      return useds;
    }

    @Override
    public Set<CSimpleDeclaration> visit(CBinaryExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CCastExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CCharLiteralExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CFloatLiteralExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CIntegerLiteralExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CStringLiteralExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CTypeIdExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CUnaryExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CImaginaryLiteralExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CAddressOfLabelExpression pExp)
        throws CPATransferException {
      return pExp.accept(idCollector);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CFunctionDeclaration pDecl) throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<CSimpleDeclaration> visit(CComplexTypeDeclaration pDecl)
        throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<CSimpleDeclaration> visit(CTypeDefDeclaration pDecl) throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<CSimpleDeclaration> visit(CVariableDeclaration pDecl) throws CPATransferException {
      CInitializer init = pDecl.getInitializer();
      if (init != null) {
        return init.accept(this);
      } else {
        return Collections.emptySet();
      }
    }

    @Override
    public Set<CSimpleDeclaration> visit(CParameterDeclaration pDecl) throws CPATransferException {
      return pDecl.asVariableDeclaration().accept(this);
    }

    @Override
    public Set<CSimpleDeclaration> visit(CEnumerator pDecl) throws CPATransferException {
      return Collections.emptySet();
    }

    @Override
    public Set<CSimpleDeclaration> visit(CReturnStatement pNode) throws CPATransferException {
      com.google.common.base.Optional<CExpression> ret = pNode.getReturnValue();

      if (ret.isPresent()) {
        return ret.get().accept(idCollector);
      } else {
        return Collections.emptySet();
      }
    }
  }
}
