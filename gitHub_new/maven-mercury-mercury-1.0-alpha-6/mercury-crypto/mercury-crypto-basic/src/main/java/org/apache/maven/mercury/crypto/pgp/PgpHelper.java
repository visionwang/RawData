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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.Security;
import java.util.Iterator;

import org.apache.maven.mercury.crypto.api.StreamObserverException;
import org.apache.maven.mercury.crypto.api.StreamVerifierAttributes;
import org.apache.maven.mercury.crypto.api.StreamVerifierException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.codehaus.plexus.lang.DefaultLanguage;
import org.codehaus.plexus.lang.Language;

/**
 * PGP helper - collection of utility methods, loosely based on one of the
 * Bouncy Castle's SignedFileProcessor.java
 * 
 */
public class PgpHelper
{
  public static final String PROVIDER = "BC";
  public static final String EXTENSION = "asc";
  
  private static final byte [] _buf = new byte[10240];


  private static final Language LANG = new DefaultLanguage( PgpHelper.class );
  //---------------------------------------------------------------------------------
  static 
  {
    Security.addProvider( new BouncyCastleProvider() );
  }
  //---------------------------------------------------------------------------------
  /**
   * load a key ring stream and find the secret key by hex id
   * 
   * @param in PGP keystore
   * @param hexId key id
   * @return
   * @throws IOException
   * @throws PGPException
   */
  public static PGPSecretKeyRing readKeyRing( InputStream in, String hexId )
  throws IOException, PGPException
  {
    if( in == null )
      throw new IllegalArgumentException( LANG.getMessage( "null.input.stream" ) );

    if( hexId == null || hexId.length() < 16 )
      throw new IllegalArgumentException( LANG.getMessage( "bad.key.id", hexId ) );
    
    long id = hexToId( hexId );

    in = PGPUtil.getDecoderStream( in );

    PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection( in );

    Iterator<PGPSecretKeyRing> ringIt = pgpSec.getKeyRings();

    while( ringIt.hasNext() )
    {
      PGPSecretKeyRing keyRing = ringIt.next();
      PGPSecretKey key = keyRing.getSecretKey( id );
      if( key != null )
        return keyRing;
    }

    throw new IllegalArgumentException( LANG.getMessage( "no.secret.key", hexId ) );
  }
  //---------------------------------------------------------------------------------
  public static long hexToId( String hexId )
  {
    BigInteger bi = new BigInteger( hexId, 16 );
    return bi.longValue();
  }
  //---------------------------------------------------------------------------------
  public static long getKeyId( InputStream inSig )
  throws IOException, PGPException
  {
      PGPSignature sig = readSignature( inSig );
      
      return sig.getKeyID();
  }
  //---------------------------------------------------------------------------------
  public static PGPSignature readSignature( InputStream inS )
  throws IOException, PGPException
  {
    if( inS == null )
      throw new IllegalArgumentException( "null.input.stream" );
    
    InputStream in = inS;
    in = PGPUtil.getDecoderStream( in );
  
    PGPObjectFactory pgpObjectFactory = new PGPObjectFactory( in );
    PGPSignatureList sigList = null;

    Object pgpObject = pgpObjectFactory.nextObject();
    
    if( pgpObject == null )
      throw new PGPException( LANG.getMessage( "no.objects.in.stream" ) );

    if( pgpObject instanceof PGPCompressedData )
    {
        PGPCompressedData cd = (PGPCompressedData)pgpObject;

        pgpObjectFactory = new PGPObjectFactory( cd.getDataStream() );
        
        sigList = (PGPSignatureList)pgpObjectFactory.nextObject();
    }
    else
    {
        sigList = (PGPSignatureList)pgpObject;
    }
    
    if( sigList.size() < 1 )
      throw new PGPException( LANG.getMessage( "no.signatures.in.stream" ) );
    
    PGPSignature sig = sigList.get(0);
    
    return sig;
  }
  //---------------------------------------------------------------------------------
  public static String streamToString( InputStream in )
  throws IOException
  {
    if( in == null )
      return null;

    ByteArrayOutputStream ba = new ByteArrayOutputStream();
    int b = 0;
    while( (b = in.read()) != -1 )
      ba.write( b );
    
    return ba.toString();
  }
  //---------------------------------------------------------------------------------
  public static String fileToString( String fileName )
  throws IOException
  {
    FileInputStream fis = null;
    try
    {
      fis = new FileInputStream( fileName );
      return streamToString( fis );
    }
    finally
    {
      if( fis != null ) try { fis.close(); } catch( Exception any ) {}
    }
  }
  //---------------------------------------------------------------------------------
  public static final boolean verifyStream( InputStream in, InputStream asc, InputStream publicKeyRingStream )
  throws IOException, StreamObserverException
  {
      StreamVerifierAttributes attributes = new StreamVerifierAttributes(PgpStreamVerifierFactory.DEFAULT_EXTENSION, false, true);
      
      PgpStreamVerifierFactory svf = new PgpStreamVerifierFactory( attributes, publicKeyRingStream );
      String sig = PgpHelper.streamToString( asc );
      
      return verifyStream( in, sig, svf );
  }
  //---------------------------------------------------------------------------------
  public static final boolean verifyStream( InputStream in, String sig, PgpStreamVerifierFactory factory )
  throws IOException, StreamObserverException
  {

      PgpStreamVerifier sv = (PgpStreamVerifier)factory.newInstance();
      
      sv.initSignature( sig );
      
      int nBytes = -1;
      while( (nBytes = in.read(_buf)) != -1 )
        sv.bytesReady( _buf, 0, nBytes );
      
      boolean verified = sv.verifySignature();

      return verified;
  }
  
  public static final PGPPublicKeyRingCollection readPublicRing( InputStream in )
  throws IOException, PGPException
  {
      if( in == null )
          return null;
      
      PGPPublicKeyRingCollection res = new PGPPublicKeyRingCollection( PGPUtil.getDecoderStream( in ) );
      
      return res;
  }
  
  public static final void writePublicRing( PGPPublicKeyRingCollection  prc, OutputStream out )
  throws IOException
  {
      if( prc == null )
          throw new IllegalArgumentException( LANG.getMessage( "null.ring" ) );
      
      if( out == null )
          throw new IllegalArgumentException( LANG.getMessage( "null.os" ) );
      
      prc.encode( out );
      
  }
  //---------------------------------------------------------------------------------
  //---------------------------------------------------------------------------------
}
