/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cfa.parser.llvm;

import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.llvm.Module;
import org.llvm.TypeRef;
import org.llvm.Value;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.Language;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionType;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;

/**
 * CFA builder for LLVM IR.
 * Metadata stored in the LLVM IR file is ignored.
 */
public class CFABuilder extends LlvmAstVisitor {
  // TODO: Linkage types
  // TODO: Visibility styles, i.e., default, hidden, protected
  // TODO: DLL Storage classes (do we actually need this?)
  // TODO: Thread Local Storage Model: May be important for concurrency

  // TODO: Alignment of global variables
  // TODO: Aliases (@a = %b) and IFuncs (@a = ifunc @..)

  private final LogManager logger;
  private final MachineModel machineModel;

  private final LlvmTypeConverter typeConverter;

  private SortedSetMultimap<String, CFANode> cfaNodes;
  private List<Pair<ADeclaration, String>> globalDeclarations;

  public CFABuilder(final LogManager pLogger, final MachineModel pMachineModel) {
    logger = pLogger;
    machineModel = pMachineModel;

    typeConverter = new LlvmTypeConverter(pMachineModel, pLogger);

    cfaNodes = TreeMultimap.create();
    globalDeclarations = new ArrayList<>();
  }

  public ParseResult build(final Module pModule) {
    visit(pModule);

    return new ParseResult(functions, cfaNodes, globalDeclarations, Language.LLVM);
  }

  @Override
  protected FunctionEntryNode visitFunction(final Value pItem) {
    assert pItem.isFunction();

    logger.log(Level.INFO, "Creating function: " + pItem.getValueName());

    return handleFunctionDefinition(pItem);
  }

  @Override
  protected CStatement visitInstruction(final Value pItem) {
    pItem.dumpValue();
    // TODO
    return null;
  }

  private FunctionEntryNode handleFunctionDefinition(final Value pFuncDef) {
    TypeRef functionType = pFuncDef.typeOf();

    CFunctionType cFuncType = (CFunctionType) typeConverter.getCType(functionType);

    /* FIXME
    List<CParameterDeclaration> parameters = null; // FIXME
    CFunctionDeclaration functionDeclaration = new CFunctionDeclaration(
        getLocation(pFuncDef),
        cFuncType,
        pFuncDef.getValueName(),
        parameters);
    FunctionExitNode functionExit = null;
    Optional<CVariableDeclaration> returnVar = null;

    return new CFunctionEntryNode(getLocation(pFuncDef), functionDeclaration, functionExit, returnVar);
    */
    return null;
  }

  @Override
  protected Behavior visitGlobalItem(final Value pItem) {
    return Behavior.CONTINUE; // Parent will iterate through the statements of the block that way
  }

  private FileLocation getLocation(final Value pItem) {
    return FileLocation.DUMMY;
  }
}
