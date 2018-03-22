/**
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.maven.mercury.repository.remote.m2;

import org.apache.maven.mercury.repository.api.AbstractRepository;
import org.apache.maven.mercury.repository.api.LocalRepository;
import org.apache.maven.mercury.repository.api.RemoteRepository;
import org.apache.maven.mercury.repository.api.Repository;
import org.apache.maven.mercury.repository.api.RepositoryException;
import org.apache.maven.mercury.repository.api.RepositoryWriter;
import org.apache.maven.mercury.repository.api.RepositoryWriterFactory;
import org.codehaus.plexus.lang.DefaultLanguage;
import org.codehaus.plexus.lang.Language;

public class RemoteRepositoryWriterM2Factory
implements RepositoryWriterFactory
{
  private static final Language LANG = new DefaultLanguage( RemoteRepositoryWriterM2Factory.class );
  private static final RemoteRepositoryWriterM2Factory FACTORY = new RemoteRepositoryWriterM2Factory();
  
  static 
  {
    AbstractRepository.register( AbstractRepository.DEFAULT_REPOSITORY_TYPE, FACTORY );
  }
  
  public RepositoryWriter getWriter( Repository repo )
  throws RepositoryException
  {
    if( repo == null || !(repo instanceof LocalRepository) )
      throw new RepositoryException( LANG.getMessage( "bad.repository.type", repo == null ? "null" : repo.getClass().getName() ) );
    
    return new RemoteRepositoryWriterM2( (RemoteRepository)repo );
  }

}
