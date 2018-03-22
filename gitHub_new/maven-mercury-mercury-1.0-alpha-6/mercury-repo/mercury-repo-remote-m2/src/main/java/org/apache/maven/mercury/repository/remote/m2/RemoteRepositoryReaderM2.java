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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.apache.maven.mercury.artifact.Artifact;
import org.apache.maven.mercury.artifact.ArtifactCoordinates;
import org.apache.maven.mercury.artifact.ArtifactMetadata;
import org.apache.maven.mercury.artifact.DefaultArtifact;
import org.apache.maven.mercury.artifact.Quality;
import org.apache.maven.mercury.artifact.QualityEnum;
import org.apache.maven.mercury.artifact.version.DefaultArtifactVersion;
import org.apache.maven.mercury.artifact.version.VersionComparator;
import org.apache.maven.mercury.artifact.version.VersionException;
import org.apache.maven.mercury.artifact.version.VersionRange;
import org.apache.maven.mercury.artifact.version.VersionRangeFactory;
import org.apache.maven.mercury.builder.api.DependencyProcessor;
import org.apache.maven.mercury.builder.api.MetadataReader;
import org.apache.maven.mercury.builder.api.MetadataReaderException;
import org.apache.maven.mercury.logging.IMercuryLogger;
import org.apache.maven.mercury.logging.MercuryLoggerManager;
import org.apache.maven.mercury.repository.api.AbstracRepositoryReader;
import org.apache.maven.mercury.repository.api.AbstractRepOpResult;
import org.apache.maven.mercury.repository.api.AbstractRepository;
import org.apache.maven.mercury.repository.api.MetadataResults;
import org.apache.maven.mercury.repository.api.ArtifactResults;
import org.apache.maven.mercury.repository.api.LocalRepository;
import org.apache.maven.mercury.repository.api.MetadataCacheException;
import org.apache.maven.mercury.repository.api.MetadataCorruptionException;
import org.apache.maven.mercury.repository.api.RemoteRepository;
import org.apache.maven.mercury.repository.api.Repository;
import org.apache.maven.mercury.repository.api.RepositoryException;
import org.apache.maven.mercury.repository.api.RepositoryGAMetadata;
import org.apache.maven.mercury.repository.api.RepositoryGAVMetadata;
import org.apache.maven.mercury.repository.api.RepositoryReader;
import org.apache.maven.mercury.repository.local.m2.ArtifactLocation;
import org.apache.maven.mercury.repository.local.m2.LocalRepositoryM2;
import org.apache.maven.mercury.repository.metadata.Metadata;
import org.apache.maven.mercury.repository.metadata.MetadataBuilder;
import org.apache.maven.mercury.repository.metadata.MetadataException;
import org.apache.maven.mercury.repository.metadata.Versioning;
import org.apache.maven.mercury.spi.http.client.HttpClientException;
import org.apache.maven.mercury.spi.http.client.retrieve.DefaultRetrievalRequest;
import org.apache.maven.mercury.spi.http.client.retrieve.DefaultRetriever;
import org.apache.maven.mercury.spi.http.client.retrieve.RetrievalResponse;
import org.apache.maven.mercury.transport.api.Binding;
import org.apache.maven.mercury.transport.api.Server;
import org.apache.maven.mercury.util.FileUtil;
import org.apache.maven.mercury.util.Util;
import org.codehaus.plexus.lang.DefaultLanguage;
import org.codehaus.plexus.lang.Language;

/**
 * implementation of M2 remote repository reader. Actual Transport (protocol, URL) [should] come from RemoteRepository
 * Server URL Current implementation does not do the check and uses jetty-client directly. TODO - re-implements after
 * jetty-client implements ReaderTransport
 * 
 * @author Oleg Gusakov
 * @version $Id$
 */
public class RemoteRepositoryReaderM2
    extends AbstracRepositoryReader
    implements RepositoryReader, MetadataReader
{
    private static final IMercuryLogger LOG = MercuryLoggerManager.getLogger( RemoteRepositoryReaderM2.class );

    private static final Language LANG = new DefaultLanguage( RemoteRepositoryReaderM2.class );

    // TODO - replace with known Transport's protocols. Should be similar to RepositoryReader/Writer registration
    private static final String[] _protocols = new String[] { "http", "https", "dav", "webdav" };

    private HashSet<Server> _servers;
    // ---------------------------------------------------------------------------------------------------------------
    RemoteRepository _repo;

    List<LocalRepository> _localRepos;

    File _defaultRoot;

    // ---------------------------------------------------------------------------------------------------------------
    public RemoteRepositoryReaderM2( RemoteRepository repo, DependencyProcessor mdProcessor )
        throws RepositoryException
    {
        this( repo, mdProcessor, null );
    }

    // ---------------------------------------------------------------------------------------------------------------
    public RemoteRepositoryReaderM2( RemoteRepository repo, DependencyProcessor mdProcessor,
                                     List<LocalRepository> localRepos )
        throws RepositoryException
    {
        if ( repo == null )
            throw new IllegalArgumentException( LANG.getMessage( "bad.repository.null" ) );

        if ( repo.getServer() == null )
            throw new IllegalArgumentException( LANG.getMessage( "bad.repository.server.null" ) );

        if ( repo.getServer().getURL() == null )
            throw new IllegalArgumentException( LANG.getMessage( "bad.repository.server.url.null" ) );

        _repo = repo;

        if ( mdProcessor == null )
            throw new IllegalArgumentException( "MetadataProcessor cannot be null " );

        setDependencyProcessor( mdProcessor );

        try
        {
            _defaultRoot = File.createTempFile( "mercury-", "-local" );
            _defaultRoot.delete();
            _defaultRoot.mkdirs();
            _defaultRoot.deleteOnExit();
            
            if( LOG.isDebugEnabled() )
                LOG.debug( LANG.getMessage( "default.root", _defaultRoot.getCanonicalPath() ) );
        }
        catch ( IOException e )
        {
            throw new RepositoryException( e );
        }

        if ( localRepos == null || localRepos.isEmpty() )
        {
            _localRepos = new ArrayList<LocalRepository>( 1 );
            _localRepos.add( new LocalRepositoryM2( "temp", _defaultRoot, getDependencyProcessor() ) );
        }
        else
            _localRepos = localRepos;

        // TODO 2008-07-29 og: here I should analyze Server protocol
        // and come with appropriate Transport implementation
        _servers = new HashSet<Server>( 1 );
        _servers.add( repo.getServer() );
    }

    // ---------------------------------------------------------------------------------------------------------------
    public Repository getRepository()
    {
        return _repo;
    }

    // ---------------------------------------------------------------------------------------------------------------
    private final ArtifactLocation calculateLocation( String root, ArtifactMetadata md, AbstractRepOpResult res )
        throws RepositoryException, MetadataReaderException, MetadataException
    {
        ArtifactLocation loc = new ArtifactLocation( root, md );

        Collection<String> versions = null;
        try
        {
            versions = getCachedVersions( loc, md, res );
        }
        catch ( MetadataCacheException e )
        {
            throw new MetadataException( e );
        }

        if ( Util.isEmpty( versions ) )
            throw new RepositoryException( LANG.getMessage( "group.md.no.versions",
                                                            _repo.getServer().getURL().toString(), loc.getGaPath() ) );

        Quality vq = new Quality( loc.getVersion() );

        // RELEASE = LATEST - SNAPSHOTs
        if ( Artifact.RELEASE_VERSION.equals( loc.getVersion() ) || Artifact.LATEST_VERSION.equals( loc.getVersion() ) )
        {
            boolean noSnapshots = Artifact.RELEASE_VERSION.equals( loc.getVersion() );
            String ver = VersionRangeFactory.findLatest( versions, noSnapshots );

            if ( ver == null )
            {
                // res.addError( bmd, new RepositoryException( LANG.getMessage( "gav.not.found", bmd.toString(),
                // loc.getGaPath() ) ) );
                if ( LOG.isWarnEnabled() )
                    LOG.warn( LANG.getMessage( "gav.not.found", md.toString(), loc.getGaPath() ) );

                return null;
            }

            loc.setVersion( ver );

            // LATEST is a SNAPSHOT :(
            if ( loc.getVersion().endsWith( Artifact.SNAPSHOT_VERSION ) )
            {
                loc.setVersionDir( loc.getVersion() );

                if ( !findLatestSnapshot( md, loc, res ) )
                    return null;
            }
            else
                // R or L found and actual captured in loc.version
                loc.setVersionDir( loc.getVersion() );
        }
        // regular snapshot requested
        else if ( loc.getVersion().endsWith( Artifact.SNAPSHOT_VERSION ) )
        {
            if ( !versions.contains( loc.getVersion() ) && !findLatestSnapshot( md, loc, res ) )
                return null;
        }
        // time stamped snapshot requested
        else if ( vq.equals( Quality.SNAPSHOT_TS_QUALITY ) )
        {
            loc.setVersionDir( loc.getVersionWithoutTS() + FileUtil.DASH + Artifact.SNAPSHOT_VERSION );
        }

        return loc;
    }

    // ---------------------------------------------------------------------------------------------------------------
    private Collection<String> getCachedSnapshots( ArtifactMetadata bmd, ArtifactLocation loc )
        throws MetadataCacheException, RepositoryException, MetadataReaderException, MetadataException
    {
        RepositoryGAVMetadata gavm = null;
        ArtifactCoordinates coord = null;

        if ( _mdCache != null )
        {
            try
            {
                coord = bmd.getEffectiveCoordinates();
                coord.setVersion( loc.getVersion() );

                gavm = _mdCache.findGAV( _repo.getId(), _repo.getUpdatePolicy(), coord );
                if ( gavm != null )
                    return gavm.getSnapshots();
            }
            catch ( MetadataCorruptionException e )
            {
                // bad cached data - let's overwrite it
                LOG.error( LANG.getMessage( "cached.data.problem", e.getMessage(), bmd.toString() ) );
            }
        }

        String mdPath = loc.getGavPath() + '/' + _repo.getMetadataName();

        byte[] mdBytes = readRawData( mdPath, true );
        if ( mdBytes == null )
        {
            throw new RepositoryException( LANG.getMessage( "no.gav.md", _repo.getServer().getURL().toString(), mdPath ) );
        }

        Metadata gavMd = MetadataBuilder.read( new ByteArrayInputStream( mdBytes ) );
        if ( gavMd == null )
        {
            throw new RepositoryException( LANG.getMessage( "gav.md.no.versions",
                                                            _repo.getServer().getURL().toString(), mdPath ) );
        }

        gavm = new RepositoryGAVMetadata( gavMd );

        if ( _mdCache != null )
        {
            _mdCache.updateGAV( _repo.getId(), gavm );
        }

        if ( Util.isEmpty( gavm.getSnapshots() ) )
        {
            throw new RepositoryException( LANG.getMessage( "gav.md.no.versions",
                                                            _repo.getServer().getURL().toString(), mdPath ) );
        }

        return gavm.getSnapshots();

    }

    // ---------------------------------------------------------------------------------------------------------------
    private boolean findLatestSnapshot( ArtifactMetadata bmd, ArtifactLocation loc, AbstractRepOpResult res )
        throws MetadataReaderException, MetadataException, RemoteRepositoryM2OperationException
    {
        DefaultArtifactVersion dav = new DefaultArtifactVersion( loc.getVersion() );

        Collection<String> versions = null;

        try
        {
            versions = getCachedSnapshots( bmd, loc );
        }
        catch ( Exception e )
        {
            // res.addError( bmd, new RepositoryException( LANG.getMessage( "cached.metadata.reading.exception",
            // e.getMessage(), bmd.toString(), _repo.getServer().getURL().toString() ) ) );
            if ( LOG.isErrorEnabled() )
                LOG.error( LANG.getMessage( "cached.metadata.reading.exception", e.getMessage(), bmd.toString(),
                                            _repo.getServer().getURL().toString() ) );

            return false;
        }

        // version-SNAPSHOT or exact TS exists?
        if ( versions.contains( loc.getVersion() ) )
        {
            return true;
        }

        // nothing to do, but find latest
        String ver = VersionRangeFactory.findLatest( versions, false );

        if ( ver == null )
        {
            // res.addError( bmd, new RepositoryException( LANG.getMessage( "snapshot.not.found",
            // _repo.getServer().getURL().toString(), bmd.toString() ) ) );
            if ( LOG.isErrorEnabled() )
                LOG.error( LANG.getMessage( "snapshot.not.found", _repo.getServer().getURL().toString(), bmd.toString() ) );

            return false;
        }

        loc.setVersion( ver );
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    /**
     * TODO og: parallelize as soon as code stabilizes
     */
    public ArtifactResults readArtifacts( Collection<ArtifactMetadata> query )
        throws RepositoryException
    {
        if ( query == null || query.size() < 1 )
            return null;

        ArtifactResults res = new ArtifactResults();

        for ( ArtifactMetadata md : query )
        {
            if( ! _repo.getRepositoryQualityRange().isAcceptedQuality( md.getRequestedQuality() ) )
                continue;
            
            try
            {
                readArtifact( md, res );
            }
            catch ( Exception e )
            {
                res.addError( md, e );
            }
        }

        return res;
    }

    // ---------------------------------------------------------------------------------------------------------------
    private File findLocalRoot( Quality vq )
    {
        for ( LocalRepository lr : _localRepos )
            if ( lr.isWriteable() && lr.getVersionRangeQualityRange().isAcceptedQuality( vq ) )
                return lr.getDirectory();

        return _defaultRoot;
    }

    // ---------------------------------------------------------------------------------------------------------------
    private void readArtifact( ArtifactMetadata md, ArtifactResults res )
        throws IOException, RepositoryException, MetadataReaderException, MetadataException
    {
        DefaultArtifact da = md instanceof DefaultArtifact ? (DefaultArtifact) md : new DefaultArtifact( md );

        ArtifactLocation loc = calculateLocation( _repo.getServer().getURL().toString(), md, res );

        if ( loc == null )
            return;

        da.setVersion( loc.getVersion() );

        Quality vq = da.getRequestedQuality();

        File root = findLocalRoot( vq );

        File binFile = new File( root, loc.getRelPath() );
        File pomFile = null;

        DefaultRetrievalRequest drr = new DefaultRetrievalRequest();

        Binding binBinding = new Binding( new URL( loc.getAbsPath() ), binFile );
        drr.addBinding( binBinding );

        boolean isPom = "pom".equals( md.getType() );
        if ( !isPom )
        {
            pomFile = new File( root, loc.getRelPomPath() );
            Binding pomBinding = new Binding( new URL( loc.getAbsPomPath() ), pomFile );
            drr.addBinding( pomBinding );
        }

        DefaultRetriever transport;
        try
        {
            transport = new DefaultRetriever();
        }
        catch ( HttpClientException e )
        {
            throw new RepositoryException( e );
        }
        transport.setServers( _servers );
        RetrievalResponse resp = transport.retrieve( drr );
        transport.stop();

        if ( resp.hasExceptions() )
        {
            res.addError( md, new RepositoryException( resp.getExceptions().toString() ) );
        }
        else
        {
            if( LOG.isInfoEnabled() )
                LOG.info( LANG.getMessage( "read.artifact", loc.getAbsPath(), Util.convertLength( binFile.length() ) ) );

            da.setFile( binFile );
            da.setPomBlob( FileUtil.readRawData( isPom ? binFile : pomFile ) );
            res.add( md, da );
        }
    }
    // ---------------------------------------------------------------------------------------------------------------
    /**
     * this is an extended call, specific to Remote M2 repository for now
     */
    public void readQualifiedArtifacts( Collection<ArtifactMetadata> mds, ArtifactResults res )
    throws IOException, RepositoryException, MetadataReaderException, MetadataException
    {
        if( Util.isEmpty( mds ) )
            throw new IllegalArgumentException( LANG.getMessage( "no.md.list" ) );
        
        Map<ArtifactMetadata, DefaultArtifact> artifacts = new HashMap<ArtifactMetadata, DefaultArtifact>( mds.size() );
        
        DefaultRetrievalRequest request = new DefaultRetrievalRequest();
        
        long start = 0;

        if( LOG.isInfoEnabled() )
        {
            start = System.currentTimeMillis();
            
            LOG.info( LANG.getMessage( "read.artifacts", mds.size()+"" ) );
        }
        
        TreeSet<Server> servers = new TreeSet<Server>(
                        new Comparator<Server>()
                        {

                            public int compare( Server s1, Server s2 )
                            {
                                return s1.getId().compareTo( s2.getId() );
                            }
            
                        }
                                                     );
        for( ArtifactMetadata md : mds )
        {
            DefaultArtifact da = md instanceof DefaultArtifact ? (DefaultArtifact) md : new DefaultArtifact( md );
            
            artifacts.put( md, da );
            
            Server server = ( (RemoteRepositoryReaderM2)md.getTracker() ).getRepository().getServer();
    
            ArtifactLocation loc = new ArtifactLocation( server.getURL().toString(), md ); //calculateLocation( server.getURL().toString(), md, res );
            
            servers.add( server );
    
            da.setVersion( loc.getVersion() );
    
            Quality vq = da.getRequestedQuality();
    
            File root = findLocalRoot( vq );
    
            File binFile = new File( root, loc.getRelPath() );
            File pomFile = null;
    
            Binding binBinding = new Binding( new URL( loc.getAbsPath() ), binFile );
            request.addBinding( binBinding );
            
            da.setFile( binFile );
    
            if ( !md.isPom() )
            {
                pomFile = new File( root, loc.getRelPomPath() );
                Binding pomBinding = new Binding( new URL( loc.getAbsPomPath() ), pomFile );
                request.addBinding( pomBinding );
                
                da.setPomFile( pomFile );
            }

            Object tracker = md.getTracker();
            
            if( tracker == null )
                throw new RepositoryNonQualifiedArtifactException( LANG.getMessage( "non.qualified.artifact", md.toString() ));

            servers.add( ((RemoteRepositoryReaderM2)tracker).getRepository().getServer() );
        }

        DefaultRetriever retriever;
        try
        {
            retriever = new DefaultRetriever();
        }
        catch ( HttpClientException e )
        {
            throw new RepositoryException(e);
        }
        
        retriever.setServers( servers );
        
        RetrievalResponse response = retriever.retrieve( request );
        
        retriever.stop();
        
        if ( response.hasExceptions() )
        {
            // record all bugs on the first artifact as jetty transport does not 
            res.addError( (ArtifactMetadata) mds.toArray()[0], new RepositoryException( response.getExceptions().toString() ) );
        }
        else
        {
            for( Map.Entry<ArtifactMetadata, DefaultArtifact> e : artifacts.entrySet() )
            {
                DefaultArtifact da = e.getValue();
                da.setPomBlob( FileUtil.readRawData( da.isPom() ? da.getFile() : da.getPomFile() ) );
                res.add( e.getKey(), da );
            }
        }

        if( LOG.isInfoEnabled() )
        {
            long diff = System.currentTimeMillis() - start;
            LOG.info( LANG.getMessage( "read.artifacts.time", mds.size()+"", (diff/1000L)+"" ) );
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    /**
   * 
   */
    public MetadataResults readDependencies( Collection<ArtifactMetadata> query )
        throws RepositoryException
    {
        if ( query == null || query.size() < 1 )
            return null;

        MetadataResults ror = new MetadataResults( 16 );

        for ( ArtifactMetadata md : query )
        {
            if( ! _repo.getRepositoryQualityRange().isAcceptedQuality( md.getRequestedQuality() ) )
                continue;
            
            try
            {
                List<ArtifactMetadata> deps =
                    _mdProcessor.getDependencies( md, _mdReader == null ? this : _mdReader, System.getenv(),
                                                  System.getProperties() );
                ror.add( md, deps );
            }
            catch ( Exception e )
            {
                LOG.warn( "error reading " + md.toString() + " dependencies", e );
                continue;
            }
        }

        return ror;
    }

    // ---------------------------------------------------------------------------------------------------------------
    private TreeSet<String> getCachedVersions( ArtifactLocation loc, ArtifactMetadata bmd, AbstractRepOpResult res )
        throws MetadataException, MetadataReaderException, MetadataCacheException
    {
        RepositoryGAMetadata gam = null;
        ArtifactCoordinates coord = null;
        TreeSet<String> gaVersions = null;
        String ver = bmd.getVersion();
        
        // check the cache first
        if ( _mdCache != null )
        {
            try
            {
                coord = bmd.getEffectiveCoordinates();
                coord.setVersion( loc.getVersion() );

                gam = _mdCache.findGA( _repo.getId(), _repo.getUpdatePolicy(), coord );

                if ( gam != null && !gam.isExpired() )
                {
                    gaVersions = gam.getVersions();

                    if ( !( (RemoteRepositoryM2) _repo )._workAroundBadMetadata )
                        return gaVersions;

                    if ( bmd.isVirtual() || !bmd.isSingleton() )
                        return gaVersions;

                    if ( gaVersions.contains( ver ) )
                        return gaVersions;

                    String versionDir = ArtifactLocation.calculateVersionDir( bmd.getVersion() );

                    String binPath =
                        loc.getGaPath() + FileUtil.SEP + versionDir + FileUtil.SEP + bmd.getArtifactId() + "-"
                            + bmd.getVersion() + ".pom";
                    
                    byte [] pom = null;
                    
                    try
                    {
                        pom = readRawData( binPath, true );
                    }
                    catch( Exception e ) {}

                    if ( pom != null && pom.length > 1 )
                    {

                        String oldSnapshot = findDuplicateSnapshot( ver, gaVersions );

                        if ( oldSnapshot != null )
                            gaVersions.remove( oldSnapshot );

                        gaVersions.add( ver );

                        _mdCache.updateGA( _repo.getId(), gam );
                    }
                    return gaVersions;
                }
            }
            catch ( MetadataCorruptionException e )
            {
                // bad cached data - let's overwrite it
                LOG.error( LANG.getMessage( "cached.data.problem", e.getMessage(), bmd.toString() ) );
            }
        }

        if ( LOG.isDebugEnabled() )
            LOG.debug( _repo.getId() + ": did not find in the cache - go out for " + bmd );

        // no cached data, or it has expired - read from repository
        String mdPath = loc.getGaPath() + FileUtil.SEP + _repo.getMetadataName();
        byte[] mavenMetadata = readRawData( mdPath, true );

        Metadata mmd = null;
        
        if( mavenMetadata != null )
            mmd = MetadataBuilder.getMetadata( mavenMetadata );

        if ( mmd == null
            || mmd.getVersioning() == null
            || mmd.getVersioning().getVersions() == null
            || ( bmd.isSingleton() && ! mmd.getVersioning().getVersions().contains( ver ) )
        )
        {
            if ( ( (RemoteRepositoryM2) _repo )._workAroundBadMetadata )
            {
                // check for the pom
                String versionDir = ArtifactLocation.calculateVersionDir( bmd.getVersion() );

                String binPath =
                    loc.getGaPath() + FileUtil.SEP + versionDir + FileUtil.SEP + bmd.getArtifactId() + "-"
                        + ver + ".pom";
                byte[] pom = readRawData( binPath, true );

                if ( pom != null ) // version exists
                {
                    if( mmd == null )
                    {
                        mavenMetadata =
                            ( "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<metadata>" + "<groupId>" + bmd.getGroupId()
                                + "</groupId>" + "<artifactId>" + bmd.getArtifactId() + "</artifactId>" + "<version>"
                                + ArtifactLocation.calculateVersionDir( ver ) + "</version>" + "<versioning>"
                                + "<versions>" + "<version>" + ver + "</version>" + "</versions>"
                                + "</versioning>" + "</metadata>" ).getBytes();
                        mmd = MetadataBuilder.getMetadata( mavenMetadata );
                    }
                    else
                    {
                        Versioning v = mmd.getVersioning();
                        
                        if( v == null )
                        {
                            v = new Versioning();
                            mmd.setVersioning( v );
                        }
                        
                        v.addVersion( ver );
                    }
                }
            }

            if ( mmd == null )
                throw new MetadataReaderException( LANG.getMessage( "no.group.md",
                                                                    _repo.getServer().getURL().toString(), mdPath ) );
        }

        if ( mmd == null || mmd.getVersioning() == null )
        {
            LOG.warn( LANG.getMessage( "maven.bad.metadata", loc.getGaPath() + FileUtil.SEP + _repo.getMetadataName(),
                                       _repo.getId() ) );
            return null;
        }

        gam = new RepositoryGAMetadata( mmd );

        if ( gam == null || Util.isEmpty( gam.getVersions() ) )
        {
            LOG.warn( LANG.getMessage( "maven.metadata.no.versions", loc.getGaPath() + FileUtil.SEP
                + _repo.getMetadataName(), _repo.getId() ) );
            return null;
        }

        Collection<String> vList = (Collection<String>) gam.getVersions();
        List<String> vRes = new ArrayList<String>( vList.size() );
        vRes.addAll( vList );

        gam.getVersions().clear();

        boolean requestRelease = bmd.getVersion().endsWith( Artifact.RELEASE_VERSION );
        // GA metadata version scan
        for ( String v : vRes )
        {
            String toAdd;

            boolean foundSnapshot = v.endsWith( Artifact.SNAPSHOT_VERSION );

            if ( requestRelease && foundSnapshot )
                continue;

            if ( foundSnapshot )
            {
                boolean snFound = false;
                ArtifactMetadata snMd = new ArtifactMetadata( bmd.toString() );
                snMd.setVersion( v );
                ArtifactLocation snLoc = new ArtifactLocation( loc.getPrefix(), snMd );
                try
                {
                    snFound = findLatestSnapshot( snMd, snLoc, res );
                }
                catch ( Exception e )
                {
                    throw new MetadataException( e );
                }
                if ( snFound )
                    toAdd = snLoc.getVersion();
                else
                    continue;
            }
            else
                toAdd = v;

            gam.getVersions().add( toAdd );
        }

        // cache it
        if ( _mdCache != null )
        {
            _mdCache.updateGA( _repo.getId(), gam );
        }

        return gam.getVersions();
    }

    /**
     * clean the list of duplicate TSs of the same SN (if any)
     * 
     * @param gaVersions
     */
    private String findDuplicateSnapshot( String ver, TreeSet<String> versions )
    {
        if ( !ver.matches( Artifact.SNAPSHOT_TS_REGEX ) )
            return null;

        String base = ArtifactLocation.stripTS( ver );

        for ( String v : versions )
        {
            if ( !v.startsWith( base ) )
                continue;

            if ( v.matches( Artifact.SNAPSHOT_TS_REGEX ) )
            {
                Comparator<String> vc = new VersionComparator();

                if ( vc.compare( ver, v ) > 0 )
                    return v;
                else
                    return null;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    /**
     * direct metadata search, no redirects, first attempt
     */
    public MetadataResults readVersions( Collection<ArtifactMetadata> query )
        throws RepositoryException
    {
        if ( query == null || query.size() < 1 )
            return null;

        MetadataResults res = new MetadataResults( query.size() );

        String root = _repo.getServer().getURL().toString();

        for ( ArtifactMetadata md : query )
        {
            if( ! _repo.getRepositoryQualityRange().isAcceptedQuality( md.getRequestedQuality() ) )
                continue;
            
            ArtifactLocation loc = new ArtifactLocation( root, md );

            TreeSet<String> versions = null;

            try
            {
                versions = getCachedVersions( loc, md, res );

                if ( Util.isEmpty( versions ) )
                    continue;
            }
            catch ( Exception e )
            {
                res.addError( md, e );
                continue;
            }

            Quality vq = new Quality( md.getVersion() );

            boolean lookForSnapshot = vq.equals( Quality.SNAPSHOT_QUALITY );
            boolean lookForLatest = vq.equals( Quality.FIXED_LATEST_QUALITY );
            boolean lookForRelease = vq.equals( Quality.FIXED_RELEASE_QUALITY );

            boolean lookForVirtual = lookForLatest || lookForRelease || lookForSnapshot;

            if ( lookForVirtual )
            {
                String found = null;

                if ( lookForLatest )
                    found = versions.last();
                else if ( lookForRelease )
                {
                    int len = versions.size();

                    String[] vers = versions.toArray( new String[len] );

                    for ( int i = len - 1; i >= 0; i-- )
                    {
                        Quality q = new Quality( vers[i] );

                        if ( q.getQuality().equals( QualityEnum.snapshot ) )
                            continue;

                        found = vers[i];

                        break;
                    }
                }
                else
                {
                    String base = ArtifactLocation.stripSN( md.getVersion() );

                    if ( base != null )
                    {
                        int len = base.length();

                        for ( String v : versions )
                        {
                            if ( !base.regionMatches( 0, v, 0, len ) )
                                continue;

                            Quality q = new Quality( v );

                            if ( q.equals( Quality.SNAPSHOT_TS_QUALITY ) )
                            {
                                found = v;
                                break;
                            }
                        }
                    }
                }

                if ( found != null )
                {
                    ArtifactMetadata vmd = new ArtifactMetadata();
                    vmd.setGroupId( md.getGroupId() );
                    vmd.setArtifactId( md.getArtifactId() );
                    vmd.setClassifier( md.getClassifier() );
                    vmd.setType( md.getType() );
                    vmd.setVersion( found );
                    
                    vmd.setTracker( this );

                    res = MetadataResults.add( res, md, vmd );
                }

                continue;
            }

            VersionRange versionQuery;
            try
            {
                versionQuery = VersionRangeFactory.create( md.getVersion(), _repo.getVersionRangeQualityRange() );
            }
            catch ( VersionException e )
            {
                res.addError( md, new RepositoryException( e ) );
                continue;
            }

            for ( String version : versions )
            {
                Quality q = new Quality( version );

                if ( !_repo.isAcceptedQuality( q ) )
                    continue;

                if ( !versionQuery.includes( version ) )
                    continue;

                ArtifactMetadata vmd = new ArtifactMetadata();
                vmd.setGroupId( md.getGroupId() );
                vmd.setArtifactId( md.getArtifactId() );
                vmd.setClassifier( md.getClassifier() );
                vmd.setType( md.getType() );
                vmd.setVersion( version );
                
                vmd.setTracker( this );

                res = MetadataResults.add( res, md, vmd );
            }

        }

        return res;
    }

    // ---------------------------------------------------------------------------------------------------------------
    public byte[] readRawData( ArtifactMetadata md, String classifier, String type )
        throws MetadataReaderException
    {
        return readRawData( md, classifier, type, false );
    }

    // ---------------------------------------------------------------------------------------------------------------
    public byte[] readRawData( ArtifactMetadata md, String classifier, String type, boolean exempt )
        throws MetadataReaderException
    {
        if( ! _repo.getRepositoryQualityRange().isAcceptedQuality( md.getRequestedQuality() ) )
            return null;

        byte[] res = null;
        ArtifactMetadata mod = null;

        // if( _log.isDebugEnabled() )
        // _log.debug( "reading "+bmd+" from " + _repo.getId() );

        // only cache poms at the moment
        if ( _mdCache != null && "pom".equals( type ) )
        {
            mod = new ArtifactMetadata( md );
            mod.setClassifier( classifier );
            mod.setType( type );

            try
            {
                res = _mdCache.findRaw( mod );
                if ( res != null )
                {
                    if ( LOG.isDebugEnabled() )
                        LOG.debug( "found " + md + " in the cache" );
                    return res;
                }
            }
            catch ( MetadataCacheException e )
            {
                // problems with the cache - move on
                LOG.error( LANG.getMessage( "cached.data.problem", e.getMessage(), md.toString() ) );
            }
        }

        mod =
            new ArtifactMetadata( md.getGroupId() + ":" + md.getArtifactId() + ":" + md.getVersion() + ":"
                + ( classifier == null ? "" : classifier ) + ":" + ( type == null ? md.getType() : type ) );

        // ArtifactLocation loc = new ArtifactLocation( "", mod );

        // String bmdPath = loc.getAbsPath();

        String mdPath =
            md.getGroupId().replace( '.', '/' ) + '/' + md.getArtifactId() + '/'
                + ArtifactLocation.calculateVersionDir( md.getVersion() ) + '/' + md.getBaseName( classifier ) + '.'
                + ( type == null ? md.getType() : type );

        res = readRawData( mdPath, exempt );

        if ( LOG.isDebugEnabled() )
            LOG.debug( "POM bytes not cached, read "+ (res == null ? 0:res.length) +" bytes from " + mdPath );

        if ( _mdCache != null && res != null && mod != null )
        {
            try
            {
                _mdCache.saveRaw( mod, res );
            }
            catch ( MetadataCacheException e )
            {
                throw new MetadataReaderException( e );
            }
        }

        return res;
    }

    // ---------------------------------------------------------------------------------------------------------------
    public byte[] readRawData( String path )
        throws MetadataReaderException
    {
        return readRawData( path, false );
    }

    // ---------------------------------------------------------------------------------------------------------------
    public byte[] readRawData( String path, boolean exempt )
        throws MetadataReaderException
    {
        if ( path == null || path.length() < 1 )
            return null;

        FileInputStream fis = null;
        try
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream( 10240 );

            String separator = "/";
            if ( path.startsWith( separator ) )
                separator = "";

            String url = ( path.startsWith( "http" ) ? "" : _repo.getServer().getURL().toString() + separator ) + path;

            Binding binding = new Binding( new URL( url ), baos, exempt );
            DefaultRetrievalRequest request = new DefaultRetrievalRequest();
            request.addBinding( binding );

            DefaultRetriever transport = new DefaultRetriever();
            transport.setServers( _servers );
            RetrievalResponse response = transport.retrieve( request );
            transport.stop();

            if ( response.hasExceptions() )
            {
                if( LOG.isDebugEnabled() )
                    LOG.debug( LANG.getMessage( "read.raw.exceptions", path, response.getExceptions().toString() ) );

                return null;
            }
            
            if( LOG.isInfoEnabled() )
                LOG.info( LANG.getMessage( "read.raw.length", url, Util.convertLength( baos.size() ) ) );

            return baos.toByteArray();
        }
        catch ( IOException e )
        {
            throw new MetadataReaderException( e );
        }
        catch ( HttpClientException e )
        {
            throw new MetadataReaderException(e);
        }
        finally
        {
            if ( fis != null )
                try
                {
                    fis.close();
                }
                catch ( Exception any )
                {
                }
        }
    }

    public boolean canHandle( String protocol )
    {
        return AbstractRepository.DEFAULT_REMOTE_READ_PROTOCOL.equals( protocol );
    }

    public void close()
    {
//        if( _transport != null )
//            _transport.stop();
        
        if( _defaultRoot != null )
            FileUtil.delete( _defaultRoot );
        _defaultRoot = null;
    }

    public String[] getProtocols()
    {
        return _protocols;
    }

}
