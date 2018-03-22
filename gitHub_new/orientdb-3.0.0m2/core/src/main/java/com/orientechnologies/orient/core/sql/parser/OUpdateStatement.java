/* Generated By:JJTree: Do not edit this line. OUpdateStatement.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OBasicCommandContext;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.ODatabase;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import com.orientechnologies.orient.core.sql.executor.OUpdateExecutionPlan;
import com.orientechnologies.orient.core.sql.executor.OUpdateExecutionPlanner;
import com.orientechnologies.orient.core.storage.OStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OUpdateStatement extends OStatement {
  public OFromClause target;

  protected List<OUpdateOperations> operations = new ArrayList<OUpdateOperations>();

  protected boolean upsert = false;

  protected boolean returnBefore = false;
  protected boolean returnAfter  = false;
  protected boolean returnCount  = false;
  protected OProjection returnProjection;

  public OWhereClause whereClause;

  public OStorage.LOCKING_STRATEGY lockRecord = null;

  public OLimit   limit;
  public OTimeout timeout;

  public OUpdateStatement(int id) {
    super(id);
  }

  public OUpdateStatement(OrientSql p, int id) {
    super(p, id);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    builder.append(getStatementType());
    if (target != null) {
      target.toString(params, builder);
    }

    for (OUpdateOperations ops : this.operations) {
      builder.append(" ");
      ops.toString(params, builder);
    }

    if (upsert) {
      builder.append(" UPSERT");
    }

    if (returnBefore || returnAfter || returnCount) {
      builder.append(" RETURN");
      if (returnBefore) {
        builder.append(" BEFORE");
      } else if (returnAfter) {
        builder.append(" AFTER");
      } else {
        builder.append(" COUNT");
      }
      if (returnProjection != null) {
        builder.append(" ");
        returnProjection.toString(params, builder);
      }
    }
    if (whereClause != null) {
      builder.append(" WHERE ");
      whereClause.toString(params, builder);
    }

    if (lockRecord != null) {
      builder.append(" LOCK ");
      switch (lockRecord) {
      case DEFAULT:
        builder.append("DEFAULT");
        break;
      case EXCLUSIVE_LOCK:
        builder.append("RECORD");
        break;
      case SHARED_LOCK:
        builder.append("SHARED");
        break;
      case NONE:
        builder.append("NONE");
        break;
      }
    }
    if (limit != null) {
      limit.toString(params, builder);
    }
    if (timeout != null) {
      timeout.toString(params, builder);
    }
  }

  protected String getStatementType() {
    return "UPDATE ";
  }

  @Override public OUpdateStatement copy() {
    OUpdateStatement result = null;
    try {
      result = getClass().getConstructor(Integer.TYPE).newInstance(-1);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    result.target = target == null ? null : target.copy();
    result.operations = operations == null ? null : operations.stream().map(x -> x.copy()).collect(Collectors.toList());
    result.upsert = upsert;
    result.returnBefore = returnBefore;
    result.returnAfter = returnAfter;
    result.returnProjection = returnProjection == null ? null : returnProjection.copy();
    result.whereClause = whereClause == null ? null : whereClause.copy();
    result.lockRecord = lockRecord;
    result.limit = limit == null ? null : limit.copy();
    result.timeout = timeout == null ? null : timeout.copy();
    return result;
  }

  @Override public OResultSet execute(ODatabase db, Object[] args, OCommandContext parentCtx) {
    OBasicCommandContext ctx = new OBasicCommandContext();
    if (parentCtx != null) {
      ctx.setParentWithoutOverridingChild(parentCtx);
    }
    ctx.setDatabase(db);
    Map<Object, Object> params = new HashMap<>();
    if (args != null) {
      for (int i = 0; i < args.length; i++) {
        params.put(i, args[i]);
      }
    }
    ctx.setInputParameters(params);
    OUpdateExecutionPlan executionPlan = createExecutionPlan(ctx, false);
    executionPlan.executeInternal();
    return new OLocalResultSet(executionPlan);
  }

  @Override public OResultSet execute(ODatabase db, Map params, OCommandContext parentCtx) {
    OBasicCommandContext ctx = new OBasicCommandContext();
    if (parentCtx != null) {
      ctx.setParentWithoutOverridingChild(parentCtx);
    }
    ctx.setDatabase(db);
    ctx.setInputParameters(params);
    OUpdateExecutionPlan executionPlan = createExecutionPlan(ctx, false);
    executionPlan.executeInternal();
    return new OLocalResultSet(executionPlan);
  }

  public OUpdateExecutionPlan createExecutionPlan(OCommandContext ctx, boolean enableProfiling) {
    OUpdateExecutionPlanner planner = new OUpdateExecutionPlanner(this);
    return planner.createExecutionPlan(ctx, enableProfiling);
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OUpdateStatement that = (OUpdateStatement) o;

    if (upsert != that.upsert)
      return false;
    if (returnBefore != that.returnBefore)
      return false;
    if (returnAfter != that.returnAfter)
      return false;
    if (target != null ? !target.equals(that.target) : that.target != null)
      return false;
    if (operations != null ? !operations.equals(that.operations) : that.operations != null)
      return false;
    if (returnProjection != null ? !returnProjection.equals(that.returnProjection) : that.returnProjection != null)
      return false;
    if (whereClause != null ? !whereClause.equals(that.whereClause) : that.whereClause != null)
      return false;
    if (lockRecord != that.lockRecord)
      return false;
    if (limit != null ? !limit.equals(that.limit) : that.limit != null)
      return false;
    if (timeout != null ? !timeout.equals(that.timeout) : that.timeout != null)
      return false;

    return true;
  }

  @Override public int hashCode() {
    int result = target != null ? target.hashCode() : 0;
    result = 31 * result + (operations != null ? operations.hashCode() : 0);
    result = 31 * result + (upsert ? 1 : 0);
    result = 31 * result + (returnBefore ? 1 : 0);
    result = 31 * result + (returnAfter ? 1 : 0);
    result = 31 * result + (returnProjection != null ? returnProjection.hashCode() : 0);
    result = 31 * result + (whereClause != null ? whereClause.hashCode() : 0);
    result = 31 * result + (lockRecord != null ? lockRecord.hashCode() : 0);
    result = 31 * result + (limit != null ? limit.hashCode() : 0);
    result = 31 * result + (timeout != null ? timeout.hashCode() : 0);
    return result;
  }

  public OFromClause getTarget() {
    return target;
  }

  public List<OUpdateOperations> getOperations() {
    return operations;
  }

  public boolean isUpsert() {
    return upsert;
  }

  public boolean isReturnBefore() {
    return returnBefore;
  }

  public boolean isReturnAfter() {
    return returnAfter;
  }

  public boolean isReturnCount() {
    return returnCount;
  }

  public OProjection getReturnProjection() {
    return returnProjection;
  }

  public OWhereClause getWhereClause() {
    return whereClause;
  }

  public OStorage.LOCKING_STRATEGY getLockRecord() {
    return lockRecord;
  }

  public OLimit getLimit() {
    return limit;
  }

  public OTimeout getTimeout() {
    return timeout;
  }
}
/* JavaCC - OriginalChecksum=093091d7273f1073ad49f2a2bf709a53 (do not edit this line) */