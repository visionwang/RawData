package com.orientechnologies.orient.core.sql.executor;

import com.orientechnologies.common.concur.OTimeoutException;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.sql.parser.OTraverseProjectionItem;
import com.orientechnologies.orient.core.sql.parser.OWhereClause;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luigidellaquila on 26/10/16.
 */
public abstract class AbstractTraverseStep extends AbstractExecutionStep {
  protected final OWhereClause                  whileClause;
  protected final List<OTraverseProjectionItem> projections;

  protected List<OResult> entryPoints = null;
  protected List<OResult> results     = new ArrayList<>();
  private   long          cost        = 0;

  Set<ORID> traversed = new ORidSet();

  public AbstractTraverseStep(List<OTraverseProjectionItem> projections, OWhereClause whileClause, OCommandContext ctx, boolean profilingEnabled) {
    super(ctx, profilingEnabled);
    this.whileClause = whileClause;
    this.projections = projections.stream().map(x -> x.copy()).collect(Collectors.toList());
  }

  @Override
  public OResultSet syncPull(OCommandContext ctx, int nRecords) throws OTimeoutException {
    //TODO

    return new OResultSet() {
      int localFetched = 0;

      @Override
      public boolean hasNext() {
        if (localFetched >= nRecords) {
          return false;
        }
        if (results.isEmpty()) {
          fetchNextBlock(ctx, nRecords);
        }
        if (results.isEmpty()) {
          return false;
        }
        return true;
      }

      @Override
      public OResult next() {
        if (localFetched >= nRecords) {
          throw new IllegalStateException();
        }
        if (results.isEmpty()) {
          fetchNextBlock(ctx, nRecords);
          if (results.isEmpty()) {
            throw new IllegalStateException();
          }
        }
        localFetched++;
        OResult result = results.remove(0);
        if (result.isElement()) {
          traversed.add(result.getElement().get().getIdentity());
        }
        return result;
      }

      @Override
      public void close() {

      }

      @Override
      public Optional<OExecutionPlan> getExecutionPlan() {
        return null;
      }

      @Override
      public Map<String, Long> getQueryStats() {
        return null;
      }
    };
  }

  private void fetchNextBlock(OCommandContext ctx, int nRecords) {
    if (this.entryPoints == null) {
      this.entryPoints = new ArrayList<OResult>();
    }
    if (!this.results.isEmpty()) {
      return;
    }
    while (this.results.isEmpty()) {
      if (this.entryPoints.isEmpty()) {
        fetchNextEntryPoints(ctx, nRecords);
      }
      if (this.entryPoints.isEmpty()) {
        return;
      }
      long begin = profilingEnabled ? System.nanoTime() : 0;
      fetchNextResults(ctx, nRecords);
      if (profilingEnabled) {
        cost += (System.nanoTime() - begin);
      }
      if (!this.results.isEmpty()) {
        return;
      }
    }
  }

  protected abstract void fetchNextEntryPoints(OCommandContext ctx, int nRecords);

  protected abstract void fetchNextResults(OCommandContext ctx, int nRecords);

  protected boolean isFinished() {
    return entryPoints != null && entryPoints.isEmpty() && results.isEmpty();
  }

  @Override
  public long getCost() {
    return cost;
  }
}
