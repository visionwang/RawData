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

import com.orientechnologies.orient.core.config.OGlobalConfiguration;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lock manager implementation that uses multipel partitions to increase the level of concurrency without having to keep one entry
 * per locked key, like for {@link OOneEntryPerKeyLockManager} implementation.
 *
 * @author Andrey Lomakin (a.lomakin-at-orientdb.com)
 * @since 8/11/14
 */
public class OPartitionedLockManager<T> implements OLockManager<T> {
  private static final int HASH_BITS = 0x7fffffff;

  private final int concurrencyLevel = closestInteger(
      OGlobalConfiguration.ENVIRONMENT_LOCK_MANAGER_CONCURRENCY_LEVEL.getValueAsInteger());
  private final int mask             = concurrencyLevel - 1;

  private final ReadWriteLock[]          locks;
  private final OReadersWriterSpinLock[] spinLocks;

  private final boolean useSpinLock;
  private final Comparator comparator = new Comparator() {
    @Override
    public int compare(final Object one, final Object two) {
      final int indexOne;
      if (one == null)
        indexOne = 0;
      else
        indexOne = index(one.hashCode());

      final int indexTwo;
      if (two == null)
        indexTwo = 0;
      else
        indexTwo = index(two.hashCode());

      if (indexOne > indexTwo)
        return 1;

      if (indexOne < indexTwo)
        return -1;

      return 0;
    }
  };

  private static final class SpinLockWrapper implements Lock {
    private final boolean                readLock;
    private final OReadersWriterSpinLock spinLock;

    private SpinLockWrapper(boolean readLock, OReadersWriterSpinLock spinLock) {
      this.readLock = readLock;
      this.spinLock = spinLock;
    }

    @Override
    public void lock() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unlock() {
      if (readLock)
        spinLock.releaseReadLock();
      else
        spinLock.releaseWriteLock();
    }

    @Override
    public Condition newCondition() {
      throw new UnsupportedOperationException();
    }
  }

  public OPartitionedLockManager() {
    this(false);
  }

  public OPartitionedLockManager(boolean useSpinLock) {
    this.useSpinLock = useSpinLock;

    if (useSpinLock) {
      OReadersWriterSpinLock[] lcks = new OReadersWriterSpinLock[concurrencyLevel];

      for (int i = 0; i < lcks.length; i++)
        lcks[i] = new OReadersWriterSpinLock();

      spinLocks = lcks;
      locks = null;
    } else {
      ReadWriteLock[] lcks = new ReadWriteLock[concurrencyLevel];
      for (int i = 0; i < lcks.length; i++)
        lcks[i] = new ReentrantReadWriteLock();

      locks = lcks;
      spinLocks = null;
    }
  }

  private static int closestInteger(int value) {
    return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
  }

  private static int longHashCode(long value) {
    return (int) (value ^ (value >>> 32));
  }

  private int index(int hashCode) {
    return shuffleHashCode(hashCode) & mask;
  }

  public static int shuffleHashCode(int h) {
    return (h ^ (h >>> 16)) & HASH_BITS;
  }

  public Lock acquireExclusiveLock(long value) {
    final int hashCode = longHashCode(value);
    final int index = index(hashCode);

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.acquireWriteLock();
      return new SpinLockWrapper(false, spinLock);
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.writeLock();
    lock.lock();

    return lock;
  }

  public Lock acquireExclusiveLock(int value) {
    final int index = index(value);

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.acquireWriteLock();

      return new SpinLockWrapper(false, spinLock);
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.writeLock();
    lock.lock();
    return lock;
  }

  @Override
  public Lock acquireExclusiveLock(T value) {
    final int index;
    if (value == null)
      index = 0;
    else
      index = index(value.hashCode());

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.acquireWriteLock();

      return new SpinLockWrapper(false, spinLock);
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.writeLock();

    lock.lock();
    return lock;
  }

  public void lockAllExclusive() {
    if (useSpinLock) {
      for (OReadersWriterSpinLock spinLock : spinLocks) {
        spinLock.acquireWriteLock();
      }
    } else {
      for (ReadWriteLock readWriteLock : locks) {
        readWriteLock.writeLock().lock();
      }
    }
  }

  public void unlockAllExclusive() {
    if (useSpinLock) {
      for (OReadersWriterSpinLock spinLock : spinLocks) {
        spinLock.releaseWriteLock();
      }
    } else {
      for (ReadWriteLock readWriteLock : locks) {
        readWriteLock.writeLock().unlock();
      }
    }
  }

  public boolean tryAcquireExclusiveLock(final T value, final long timeout) throws InterruptedException {
    if (useSpinLock)
      throw new IllegalStateException("Spin lock does not support try lock mode");

    final int index;
    if (value == null)
      index = 0;
    else
      index = index(value.hashCode());

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.writeLock();
    return lock.tryLock(timeout, TimeUnit.MILLISECONDS);
  }

  @Override
  public Lock[] acquireExclusiveLocksInBatch(final T... value) {
    if (value == null)
      return new Lock[0];

    final Lock[] locks = new Lock[value.length];
    final T[] sortedValues = getOrderedValues(value);

    for (int n = 0; n < sortedValues.length; n++) {
      locks[n] = acquireExclusiveLock(sortedValues[n]);
    }

    return locks;
  }

  public Lock[] acquireSharedLocksInBatch(final T... value) {
    if (value == null)
      return new Lock[0];

    final Lock[] locks = new Lock[value.length];
    final T[] sortedValues = getOrderedValues(value);

    for (int i = 0; i < sortedValues.length; i++) {
      locks[i] = acquireSharedLock(sortedValues[i]);
    }

    return locks;
  }

  public Lock[] acquireExclusiveLocksInBatch(Collection<T> values) {
    if (values == null || values.isEmpty())
      return new Lock[0];

    final Collection<T> valCopy = getOrderedValues(values);

    final Lock[] locks = new Lock[values.size()];
    int i = 0;
    for (T val : valCopy) {
      locks[i++] = acquireExclusiveLock(val);
    }
    return locks;
  }

  public Lock acquireSharedLock(long value) {
    final int hashCode = longHashCode(value);
    final int index = index(hashCode);

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.acquireReadLock();

      return new SpinLockWrapper(true, spinLock);
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.readLock();
    lock.lock();

    return lock;

  }

  public boolean tryAcquireSharedLock(T value, long timeout) throws InterruptedException {
    if (useSpinLock)
      throw new IllegalStateException("Spin lock does not support try lock mode");

    final int index;
    if (value == null)
      index = 0;
    else
      index = index(value.hashCode());

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.readLock();
    return lock.tryLock(timeout, TimeUnit.MILLISECONDS);
  }

  public Lock acquireSharedLock(int value) {
    final int index = index(value);

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.acquireReadLock();

      return new SpinLockWrapper(true, spinLock);
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.readLock();
    lock.lock();
    return lock;
  }

  @Override
  public Lock acquireSharedLock(final T value) {
    final int index;
    if (value == null)
      index = 0;
    else
      index = index(value.hashCode());

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.acquireReadLock();

      return new SpinLockWrapper(true, spinLock);
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.readLock();
    lock.lock();
    return lock;
  }

  public void releaseSharedLock(final int value) {
    final int index = index(value);

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.releaseReadLock();
      return;
    }

    final ReadWriteLock rwLock = locks[index];
    rwLock.readLock().unlock();
  }

  public void releaseSharedLock(final long value) {
    final int hashCode = longHashCode(value);
    final int index = index(hashCode);

    if (useSpinLock) {
      final OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.releaseReadLock();
      return;
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.readLock();
    lock.unlock();
  }

  public void releaseSharedLock(final T value) {
    final int index;
    if (value == null)
      index = 0;
    else
      index = index(value.hashCode());

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.releaseReadLock();
      return;
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.readLock();
    lock.unlock();
  }

  public void releaseExclusiveLock(final int value) {
    final int index = index(value);
    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.releaseWriteLock();
      return;
    }

    final ReadWriteLock rwLock = locks[index];
    rwLock.writeLock().unlock();
  }

  public void releaseExclusiveLock(final long value) {
    final int hashCode = longHashCode(value);
    final int index = index(hashCode);

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.releaseWriteLock();
      return;
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.writeLock();
    lock.unlock();
  }

  public void releaseExclusiveLock(final T value) {
    final int index;
    if (value == null)
      index = 0;
    else
      index = index(value.hashCode());

    if (useSpinLock) {
      OReadersWriterSpinLock spinLock = spinLocks[index];
      spinLock.releaseWriteLock();
      return;
    }

    final ReadWriteLock rwLock = locks[index];

    final Lock lock = rwLock.writeLock();
    lock.unlock();
  }

  public void releaseLock(final Lock lock) {
    lock.unlock();
  }

  private <T> T[] getOrderedValues(final T[] values) {
    if (values.length < 2) {
      // OPTIMIZED VERSION WITH JUST 1 ITEM (THE MOST COMMON)
      return values;
    }

    final T[] copy = Arrays.copyOf(values, values.length);

    Arrays.sort(copy, 0, copy.length, comparator);

    return copy;
  }

  private <T> Collection<T> getOrderedValues(final Collection<T> values) {
    if (values.size() < 2) {
      // OPTIMIZED VERSION WITH JUST 1 ITEM (THE MOST COMMON)
      return values;
    }

    final List<T> valCopy = new ArrayList<T>(values);
    Collections.sort(valCopy, comparator);

    return valCopy;
  }
}
