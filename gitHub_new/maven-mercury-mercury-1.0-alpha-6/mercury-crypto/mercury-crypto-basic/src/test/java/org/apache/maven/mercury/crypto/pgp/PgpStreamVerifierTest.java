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

import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.maven.mercury.crypto.api.StreamObserverException;
import org.apache.maven.mercury.crypto.api.StreamVerifierAttributes;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;

/**
 *
 *
 * @author Oleg Gusakov
 * @version $Id$
 *
 */
public class PgpStreamVerifierTest
    extends TestCase
{
  private static final String keyId   = "0EDB5D91141BC4F2";

  private static final String secretKeyFile = "/secring.gpg";
  private static final String secretKeyPass = "testKey82";

  private static final String publicKeyFile = "/pubring.gpg";
  
  private PGPSecretKeyRing secretKeyRing;
  private PGPSecretKey secretKey;
  private PGPPublicKey publicKey;
  
  PgpStreamVerifierFactory svf;
  
  PgpStreamVerifier sv;

  protected void setUp()
      throws Exception
  {
    InputStream in = getClass().getResourceAsStream( secretKeyFile );
    assertNotNull( in );
    
    secretKeyRing = PgpHelper.readKeyRing( in, keyId );
    assertNotNull( secretKeyRing );
    
    secretKey = secretKeyRing.getSecretKey( Long.parseLong( keyId, 16 ) );
    publicKey = secretKeyRing.getPublicKey();
    
    StreamVerifierAttributes attributes = new StreamVerifierAttributes(PgpStreamVerifierFactory.DEFAULT_EXTENSION, true, true);
    
    InputStream is = getClass().getResourceAsStream( publicKeyFile );
    svf = new PgpStreamVerifierFactory( attributes, is );
    is.close();
    
    is = getClass().getResourceAsStream( secretKeyFile );
    svf.init( is, keyId, secretKeyPass );
    is.close();
  }

  protected void tearDown()
      throws Exception
  {
    super.tearDown();
  }
  //-------------------------------------------------------------------------------------------------
  public void testGenerateSignature()
  throws IOException, StreamObserverException
  {
    PgpStreamVerifier sv = (PgpStreamVerifier)svf.newInstance();
    InputStream in = getClass().getResourceAsStream( "/file.gif" );
    
    int b;
    while( (b = in.read()) != -1 )
      sv.byteReady( b );
    
    String sig = sv.getSignature();
    
    assertNotNull( sig );
    
    assertTrue( sig.length() > 10 );
    
//    System.out.println("Signature is \n"+sig+"\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
  }
  //-------------------------------------------------------------------------------------------------
  public void testVerifySignature()
  throws IOException, StreamObserverException
  {
    PgpStreamVerifier sv = (PgpStreamVerifier)svf.newInstance();

    InputStream in = getClass().getResourceAsStream( "/file.gif" );
    String sig = PgpHelper.streamToString( getClass().getResourceAsStream( "/file.gif.asc" ) );
    
    sv.initSignature( sig );
    
    int b;
    while( (b = in.read()) != -1 )
      sv.byteReady( b );
    
    boolean verified = sv.verifySignature();
    
    assertTrue( verified );
    
    System.out.println("BouncyCastle Signature is "+verified);
  }
  //-------------------------------------------------------------------------------------------------
  public void testVerifyExternalSignature2()
  throws IOException, StreamObserverException
  {
    InputStream in = getClass().getResourceAsStream( "/file.gif" );
    InputStream sig = getClass().getResourceAsStream( "/file.gif.asc.external" );
    InputStream publicKeyRingStream = getClass().getResourceAsStream( publicKeyFile );
    
    boolean verified = PgpHelper.verifyStream( in, sig, publicKeyRingStream );
    
    assertTrue( verified );
    
    System.out.println("3rd Party Signature is "+verified);
  }
  //-------------------------------------------------------------------------------------------------
  public void testVerifyBCSignature()
  throws IOException, StreamObserverException
  {
    InputStream in = getClass().getResourceAsStream( "/file.gif" );
    InputStream sig = getClass().getResourceAsStream( "/file.gif.asc" );
    InputStream publicKeyRingStream = getClass().getResourceAsStream( publicKeyFile );
    
    boolean verified = PgpHelper.verifyStream( in, sig, publicKeyRingStream );
    
    assertTrue( verified );
    
    System.out.println("BC Signature is "+verified);
  }
  //-------------------------------------------------------------------------------------------------
}
