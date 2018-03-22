package com.orientechnologies.lucene.integration;

import com.orientechnologies.common.io.OFileUtils;
import com.orientechnologies.common.log.OLogManager;
import com.orientechnologies.orient.core.config.OGlobalConfiguration;
import com.orientechnologies.orient.core.db.ODatabasePool;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import com.orientechnologies.orient.server.OServer;
import com.orientechnologies.orient.server.OServerMain;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class OLuceneIndexCrashRestoreIT {

  private AtomicLong idGen;
//  private ODatabaseDocumentTx testDocumentTx;

  private ExecutorService executorService;
  private Process         serverProcess;
  private List<String>    names;
  private OrientDB        orientdb;
  private ODatabasePool   databasePool;

  @Before
  public void beforeMethod() throws Exception {
    executorService = Executors.newCachedThreadPool();
    idGen = new AtomicLong();
    spawnServer();

    orientdb = new OrientDB("remote:localhost:3900", "root", "root", OrientDBConfig.defaultConfig());

    databasePool = new ODatabasePool(orientdb, "testLuceneCrash", "admin", "admin");

    //names to be used for person to be indexed
    names = Arrays.asList("John", "Robert", "Jane", "andrew", "Scott", "luke", "Enriquez", "Luis", "Gabriel", "Sara");

  }

  public void spawnServer() throws Exception {
    OLogManager.instance().installCustomFormatter();
    OGlobalConfiguration.WAL_FUZZY_CHECKPOINT_INTERVAL.setValue(1000000);
    OGlobalConfiguration.RID_BAG_EMBEDDED_TO_SBTREEBONSAI_THRESHOLD.setValue(3);
    OGlobalConfiguration.FILE_LOCK.setValue(false);

    final String buildDirectory = "./target/testLuceneCrash";

    final File buildDir = new File(buildDirectory);
    if (buildDir.exists()) {
      OFileUtils.deleteRecursively(buildDir);
    }

    buildDir.mkdirs();

    final File mutexFile = new File(buildDir, "mutex.ct");
    final RandomAccessFile mutex = new RandomAccessFile(mutexFile, "rw");
    mutex.seek(0);
    mutex.write(0);

    String javaExec = System.getProperty("java.home") + "/bin/java";
    javaExec = new File(javaExec).getCanonicalPath();

    ProcessBuilder processBuilder = new ProcessBuilder(javaExec,
        "-Xmx2048m",
        "-XX:MaxDirectMemorySize=512g",
        "-classpath",
        System.getProperty("java.class.path"),
        "-DmutexFile=" + mutexFile.getAbsolutePath(),
        "-DORIENTDB_HOME=" + buildDirectory,
        RemoteDBRunner.class.getName());

    processBuilder.inheritIO();
    serverProcess = processBuilder.start();

    boolean started = false;
    do {
      System.out.println(": Wait for server start");
      TimeUnit.SECONDS.sleep(5);
      mutex.seek(0);
      started = mutex.read() == 1;
    } while (!started);

    mutex.close();
    mutexFile.delete();
    System.out.println(": Server was started");
  }

  @After
  public void tearDown() {
    File buildDir = new File("./target/databases");
    OFileUtils.deleteRecursively(buildDir);
    Assert.assertFalse(buildDir.exists());

//    databasePool.close();

    orientdb.close();
  }

  @Test
//  @Ignore
  public void testEntriesAddition() throws Exception {
    createSchema(databasePool);

    //first round
    System.out.println("Start data propagation 1");

    List<DataPropagationTask> futures = startLoaders();

    System.out.println("Wait for 1 minutes");
    TimeUnit.MINUTES.sleep(1);

    ODatabaseSession db = databasePool.acquire();
    //wildcard will not work
    OResultSet res = db.query("select from Person where name lucene 'Rob*' ");
    assertThat(res).hasSize(0);
    res.close();

    //plain name fetch docs
    res = db.query("select from Person where name lucene 'Robert' LIMIT 20");
    assertThat(res).hasSize(20);
    res.close();
    db.close();
    //crash the server
    serverProcess.destroyForcibly();

    serverProcess.waitFor();
    //crash the server

    System.out.println("Process was CRASHED");

    System.out.println("stop loaders");
    stopLoaders(futures);

    System.out.println("All loaders done");

    //now we start embedded
    System.out.println("START AGAIN");

    //start embedded
    OServer server = OServerMain.create();
    InputStream conf = RemoteDBRunner.class.getResourceAsStream("index-crash-config.xml");

    server.startup(conf);
    server.activate();

    while (!server.isActive()) {
      System.out.println("server active = " + server.isActive());
      TimeUnit.SECONDS.sleep(1);
    }

    //test query
    db = databasePool.acquire();
    db.getMetadata().reload();

    OIndex<?> index = db.getMetadata().getIndexManager().getIndex("Person.name");
    assertThat(index).isNotNull();

    //sometimes the metadata is null!!!!!
    assertThat((Iterable<? extends Map.Entry<String, Object>>) index.getMetadata()).isNotNull();

    assertThat(index.getMetadata().<String>field("default")).isNotNull();
    assertThat(index.getMetadata().<String>field("default"))
        .isEqualTo("org.apache.lucene.analysis.core.KeywordAnalyzer");
    assertThat(index.getMetadata().<String>field("unknownKey"))
        .isEqualTo("unknownValue");

    //sometimes it is not null, and all works fine
    res = db.query("select from Person where name lucene 'Rob*' ");

    assertThat(res).hasSize(0);
    res.close();
    res = db.query("select from Person where name lucene 'Robert' LIMIT 20");

    assertThat(res).hasSize(20);
    res.close();
    db.close();
    //shutdown embedded
    server.shutdown();

  }

  private void stopLoaders(List<DataPropagationTask> futures) {
    for (DataPropagationTask future : futures) {

      future.stop();
    }
  }

  private List<DataPropagationTask> startLoaders() {
    List<DataPropagationTask> futures = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      final DataPropagationTask loader = new DataPropagationTask(databasePool);
      executorService.submit(loader);
      futures.add(loader);
    }
    return futures;
  }

  private void createSchema(ODatabasePool pool) {

    final ODatabaseSession db = pool.acquire();

    System.out.println("create index for db:: " + db.getURL());
    db.command("Create class Person");
    db.command("Create property Person.name STRING");
    db.command(
        "Create index Person.name on Person(name) FULLTEXT ENGINE LUCENE METADATA {'default':'org.apache.lucene.analysis.core.KeywordAnalyzer', 'unknownKey':'unknownValue'}");
    db.getMetadata().getIndexManager().reload();

    System.out.println(db.getMetadata().getIndexManager().getIndex("Person.name").getConfiguration().toJSON());
    db.close();
  }

  public static final class RemoteDBRunner {
    public static void main(String[] args) throws Exception {
      System.out.println("prepare server");
      OGlobalConfiguration.RID_BAG_EMBEDDED_TO_SBTREEBONSAI_THRESHOLD.setValue(3);
      OGlobalConfiguration.WAL_FUZZY_CHECKPOINT_INTERVAL.setValue(100000000);

      System.out.println("create server instance");
      OServer server = OServerMain.create();
      InputStream conf = RemoteDBRunner.class.getResourceAsStream("index-crash-config.xml");

      server.startup(conf);
      server.activate();

      final String mutexFile = System.getProperty("mutexFile");
      System.out.println("mutexFile = " + mutexFile);

      final RandomAccessFile mutex = new RandomAccessFile(mutexFile, "rw");
      mutex.seek(0);
      mutex.write(1);
      mutex.close();
    }
  }

  public class DataPropagationTask implements Callable<Void> {

    private final ODatabasePool pool;

    private volatile boolean stop;

    public DataPropagationTask(ODatabasePool pool) {
      stop = false;
      this.pool = pool;
    }

    public void stop() {
      stop = true;
    }

    @Override
    public Void call() throws Exception {

      ODatabaseSession testDB = null;
      try {
        testDB = pool.acquire();
        while (!stop) {
          long id = idGen.getAndIncrement();
          long ts = System.currentTimeMillis();

          if (id % 1000 == 0) {
            System.out.println(Thread.currentThread().getName() + " inserted:: " + id);
            testDB.commit();
          }
          if (id % 2000 == 0) {
            final OResultSet resultSet = testDB.command("delete from Person where name lucene 'Robert' ");
            System.out.println(Thread.currentThread().getName() + " deleted:: " + resultSet.next().getProperty("count"));
            testDB.commit();
          }
          int nameIdx = (int) (id % names.size());

          for (int i = 0; i < 10; i++) {
            String insert = "insert into person (name) values ('" + names.get(nameIdx) + "')";
            testDB.command(insert).close();
          }

        }
      } catch (Exception e) {
        throw e;
      } finally {
        if (testDB != null && !testDB.isClosed())
          testDB.close();
      }

      return null;
    }
  }
}
