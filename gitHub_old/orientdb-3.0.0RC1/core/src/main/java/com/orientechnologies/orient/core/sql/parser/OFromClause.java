/* Generated By:JJTree: Do not edit this line. OFromClause.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultInternal;

import java.util.Map;

public class OFromClause extends SimpleNode {

  OFromItem item;

  public OFromClause(int id) {
    super(id);
  }

  public OFromClause(OrientSql p, int id) {
    super(p, id);
  }

  /** Accept the visitor. **/
  public Object jjtAccept(OrientSqlVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  public void toString(Map<Object, Object> params, StringBuilder builder) {
    if (item != null) {
      item.toString(params, builder);
    }
  }


  public OFromItem getItem() {
    return item;
  }

  public void setItem(OFromItem item) {
    this.item = item;
  }

  public OFromClause copy() {
    OFromClause result= new OFromClause(-1);
    result.item = item.copy();
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    OFromClause that = (OFromClause) o;

    if (item != null ? !item.equals(that.item) : that.item != null)
      return false;

    return true;
  }

  @Override public int hashCode() {
    return item != null ? item.hashCode() : 0;
  }

  public OResult serialize() {
    OResultInternal result = new OResultInternal();
    result.setProperty("item", item.serialize());
    return result;
  }

  public void deserialize(OResult fromResult) {
    item = new OFromItem(-1);
    item.deserialize(fromResult.getProperty("item"));
  }
}
/* JavaCC - OriginalChecksum=051839d20dabfa4cce26ebcbe0d03a86 (do not edit this line) */