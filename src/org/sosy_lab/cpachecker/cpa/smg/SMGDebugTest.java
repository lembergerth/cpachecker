/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.smg;

import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.common.io.PathTemplate;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

public class SMGDebugTest {

  public static void dumpPlot(String name, SMGState state) {
    Path path = PathTemplate.ofFormatString("output/smg/smgspec/" + name).getPath();

    try {
      String dot = state.toDot(name, name);
      MoreFiles.writeFile(path, Charset.defaultCharset(), dot);
    } catch (IOException e) {

    }
  }

}