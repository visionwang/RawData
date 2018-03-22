package com.orientechnologies.orient.core.sql.executor;

import com.orientechnologies.orient.core.db.document.ODatabaseDocument;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.metadata.security.ORole;
import com.orientechnologies.orient.core.metadata.security.ORule;
import com.orientechnologies.orient.core.metadata.security.OSecurityRole;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Luigi Dell'Aquila (l.dellaquila-(at)-orientdb.com)
 */
public class ORevokeStatementExecutionTest {
  static ODatabaseDocument db;

  @BeforeClass public static void beforeClass() {
    db = new ODatabaseDocumentTx("memory:ORevokeStatementExecutionTest");
    db.create();
  }

  @AfterClass public static void afterClass() {
    db.drop();
  }

  @Test public void testSimple() {
    ORole testRole = db.getMetadata().getSecurity().createRole("testRole", OSecurityRole.ALLOW_MODES.DENY_ALL_BUT);
    Assert.assertFalse(testRole.allow(ORule.ResourceGeneric.SERVER, "server", ORole.PERMISSION_EXECUTE));
    db.command("GRANT execute on server.remove to testRole");
    testRole = db.getMetadata().getSecurity().getRole("testRole");
    Assert.assertTrue(testRole.allow(ORule.ResourceGeneric.SERVER, "remove", ORole.PERMISSION_EXECUTE));
    db.command("REVOKE execute on server.remove from testRole");
    testRole = db.getMetadata().getSecurity().getRole("testRole");
    Assert.assertFalse(testRole.allow(ORule.ResourceGeneric.SERVER, "remove", ORole.PERMISSION_EXECUTE));
  }



}
