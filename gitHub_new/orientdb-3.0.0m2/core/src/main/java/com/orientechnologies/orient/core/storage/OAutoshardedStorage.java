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
package com.orientechnologies.orient.core.storage;

/**
 * This interface indicates that used storage is autosharded and provides ability to determine current storage unique id
 *
 * @author edegtyarenko
 * @since 15.10.12 10:27
 */
public interface OAutoshardedStorage {

  /**
   * Storage unique id, made by node name + database name
   *
   * @return storage unique id
   */
  String getStorageId();

  String getNodeId();

  void acquireDistributedExclusiveLock(final long timeout);

  void releaseDistributedExclusiveLock();

  /**
   * Check if the distributed need to run only as local env
   *
   * @return
   */
  boolean isLocalEnv();
}
