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
package org.sosy_lab.cpachecker.core.reachedset;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PseudoPartitionable;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist.WaitlistFactory;

/**
 * Special implementation of the partitioned reached set {@link PartitionedReachedSet}.
 * By default, this implementation needs abstract states which implement
 * {@link PseudoPartitionable} and uses the return value of
 * {@link PseudoPartitionable#getPseudoPartitionKey()} as the key.
 *
 * Whenever the method {@link PseudoPartitionedReachedSet#getReached(AbstractState)}
 * is called (which is usually done by the CPAAlgorithm to get the candidates
 * for merging and coverage checks), it will return a subset of the set of all
 * reached states. This subset contains exactly those states,
 * where the given state might be 'lessOrEqual'.
 *
 * This type of reached-set might work best in combination with an analysis
 * that uses the operators merge_sep and stop_sep.
 */
public class PseudoPartitionedReachedSet extends DefaultReachedSet {

  /**
   * the main storage: row/first key: the partition key, same as in {@link PartitionedReachedSet},
   * column/second key: the pseudo-partition, see {@link PseudoPartitionable}.
   *
   * Since a partition key may be null, but HashBasedTable does not support null keys,
   * we use Optionals.
   */
  private final Table<Optional<Object>, Comparable<?>, SetMultimap<Object, AbstractState>>
      partitionedReached = HashBasedTable.create(1, 1);

  public PseudoPartitionedReachedSet(WaitlistFactory waitlistFactory) {
    super(waitlistFactory);
  }

  @Override
  public void add(AbstractState pState, Precision pPrecision) {
    super.add(pState, pPrecision);

    Optional<Object> key = getPartitionKey(pState);
    Comparable<?> pseudoKey = getPseudoPartitionKey(pState);
    Object pseudoHash = getPseudoHashCode(pState);
    SetMultimap<Object, AbstractState> states = partitionedReached.get(key, pseudoKey);
    if (states == null) {
      // create if not existent
      states = HashMultimap.create();
      partitionedReached.put(key, pseudoKey, states);
    }
    states.put(pseudoHash, pState);
  }

  @Override
  public void remove(AbstractState pState) {
    super.remove(pState);

    Optional<Object> key = getPartitionKey(pState);
    Comparable<?> pseudoKey = getPseudoPartitionKey(pState);
    Object pseudoHash = getPseudoHashCode(pState);
    SetMultimap<Object, AbstractState> states =
        partitionedReached.get(key, pseudoKey);
    if (states != null) {
      states.remove(pseudoHash, pState);
      if (states.isEmpty()) {
        partitionedReached.remove(key, pseudoKey);
      }
    }
  }

  @Override
  public void clear() {
    super.clear();

    partitionedReached.clear();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public Set<AbstractState> getReached(AbstractState pState) {
    Optional<Object> key = getPartitionKey(pState);
    Comparable pseudoKey = getPseudoPartitionKey(pState);
    Object pseudoHash = getPseudoHashCode(pState);
    Map<Comparable<?>, SetMultimap<Object, AbstractState>> partition = partitionedReached.row(key);

    if (partition == null) {
      // partition is empty
      return Collections.emptySet();
    }

    Set<AbstractState> states;

    // here happens the trick, if the comparable is "equal" (matching pseudoKey):
    // if the state is also "equal", we use the state, otherwise state is definitely not "lessOrEqual".
    if (partition.containsKey(pseudoKey)) {
      states = partition.get(pseudoKey).get(pseudoHash);
    } else {
      states = ImmutableSet.of();
    }

    // add all states with a smaller pseudo-key, we might be "lessOrEqual" than those.
    for (Entry<Comparable<?>, SetMultimap<Object, AbstractState>> entry : partition.entrySet()) {
      if (pseudoKey.compareTo(entry.getKey()) > 0) { // pseudoKey is "greaterThan"
        SetMultimap<Object, AbstractState> m = entry.getValue();
        for (Object mKey : m.keySet()) {
          states = Sets.union(states, m.get(mKey));
        }
      }
    }

    return Collections.unmodifiableSet(states);
  }

  private static Comparable<?> getPseudoPartitionKey(AbstractState pState) {
    assert pState instanceof PseudoPartitionable
        : "PseudoPartitionable states necessary for PseudoPartitionedReachedSet";
    return ((PseudoPartitionable) pState).getPseudoPartitionKey();
  }

  private static Object getPseudoHashCode(AbstractState pState) {
    assert pState instanceof PseudoPartitionable
        : "PseudoPartitionable states necessary for PseudoPartitionedReachedSet";
    return ((PseudoPartitionable) pState).getPseudoHashCode();
  }

  private static Optional<Object> getPartitionKey(AbstractState pState) {
    assert pState instanceof Partitionable
        : "Partitionable states necessary for PartitionedReachedSet";
    return Optional.ofNullable(((Partitionable) pState).getPartitionKey());
  }
}
