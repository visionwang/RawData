/*
 *
 *  *  Copyright 2014 OrientDB LTD (info(at)orientdb.com)
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
 *  * For more information: http://www.orientdb.com
 *
 */
package com.orientechnologies.orient.core.metadata.sequence;

import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.util.concurrent.Callable;

/**
 * @author Matan Shukry (matanshukry@gmail.com)
 * @since 3/3/2015
 */
public class OSequenceCached extends OSequence {
  private static final String FIELD_CACHE = "cache";
  private              long   cacheStart  = 0L;
  private              long   cacheEnd    = 0L;

  public OSequenceCached() {
    super();
  }

  public OSequenceCached(final ODocument iDocument) {
    super(iDocument);
  }

  public OSequenceCached(final ODocument iDocument, OSequence.CreateParams params) {
    super(iDocument, params);
  }

  @Override
  public synchronized boolean updateParams(OSequence.CreateParams params) {
    boolean any = super.updateParams(params);
    if (params.cacheSize != null && this.getCacheSize() != params.cacheSize) {
      this.setCacheSize(params.cacheSize);
      any = true;
    }
    return any;
  }

  @Override
  protected void initSequence(OSequence.CreateParams params) {
    super.initSequence(params);
    setCacheSize(params.cacheSize);
  }

  @Override
  public long next() {
    ODatabaseDocumentInternal mainDb = getDatabase();
    boolean tx = mainDb.getTransaction().isActive();
    try {
      ODatabaseDocumentInternal db = mainDb;
      if (tx) {
        db = mainDb.copy();
        db.activateOnCurrentThread();
      }
      try {
        ODatabaseDocumentInternal finalDb = db;
        return callRetry(new Callable<Long>() {
          @Override
          public Long call() throws Exception {
            synchronized (OSequenceCached.this) {
              int increment = getIncrement();
              if (cacheStart + increment >= cacheEnd) {
                allocateCache(getCacheSize(), finalDb);
              }

              cacheStart = cacheStart + increment;
              return cacheStart;
            }
          }
        }, "next");
      } finally {
        if (tx) {
          db.close();
        }
      }
    } finally {
      if (tx) {
        mainDb.activateOnCurrentThread();
      }
    }
  }

  @Override
  public synchronized long current() {
    return this.cacheStart;
  }

  @Override
  public long reset() {
    ODatabaseDocumentInternal mainDb = getDatabase();
    boolean tx = mainDb.getTransaction().isActive();
    try {
      ODatabaseDocumentInternal db = mainDb;
      if (tx) {
        db = mainDb.copy();
        db.activateOnCurrentThread();
      }
      try {
        ODatabaseDocumentInternal finalDb = db;
        return callRetry(new Callable<Long>() {
          @Override
          public Long call() throws Exception {
            synchronized (OSequenceCached.this) {
              long newValue = getStart();
              setValue(newValue);
              save(finalDb);

              allocateCache(getCacheSize(), finalDb);

              return newValue;
            }
          }
        }, "reset");
      } finally {
        if (tx) {
          db.close();
        }
      }
    } finally {
      if (tx) {
        mainDb.activateOnCurrentThread();
      }
    }
  }

  @Override
  public SEQUENCE_TYPE getSequenceType() {
    return SEQUENCE_TYPE.CACHED;
  }

  public int getCacheSize() {
    return getDocument().field(FIELD_CACHE, OType.INTEGER);
  }

  public void setCacheSize(int cacheSize) {
    getDocument().field(FIELD_CACHE, cacheSize);
  }

  private void allocateCache(int cacheSize, ODatabaseDocumentInternal db) {
    long value = getValue();
    long newValue = value + (getIncrement() * cacheSize);
    setValue(newValue);
    save(db);

    this.cacheStart = value;
    this.cacheEnd = newValue - 1;
  }
}