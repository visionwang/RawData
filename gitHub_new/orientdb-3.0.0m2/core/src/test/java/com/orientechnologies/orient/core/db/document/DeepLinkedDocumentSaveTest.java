package com.orientechnologies.orient.core.db.document;

import com.orientechnologies.orient.core.record.impl.ODocument;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class DeepLinkedDocumentSaveTest {

  @Test
  public void testLinked() {
    ODatabaseDocument db = new ODatabaseDocumentTx("memory:" + DeepLinkedDocumentSaveTest.class.getSimpleName());
    db.create();
    try {
      final Set<ODocument> docs = new HashSet<>();

      db.getMetadata().getSchema().createClass("Test");
      ODocument doc = new ODocument("Test");
      docs.add(doc);
      for (int i = 0; i < 3000; i++)
        docs.add(doc = new ODocument("Test").field("linked", doc));
      db.save(doc);

      assertEquals(3001, db.countClass("Test"));

      for (ODocument d : docs)
        assertEquals(1, d.getVersion());
    } finally {
      db.drop();
    }
  }

  @Test
  public void testLinkedTx() {
    ODatabaseDocument db = new ODatabaseDocumentTx("memory:" + DeepLinkedDocumentSaveTest.class.getSimpleName());
    db.create();
    try {
      final Set<ODocument> docs = new HashSet<>();

      db.getMetadata().getSchema().createClass("Test");

      db.begin();
      ODocument doc = new ODocument("Test");
      docs.add(doc);
      for (int i = 0; i < 3000; i++)
        docs.add(doc = new ODocument("Test").field("linked", doc));
      db.save(doc);
      db.commit();

      assertEquals(3001, db.countClass("Test"));

      for (ODocument d : docs)
        assertEquals(1, d.getVersion());
    } finally {
      db.drop();
    }
  }

}
