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

import java.io.IOException;
import org.llvm.Module;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.time.Timer;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.cfa.Parser;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.exceptions.ParserException;

/**
 * Parser for the LLVM intermediate language to a CFA.
 * LLVM IR is a typed, assembler-like language that uses the SSA form by default.
 * Because of this, parsing is quite simple: there is no need for scoping
 * and expression trees are always flat.
 */
public class LlvmParser implements Parser {

  private final LogManager logger;
  private final CFABuilder cfaBuilder;

  private final Timer parseTimer = new Timer();
  private final Timer cfaCreationTimer = new Timer();

  public LlvmParser(
      final LogManager pLogger,
      final Configuration pConfig,
      final MachineModel pMachineModel
  ) {
    logger = pLogger;
    cfaBuilder = new CFABuilder(logger, pMachineModel);
  }

  @Override
  public ParseResult parseFile(final String pFilename)
      throws ParserException, IOException, InterruptedException {
    Module llvmModule;
    parseTimer.start();
    try {
      llvmModule = Module.parseIR(pFilename);
    } finally {
      parseTimer.stop();
    }

    // TODO: Handle/show errors in parser

    return buildCfa(llvmModule);
  }

  private ParseResult buildCfa(final Module pModule) {
    return cfaBuilder.build(pModule);
  }

  @Override
  public ParseResult parseString(final String pFilename, final String pCode)
      throws ParserException {
    return null;
  }

  @Override
  public Timer getParseTime() {
    return parseTimer;
  }

  @Override
  public Timer getCFAConstructionTime() {
    return cfaCreationTimer;
  }

}
