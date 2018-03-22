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

package com.orientechnologies.orient.core.storage.cache.local.twoq;

import com.orientechnologies.orient.core.storage.cache.OCacheEntry;

import java.util.Iterator;

/**
 * @author Artem Orobets (enisher-at-gmail.com)
 */
public interface LRUList extends Iterable<OCacheEntry> {
  OCacheEntry get(long fileId, long pageIndex);

  OCacheEntry remove(long fileId, long pageIndex);

  void putToMRU(OCacheEntry cacheEntry);

  void clear();

  boolean contains(long fileId, long filePosition);

  int size();

  OCacheEntry removeLRU();

  OCacheEntry getLRU();

  @Override
  Iterator<OCacheEntry> iterator();

  /**
   * @return Iterator which iterates from head to tail, or in other words from least recently used to most recently used pages.
   */
  Iterator<OCacheEntry> reverseIterator();
}
