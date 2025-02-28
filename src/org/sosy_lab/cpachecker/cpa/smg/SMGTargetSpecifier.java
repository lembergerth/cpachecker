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

/**
 * Target Specifier for specifying target of pointer.
 */
public enum SMGTargetSpecifier {
  REGION("reg"),
  FIRST("fst"),
  LAST("lst"),
  ALL("all"),
  OPT("optional"),
  UNKNOWN("unknown");

  private final String name;

  private SMGTargetSpecifier(String pName) {
    name = pName;
  }

  @Override
  public String toString() {
    return name;
  }
}
