/*
 *
 *  *  Copyright 2010-2016 OrientDB LTD (http://orientdb.com)
 *  *
 *  *  Licensed under the Apache License, Version 2.0 (the "License");
 *  *  you may not use this file except in compliance with the License.
 *  *  You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *  Unless required by applicable law or agreed to in writing, software
 *  *  distributed under the License is distributed on an "AS IS" BASIS,
 *  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  See the License for the specific language governing permissions and
 *  *  limitations under the License.
 *  *
 *  * For more information: http://orientdb.com
 *
 */

package com.orientechnologies.common.concur.lock;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.LockSupport;

import com.orientechnologies.common.types.OModifiableInteger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Andrey Lomakin (a.lomakin-at-orientdb.com)
 * @since 8/18/14
 */
@SuppressFBWarnings("SE_TRANSIENT_FIELD_NOT_RESTORED")
public class OReadersWriterSpinLock extends AbstractOwnableSynchronizer {
  private static final long                               serialVersionUID = 7975120282194559960L;

  private final transient LongAdder                       distributedCounter;
  private final transient AtomicReference<WNode>          tail             = new AtomicReference<WNode>();
  private final transient ThreadLocal<OModifiableInteger> lockHolds        = new InitOModifiableInteger();

  private final transient ThreadLocal<WNode>              myNode           = new InitWNode();
  private final transient ThreadLocal<WNode>              predNode         = new ThreadLocal<WNode>();

  public OReadersWriterSpinLock() {
    final WNode wNode = new WNode();
    wNode.locked = false;

    tail.set(wNode);

    distributedCounter = new LongAdder();
  }

  public void acquireReadLock() {
    final OModifiableInteger lHolds = lockHolds.get();

    final int holds = lHolds.intValue();
    if (holds > 0) {
      // we have already acquire read lock
      lHolds.increment();
      return;
    } else if (holds < 0) {
      // write lock is acquired before, do nothing
      return;
    }

    distributedCounter.increment();

    WNode wNode = tail.get();
    while (wNode.locked) {
      distributedCounter.decrement();

      while (wNode.locked && wNode == tail.get()) {
        wNode.waitingReaders.add(Thread.currentThread());

        if (wNode.locked && wNode == tail.get())
          LockSupport.park(this);

        wNode = tail.get();
      }

      distributedCounter.increment();

      wNode = tail.get();
    }

    lHolds.increment();
    assert lHolds.intValue() == 1;
  }

  public void releaseReadLock() {
    final OModifiableInteger lHolds = lockHolds.get();
    final int holds = lHolds.intValue();
    if (holds > 1) {
      lHolds.decrement();
      return;
    } else if (holds < 0) {
      // write lock was acquired before, do nothing
      return;
    }

    distributedCounter.decrement();

    lHolds.decrement();
    assert lHolds.intValue() == 0;
  }

  public void acquireWriteLock() {
    final OModifiableInteger lHolds = lockHolds.get();

    if (lHolds.intValue() < 0) {
      lHolds.decrement();
      return;
    }

    final WNode node = myNode.get();
    node.locked = true;

    final WNode pNode = tail.getAndSet(myNode.get());
    predNode.set(pNode);

    while (pNode.locked) {
      pNode.waitingWriter = Thread.currentThread();

      if (pNode.locked)
        LockSupport.park(this);
    }

    pNode.waitingWriter = null;

    final long beginTime = System.currentTimeMillis();
    while (distributedCounter.sum() != 0) {
      // IN THE WORST CASE CPU CAN BE 100% FOR MAXIMUM 1 SECOND
      if (System.currentTimeMillis() - beginTime > 1000)
        try {
          Thread.sleep(1);
        } catch (InterruptedException e) {
          break;
        }
    }

    setExclusiveOwnerThread(Thread.currentThread());

    lHolds.decrement();
    assert lHolds.intValue() == -1;
  }

  public void releaseWriteLock() {
    final OModifiableInteger lHolds = lockHolds.get();

    if (lHolds.intValue() < -1) {
      lHolds.increment();
      return;
    }

    setExclusiveOwnerThread(null);

    final WNode node = myNode.get();
    node.locked = false;

    final Thread waitingWriter = node.waitingWriter;
    if (waitingWriter != null)
      LockSupport.unpark(waitingWriter);

    Thread waitingReader;
    while ((waitingReader = node.waitingReaders.poll()) != null) {
      LockSupport.unpark(waitingReader);
    }

    myNode.set(predNode.get());
    predNode.set(null);

    lHolds.increment();
    assert lHolds.intValue() == 0;
  }

  private static final class InitWNode extends ThreadLocal<WNode> {
    @Override
    protected WNode initialValue() {
      return new WNode();
    }
  }

  private static final class InitOModifiableInteger extends ThreadLocal<OModifiableInteger> {
    @Override
    protected OModifiableInteger initialValue() {
      return new OModifiableInteger();
    }
  }

  private final static class WNode {
    private final Queue<Thread> waitingReaders = new ConcurrentLinkedQueue<Thread>();

    private volatile boolean    locked         = true;
    private volatile Thread     waitingWriter;
  }
}
