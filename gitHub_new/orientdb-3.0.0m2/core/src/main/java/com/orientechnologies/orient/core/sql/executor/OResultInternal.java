package com.orientechnologies.orient.core.sql.executor;

import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.OContextualRecordId;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.record.OElement;
import com.orientechnologies.orient.core.record.ORecordInternal;
import com.orientechnologies.orient.core.record.impl.OBlob;
import com.orientechnologies.orient.core.record.impl.ODocument;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luigidellaquila on 06/07/16.
 */
public class OResultInternal implements OResult {
  protected Map<String, Object> content = new LinkedHashMap<>();
  protected Map<String, Object> metadata;
  protected OIdentifiable       element;

  public void setProperty(String name, Object value) {
    if (value instanceof Optional) {
      value = ((Optional) value).orElse(null);
    }
    if (value instanceof OResult && ((OResult) value).isElement()) {
      content.put(name, ((OResult) value).getElement().get());
    } else {
      content.put(name, value);
    }
  }

  public void removeProperty(String name) {
    content.remove(name);
  }

  public <T> T getProperty(String name) {
    if (content.containsKey(name)) {
      return (T) content.get(name);
    }
    if (element != null) {
      return ((ODocument) element.getRecord()).getProperty(name);
    }
    return null;
  }

  public Set<String> getPropertyNames() {
    Set<String> result = new LinkedHashSet<>();
    if (element != null) {
      result.addAll(((ODocument) element.getRecord()).getPropertyNames());
    }
    result.addAll(content.keySet());
    return result;
  }

  @Override
  public boolean isElement() {
    return this.element != null;
  }

  public Optional<OElement> getElement() {
    if (element == null || element instanceof OElement) {
      return Optional.ofNullable((OElement) element);
    }
    if (element instanceof OIdentifiable) {
      return Optional.ofNullable(element.getRecord());
    }
    return Optional.empty();
  }

  @Override
  public OElement toElement() {
    if (isElement()) {
      return getElement().get();
    }
    ODocument doc = new ODocument();
    for (String s : getPropertyNames()) {
      if (s == null) {
        continue;
      } else if (s.equalsIgnoreCase("@rid")) {
        Object newRid = getProperty(s);
        if (newRid instanceof OIdentifiable) {
          newRid = ((OIdentifiable) newRid).getIdentity();
        } else {
          continue;
        }
        ORecordId oldId = (ORecordId) doc.getIdentity();
        oldId.setClusterId(((ORID) newRid).getClusterId());
        oldId.setClusterPosition(((ORID) newRid).getClusterPosition());
      } else if (s.equalsIgnoreCase("@version")) {
        Object v = getProperty(s);
        if (v instanceof Number) {
          ORecordInternal.setVersion(doc, ((Number) v).intValue());
        } else {
          continue;
        }
      } else if (s.equalsIgnoreCase("@class")) {
        doc.setClassName(getProperty(s));
      } else {
        doc.setProperty(s, convertToElement(getProperty(s)));
      }
    }
    return doc;
  }

  @Override
  public Optional<ORID> getIdentity() {
    if (element != null) {
      return Optional.of(element.getIdentity());
    }
    return Optional.empty();
  }

  @Override
  public boolean isBlob() {
    return this.element != null && this.element.getRecord() instanceof OBlob;
  }

  @Override
  public Optional<OBlob> getBlob() {
    if (isBlob()) {
      return Optional.ofNullable(this.element.getRecord());
    }
    return null;
  }

  @Override
  public Object getMetadata(String key) {
    if (key == null) {
      return null;
    }
    return metadata == null ? null : metadata.get(key);
  }

  public void setMetadata(String key, Object value) {
    if (key == null) {
      return;
    }
    if (metadata == null) {
      metadata = new HashMap<>();
    }
    metadata.put(key, value);
  }

  public void clearMetadata() {
    metadata = null;
  }

  public void removeMetadata(String key) {
    if (key == null || metadata == null) {
      return;
    }
    metadata.remove(key);
  }

  public void addMetadata(Map<String, Object> values) {
    if (values == null) {
      return;
    }
    if (this.metadata == null) {
      this.metadata = new HashMap<>();
    }
    this.metadata.putAll(values);
  }

  @Override
  public Set<String> getMetadataKeys() {
    return metadata == null ? Collections.emptySet() : metadata.keySet();
  }

  private Object convertToElement(Object property) {
    if (property instanceof OResult) {
      return ((OResult) property).toElement();
    }
    if (property instanceof List) {
      return ((List) property).stream().map(x -> convertToElement(x)).collect(Collectors.toList());
    }

    if (property instanceof Set) {
      return ((Set) property).stream().map(x -> convertToElement(x)).collect(Collectors.toSet());
    }

    return property;
  }

  public void setElement(OIdentifiable element) {
    if (element instanceof OElement) {
      this.element = element;
    } else if (element instanceof OIdentifiable) {
      this.element = element.getRecord();
    } else {
      this.element = element;
    }
    if (element instanceof OContextualRecordId) {
      this.addMetadata(((OContextualRecordId) element).getContext());
    }
  }

  @Override
  public String toString() {
    if (element != null) {
      return element.toString();
    }
    return "{\n" + content.entrySet().stream().map(x -> x.getKey() + ": " + x.getValue()).reduce("", (a, b) -> a + b + "\n")
        + "}\n";
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof OResultInternal)) {
      return false;
    }
    OResultInternal resultObj = (OResultInternal) obj;
    if (element != null) {
      if (!resultObj.getElement().isPresent()) {
        return false;
      }
      return element.equals(resultObj.getElement().get());
    } else {
      if (resultObj.getElement().isPresent()) {
        return false;
      }
      return this.content.equals(resultObj.content);
    }
  }

  @Override
  public int hashCode() {
    if (element != null) {
      return element.hashCode();
    }
    return content.hashCode();
  }
}
