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
package org.apache.maven.mercury.crypto.pgp;

import java.io.InputStream;

import org.apache.maven.mercury.crypto.api.AbstractStreamVerifierFactory;
import org.apache.maven.mercury.crypto.api.StreamVerifier;
import org.apache.maven.mercury.crypto.api.StreamVerifierAttributes;
import org.apache.maven.mercury.crypto.api.StreamVerifierException;
import org.apache.maven.mercury.crypto.api.StreamVerifierFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.codehaus.plexus.lang.DefaultLanguage;
import org.codehaus.plexus.lang.Language;

/**
 *
 *
 * @author Oleg Gusakov
 * @version $Id$
 *
 */
public class PgpStreamVerifierFactory
extends AbstractStreamVerifierFactory
implements StreamVerifierFactory
{

  public static final String DEFAULT_EXTENSION = PgpHelper.EXTENSION;

  private static final Language LANG = new DefaultLanguage( PgpStreamVerifierFactory.class );

  private PGPPublicKeyRingCollection  trustedPublicKeyRing;
  
  private PGPPrivateKey privateKey;
  
  private int algorithm = 0;
  
  private int digestAlgorithm = PGPUtil.SHA1;
  
  //--------------------------------------------------------------------------------------------
  public PgpStreamVerifierFactory( StreamVerifierAttributes attributes
                                  , InputStream trustedPublicKeyRingStream
                                  )
  throws StreamVerifierException
  {
    super( attributes );
    init( trustedPublicKeyRingStream );
    
  }
  //--------------------------------------------------------------------------------------------
  public PgpStreamVerifierFactory( StreamVerifierAttributes attributes
                                  , PGPPublicKeyRingCollection publicKeyRing
                                  )
  throws StreamVerifierException
  {
    super( attributes );
    this.trustedPublicKeyRing = publicKeyRing;
  }
  //--------------------------------------------------------------------------------------------
  public PgpStreamVerifierFactory( StreamVerifierAttributes attributes
                                  , InputStream secretKeyRingStream
                                  , String secretKeyId
                                  , String secretKeyPass
                                  )
  throws StreamVerifierException
  {
    super( attributes );
    init( secretKeyRingStream, secretKeyId, secretKeyPass );
  }
  //--------------------------------------------------------------------------------------------
  public void init(  InputStream trustedPublicKeyRingStream )
  throws StreamVerifierException
  {
    try
    {
      if( trustedPublicKeyRingStream != null )
      {
        trustedPublicKeyRing = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(trustedPublicKeyRingStream));
        if( trustedPublicKeyRing == null )
          throw new StreamVerifierException( LANG.getMessage( "bad.factory.init.verify.empty" ) );
      }
      else
        throw new StreamVerifierException( LANG.getMessage( "bad.factory.init.verify" ) );
    }
    catch( Exception e )
    {
      throw new StreamVerifierException(e);
    }
  }
  //--------------------------------------------------------------------------------------------
  public void init( InputStream secretKeyRingStream
                  , String secretKeyId
                  , String secretKeyPass
                  )
  throws StreamVerifierException
  {
    try
    {
      if( secretKeyRingStream != null && secretKeyId != null && secretKeyPass != null )
      {
        PGPSecretKeyRing secRing = PgpHelper.readKeyRing( secretKeyRingStream, secretKeyId );
        PGPSecretKey secKey = secRing.getSecretKey( PgpHelper.hexToId( secretKeyId ) );
        privateKey = secKey.extractPrivateKey( secretKeyPass.toCharArray(), PgpHelper.PROVIDER );
        algorithm =  secKey.getPublicKey().getAlgorithm();
      }
      else
        throw new StreamVerifierException( LANG.getMessage( "bad.factory.init.generate" ) );
    }
    catch( Exception e )
    {
      throw new StreamVerifierException(e);
    }
  }
  //--------------------------------------------------------------------------------------------
  public String getDefaultExtension()
  {
    return DEFAULT_EXTENSION;
  }
  //--------------------------------------------------------------------------------------------
  public StreamVerifier newInstance()
  throws StreamVerifierException
  {
    PgpStreamVerifier sv = new PgpStreamVerifier( attributes );
    
    if( privateKey != null )
      sv.init( privateKey, algorithm, digestAlgorithm );
    
    if( trustedPublicKeyRing != null )
      sv.init( trustedPublicKeyRing );
    
    return sv;
  }
  //--------------------------------------------------------------------------------------------
  //--------------------------------------------------------------------------------------------
}
