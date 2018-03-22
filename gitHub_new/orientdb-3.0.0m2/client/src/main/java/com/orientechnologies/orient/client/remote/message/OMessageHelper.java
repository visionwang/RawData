package com.orientechnologies.orient.client.remote.message;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.common.util.OCommonConst;
import com.orientechnologies.orient.client.remote.OClusterRemote;
import com.orientechnologies.orient.client.remote.OCollectionNetworkSerializer;
import com.orientechnologies.orient.client.remote.message.tx.IndexChange;
import com.orientechnologies.orient.client.remote.message.tx.ORecordOperationRequest;
import com.orientechnologies.orient.core.Orient;
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal;
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.db.record.ORecordOperation;
import com.orientechnologies.orient.core.db.record.ridbag.sbtree.OBonsaiCollectionPointer;
import com.orientechnologies.orient.core.exception.OSerializationException;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.id.ORecordId;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.ORecord;
import com.orientechnologies.orient.core.record.ORecordInternal;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.record.impl.OEdgeDelegate;
import com.orientechnologies.orient.core.record.impl.OVertexDelegate;
import com.orientechnologies.orient.core.serialization.serializer.record.ORecordSerializer;
import com.orientechnologies.orient.core.serialization.serializer.record.binary.ORecordSerializerNetworkV37;
import com.orientechnologies.orient.core.serialization.serializer.result.binary.OResultSerializerNetwork;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;
import com.orientechnologies.orient.core.storage.OCluster;
import com.orientechnologies.orient.core.storage.OPhysicalPosition;
import com.orientechnologies.orient.core.tx.OTransactionIndexChanges;
import com.orientechnologies.orient.core.tx.OTransactionIndexChangesPerKey;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelBinaryProtocol;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelDataInput;
import com.orientechnologies.orient.enterprise.channel.binary.OChannelDataOutput;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public class OMessageHelper {

  public static void writeIdentifiable(OChannelDataOutput channel, final OIdentifiable o, ORecordSerializer serializer)
      throws IOException {
    if (o == null)
      channel.writeShort(OChannelBinaryProtocol.RECORD_NULL);
    else if (o instanceof ORecordId) {
      channel.writeShort(OChannelBinaryProtocol.RECORD_RID);
      channel.writeRID((ORID) o);
    } else {
      writeRecord(channel, o.getRecord(), serializer);
    }
  }

  public static void writeRecord(OChannelDataOutput channel, final ORecord iRecord, ORecordSerializer serializer)
      throws IOException {
    channel.writeShort((short) 0);
    channel.writeByte(ORecordInternal.getRecordType(iRecord));
    channel.writeRID(iRecord.getIdentity());
    channel.writeVersion(iRecord.getVersion());
    try {
      final byte[] stream = getRecordBytes(iRecord, serializer);
      channel.writeBytes(stream);
    } catch (Exception e) {
      channel.writeBytes(null);
      final String message = "Error on unmarshalling record " + iRecord.getIdentity().toString() + " (" + e + ")";

      throw OException.wrapException(new OSerializationException(message), e);
    }
  }

  public static byte[] getRecordBytes(final ORecord iRecord, ORecordSerializer serializer) {

    final byte[] stream;
    String dbSerializerName = null;
    if (ODatabaseRecordThreadLocal.INSTANCE.getIfDefined() != null)
      dbSerializerName = ((ODatabaseDocumentInternal) iRecord.getDatabase()).getSerializer().toString();
    if (ORecordInternal.getRecordType(iRecord) == ODocument.RECORD_TYPE && (dbSerializerName == null || !dbSerializerName
        .equals(serializer.toString()))) {
      ((ODocument) iRecord).deserializeFields();
      stream = serializer.toStream(iRecord, false);
    } else
      stream = iRecord.toStream();

    return stream;
  }

  public static Map<UUID, OBonsaiCollectionPointer> readCollectionChanges(OChannelDataInput network) throws IOException {
    Map<UUID, OBonsaiCollectionPointer> collectionsUpdates = new HashMap<>();
    int count = network.readInt();
    for (int i = 0; i < count; i++) {
      final long mBitsOfId = network.readLong();
      final long lBitsOfId = network.readLong();

      final OBonsaiCollectionPointer pointer = OCollectionNetworkSerializer.INSTANCE.readCollectionPointer(network);

      collectionsUpdates.put(new UUID(mBitsOfId, lBitsOfId), pointer);
    }
    return collectionsUpdates;
  }

  public static void writeCollectionChanges(OChannelDataOutput channel, Map<UUID, OBonsaiCollectionPointer> changedIds)
      throws IOException {
    channel.writeInt(changedIds.size());
    for (Entry<UUID, OBonsaiCollectionPointer> entry : changedIds.entrySet()) {
      channel.writeLong(entry.getKey().getMostSignificantBits());
      channel.writeLong(entry.getKey().getLeastSignificantBits());
      OCollectionNetworkSerializer.INSTANCE.writeCollectionPointer(channel, entry.getValue());
    }
  }

  public static void writePhysicalPositions(OChannelDataOutput channel, OPhysicalPosition[] previousPositions) throws IOException {
    if (previousPositions == null) {
      channel.writeInt(0); // NO ENTRIEs
    } else {
      channel.writeInt(previousPositions.length);

      for (final OPhysicalPosition physicalPosition : previousPositions) {
        channel.writeLong(physicalPosition.clusterPosition);
        channel.writeInt(physicalPosition.recordSize);
        channel.writeVersion(physicalPosition.recordVersion);
      }
    }
  }

  public static OPhysicalPosition[] readPhysicalPositions(OChannelDataInput network) throws IOException {
    final int positionsCount = network.readInt();
    final OPhysicalPosition[] physicalPositions;
    if (positionsCount == 0) {
      physicalPositions = OCommonConst.EMPTY_PHYSICAL_POSITIONS_ARRAY;
    } else {
      physicalPositions = new OPhysicalPosition[positionsCount];

      for (int i = 0; i < physicalPositions.length; i++) {
        final OPhysicalPosition position = new OPhysicalPosition();

        position.clusterPosition = network.readLong();
        position.recordSize = network.readInt();
        position.recordVersion = network.readVersion();

        physicalPositions[i] = position;
      }
    }
    return physicalPositions;
  }

  public static OCluster[] readClustersArray(final OChannelDataInput network) throws IOException {

    final int tot = network.readShort();
    OCluster[] clusters = new OCluster[tot];
    for (int i = 0; i < tot; ++i) {
      final OClusterRemote cluster = new OClusterRemote();
      String clusterName = network.readString();
      final int clusterId = network.readShort();
      if (clusterName != null) {
        clusterName = clusterName.toLowerCase(Locale.ENGLISH);
        cluster.configure(null, clusterId, clusterName);
        if (clusterId >= clusters.length)
          clusters = Arrays.copyOf(clusters, clusterId + 1);
        clusters[clusterId] = cluster;
      }
    }
    return clusters;
  }

  public static void writeClustersArray(OChannelDataOutput channel, OCluster[] clusters, int protocolVersion) throws IOException {
    int clusterCount = 0;
    for (OCluster c : clusters) {
      if (c != null) {
        ++clusterCount;
      }
    }
    channel.writeShort((short) clusterCount);

    for (OCluster c : clusters) {
      if (c != null) {
        channel.writeString(c.getName());
        channel.writeShort((short) c.getId());
        if (protocolVersion < 24) {
          channel.writeString("none");
          channel.writeShort((short) -1);
        }
      }
    }
  }

  static void writeTransactionEntry(final OChannelDataOutput iNetwork, final ORecordOperationRequest txEntry,
      ORecordSerializer serializer) throws IOException {
    iNetwork.writeByte((byte) 1);
    iNetwork.writeByte(txEntry.getType());
    iNetwork.writeRID(txEntry.getId());
    iNetwork.writeByte(txEntry.getRecordType());

    switch (txEntry.getType()) {
    case ORecordOperation.CREATED:
      iNetwork.writeBytes(txEntry.getRecord());
      break;

    case ORecordOperation.UPDATED:
      iNetwork.writeVersion(txEntry.getVersion());
      iNetwork.writeBytes(txEntry.getRecord());
      iNetwork.writeBoolean(txEntry.isContentChanged());
      break;

    case ORecordOperation.DELETED:
      iNetwork.writeVersion(txEntry.getVersion());
      break;
    }
  }

  static ORecordOperationRequest readTransactionEntry(OChannelDataInput channel, ORecordSerializer ser) throws IOException {
    ORecordOperationRequest entry = new ORecordOperationRequest();
    entry.setType(channel.readByte());
    entry.setId(channel.readRID());
    entry.setRecordType(channel.readByte());
    switch (entry.getType()) {
    case ORecordOperation.CREATED:
      entry.setRecord(channel.readBytes());
      break;
    case ORecordOperation.UPDATED:
      entry.setVersion(channel.readVersion());
      entry.setRecord(channel.readBytes());
      entry.setContentChanged(channel.readBoolean());
      break;
    case ORecordOperation.DELETED:
      entry.setVersion(channel.readVersion());
      break;
    default:
      break;
    }
    return entry;
  }

  static void writeTransactionIndexChanges(OChannelDataOutput network, ORecordSerializerNetworkV37 serializer,
      List<IndexChange> changes) throws IOException {
    network.writeInt(changes.size());
    for (IndexChange indexChange : changes) {
      network.writeString(indexChange.getName());
      network.writeBoolean(indexChange.getKeyChanges().cleared);

      int size = indexChange.getKeyChanges().changesPerKey.size();
      if (indexChange.getKeyChanges().nullKeyChanges != null) {
        size += 1;
      }
      network.writeInt(size);
      if (indexChange.getKeyChanges().nullKeyChanges != null) {
        network.writeByte((byte) -1);
        network.writeInt(indexChange.getKeyChanges().nullKeyChanges.entries.size());
        for (OTransactionIndexChangesPerKey.OTransactionIndexEntry perKeyChange : indexChange
            .getKeyChanges().nullKeyChanges.entries) {
          network.writeInt(perKeyChange.operation.ordinal());
          network.writeRID(perKeyChange.value.getIdentity());
        }
      }
      for (OTransactionIndexChangesPerKey change : indexChange.getKeyChanges().changesPerKey.values()) {
        OType type = OType.getTypeByValue(change.key);
        byte[] value = serializer.serializeValue(change.key, type);
        network.writeByte((byte) type.getId());
        network.writeBytes(value);
        network.writeInt(change.entries.size());
        for (OTransactionIndexChangesPerKey.OTransactionIndexEntry perKeyChange : change.entries) {
          OTransactionIndexChanges.OPERATION op = perKeyChange.operation;
          if (op == OTransactionIndexChanges.OPERATION.REMOVE && perKeyChange.value == null)
            op = OTransactionIndexChanges.OPERATION.CLEAR;

          network.writeInt(op.ordinal());
          if (op != OTransactionIndexChanges.OPERATION.CLEAR)
            network.writeRID(perKeyChange.value.getIdentity());
        }
      }
    }
  }

  static List<IndexChange> readTransactionIndexChanges(OChannelDataInput channel, ORecordSerializerNetworkV37 serializer)
      throws IOException {
    List<IndexChange> changes = new ArrayList<>();
    int val = channel.readInt();
    while (val-- > 0) {
      String indexName = channel.readString();
      boolean cleared = channel.readBoolean();
      OTransactionIndexChanges entry = new OTransactionIndexChanges();
      entry.cleared = cleared;
      int changeCount = channel.readInt();
      NavigableMap<Object, OTransactionIndexChangesPerKey> entries = new TreeMap<>();
      while (changeCount-- > 0) {
        byte bt = channel.readByte();
        Object key;
        if (bt == -1) {
          key = null;
        } else {
          OType type = OType.getById(bt);
          key = serializer.deserializeValue(channel.readBytes(), type);
        }
        OTransactionIndexChangesPerKey changesPerKey = new OTransactionIndexChangesPerKey(key);
        int keyChangeCount = channel.readInt();
        while (keyChangeCount-- > 0) {
          int op = channel.readInt();
          OTransactionIndexChanges.OPERATION oper = OTransactionIndexChanges.OPERATION.values()[op];
          ORecordId id;
          if (oper == OTransactionIndexChanges.OPERATION.CLEAR) {
            oper = OTransactionIndexChanges.OPERATION.REMOVE;
            id = null;
          } else {
            id = channel.readRID();
          }
          changesPerKey.add(id, oper);
        }
        if (key == null) {
          entry.nullKeyChanges = changesPerKey;
        } else {
          entries.put(changesPerKey.key, changesPerKey);
        }
      }
      entry.changesPerKey = entries;
      changes.add(new IndexChange(indexName, entry));
    }
    return changes;
  }

  public static OIdentifiable readIdentifiable(final OChannelDataInput network, ORecordSerializer serializer) throws IOException {
    final int classId = network.readShort();
    if (classId == OChannelBinaryProtocol.RECORD_NULL)
      return null;

    if (classId == OChannelBinaryProtocol.RECORD_RID) {
      return network.readRID();
    } else {
      final ORecord record = readRecordFromBytes(network, serializer);
      return record;
    }
  }

  static ORecord readRecordFromBytes(OChannelDataInput network, ORecordSerializer serializer) throws IOException {
    byte rec = network.readByte();
    final ORecordId rid = network.readRID();
    final int version = network.readVersion();
    final byte[] content = network.readBytes();

    final ORecord record = Orient.instance().getRecordFactoryManager().newInstance(rec);
    ORecordInternal.setIdentity(record, rid);
    ORecordInternal.setVersion(record, version);
    serializer.fromStream(content, record, null);
    ORecordInternal.unsetDirty(record);
    return record;
  }

  static void writeProjection(OResult item, OChannelDataOutput channel) throws IOException {
    channel.writeByte(OQueryResponse.RECORD_TYPE_PROJECTION);
    OResultSerializerNetwork ser = new OResultSerializerNetwork();
    ser.toStream(item, channel);
  }

  static void writeBlob(OResult row, OChannelDataOutput channel, ORecordSerializer recordSerializer) throws IOException {
    channel.writeByte(OQueryResponse.RECORD_TYPE_BLOB);
    writeIdentifiable(channel, row.getBlob().get(), recordSerializer);
  }

  static void writeVertex(OResult row, OChannelDataOutput channel, ORecordSerializer recordSerializer) throws IOException {
    channel.writeByte(OQueryResponse.RECORD_TYPE_VERTEX);
    writeDocument(channel, row.getElement().get().getRecord(), recordSerializer);
  }

  static void writeElement(OResult row, OChannelDataOutput channel, ORecordSerializer recordSerializer) throws IOException {
    channel.writeByte(OQueryResponse.RECORD_TYPE_ELEMENT);
    writeDocument(channel, row.getElement().get().getRecord(), recordSerializer);
  }

  static void writeEdge(OResult row, OChannelDataOutput channel, ORecordSerializer recordSerializer) throws IOException {
    channel.writeByte(OQueryResponse.RECORD_TYPE_EDGE);
    writeDocument(channel, row.getElement().get().getRecord(), recordSerializer);
  }

  private static void writeDocument(OChannelDataOutput channel, ODocument doc, ORecordSerializer serializer) throws IOException {
    writeIdentifiable(channel, doc, serializer);
  }

  public static void writeResult(OResult row, OChannelDataOutput channel, ORecordSerializer recordSerializer) throws IOException {
    if (row.isBlob()) {
      writeBlob(row, channel, recordSerializer);
    } else if (row.isVertex()) {
      writeVertex(row, channel, recordSerializer);
    } else if (row.isEdge()) {
      writeEdge(row, channel, recordSerializer);
    } else if (row.isElement()) {
      writeElement(row, channel, recordSerializer);
    } else {
      writeProjection(row, channel);
    }
  }

  private static OResult readBlob(OChannelDataInput channel) throws IOException {
    ORecordSerializer serializer = ORecordSerializerNetworkV37.INSTANCE;
    OResultInternal result = new OResultInternal();
    result.setElement(readIdentifiable(channel, serializer));
    return result;
  }

  public static OResult readResult(OChannelDataInput channel) throws IOException {
    byte type = channel.readByte();
    switch (type) {
    case OQueryResponse.RECORD_TYPE_BLOB:
      return readBlob(channel);
    case OQueryResponse.RECORD_TYPE_VERTEX:
      return readVertex(channel);
    case OQueryResponse.RECORD_TYPE_EDGE:
      return readEdge(channel);
    case OQueryResponse.RECORD_TYPE_ELEMENT:
      return readElement(channel);
    case OQueryResponse.RECORD_TYPE_PROJECTION:
      return readProjection(channel);

    }
    return new OResultInternal();
  }

  private static OResult readElement(OChannelDataInput channel) throws IOException {
    OResultInternal result = new OResultInternal();
    result.setElement(readDocument(channel));
    return result;
  }

  private static OResult readVertex(OChannelDataInput channel) throws IOException {
    OResultInternal result = new OResultInternal();
    result.setElement(new OVertexDelegate((ODocument) readDocument(channel)));
    return result;
  }

  private static OResult readEdge(OChannelDataInput channel) throws IOException {
    OResultInternal result = new OResultInternal();
    result.setElement(new OEdgeDelegate((ODocument) readDocument(channel)));
    return result;
  }

  private static ORecord readDocument(OChannelDataInput channel) throws IOException {
    ORecordSerializer serializer = ORecordSerializerNetworkV37.INSTANCE;
    final ORecord record = (ORecord) readIdentifiable(channel, serializer);
    return record;
  }

  private static OResult readProjection(OChannelDataInput channel) throws IOException {
    OResultSerializerNetwork ser = new OResultSerializerNetwork();
    return ser.fromStream(channel);
  }
}
