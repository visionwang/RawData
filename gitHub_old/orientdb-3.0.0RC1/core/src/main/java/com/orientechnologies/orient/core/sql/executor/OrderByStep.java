package com.orientechnologies.orient.core.sql.executor;

import com.orientechnologies.common.concur.OTimeoutException;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.sql.parser.OOrderBy;

import java.util.*;

/**
 * Created by luigidellaquila on 11/07/16.
 */
public class OrderByStep extends AbstractExecutionStep {
  private final OOrderBy orderBy;
  private       Integer  maxResults;

  private long cost = 0;

  List<OResult> cachedResult = null;
  int           nextElement  = 0;

  public OrderByStep(OOrderBy orderBy, OCommandContext ctx, boolean profilingEnabled) {
    this(orderBy, null, ctx, profilingEnabled);
  }

  public OrderByStep(OOrderBy orderBy, Integer maxResults, OCommandContext ctx, boolean profilingEnabled) {
    super(ctx, profilingEnabled);
    this.orderBy = orderBy;
    this.maxResults = maxResults;
    if (this.maxResults != null && this.maxResults < 0) {
      this.maxResults = null;
    }
  }

  @Override
  public OResultSet syncPull(OCommandContext ctx, int nRecords) throws OTimeoutException {
    if (cachedResult == null) {
      cachedResult = new ArrayList<>();
      prev.ifPresent(p -> init(p, ctx));
    }

    return new OResultSet() {
      int currentBatchReturned = 0;
      int offset = nextElement;

      @Override
      public boolean hasNext() {
        if (currentBatchReturned >= nRecords) {
          return false;
        }
        if (cachedResult.size() <= nextElement) {
          return false;
        }
        return true;
      }

      @Override
      public OResult next() {
        long begin = profilingEnabled ? System.nanoTime() : 0;
        try {
          if (currentBatchReturned >= nRecords) {
            throw new IllegalStateException();
          }
          if (cachedResult.size() <= nextElement) {
            throw new IllegalStateException();
          }
          OResult result = cachedResult.get(offset + currentBatchReturned);
          nextElement++;
          currentBatchReturned++;
          return result;
        } finally {
          if (profilingEnabled) {
            cost += (System.nanoTime() - begin);
          }
        }
      }

      @Override
      public void close() {
        prev.ifPresent(p -> p.close());
      }

      @Override
      public Optional<OExecutionPlan> getExecutionPlan() {
        return Optional.empty();
      }

      @Override
      public Map<String, Long> getQueryStats() {
        return new HashMap<>();
      }
    };
  }

  private void init(OExecutionStepInternal p, OCommandContext ctx) {

    boolean sorted = true;
    do {
      OResultSet lastBatch = p.syncPull(ctx, 100);
      if (!lastBatch.hasNext()) {
        break;
      }
      while (lastBatch.hasNext()) {
        if (this.timedOut) {
          break;
        }
        OResult item = lastBatch.next();
        long begin = profilingEnabled ? System.nanoTime() : 0;
        try {
          cachedResult.add(item);
          sorted = false;
          //compact, only at twice as the buffer, to avoid to do it at each add
          if (this.maxResults != null && maxResults * 2 < cachedResult.size()) {
            cachedResult.sort((a, b) -> orderBy.compare(a, b, ctx));
            cachedResult = new ArrayList<>(cachedResult.subList(0, maxResults));
            sorted = true;
          }
        } finally {
          if (profilingEnabled) {
            cost += (System.nanoTime() - begin);
          }
        }
      }
      if (timedOut) {
        break;
      }
      long begin = profilingEnabled ? System.nanoTime() : 0;
      try {
        //compact at each batch, if needed
        if (!sorted && this.maxResults != null && maxResults < cachedResult.size()) {
          cachedResult.sort((a, b) -> orderBy.compare(a, b, ctx));
          cachedResult = new ArrayList<>(cachedResult.subList(0, maxResults));
          sorted = true;
        }
      } finally {
        if (profilingEnabled) {
          cost += (System.nanoTime() - begin);
        }
      }
    } while (true);
    long begin = profilingEnabled ? System.nanoTime() : 0;
    try {
      if (!sorted) {
        cachedResult.sort((a, b) -> orderBy.compare(a, b, ctx));
      }
    } finally {
      if (profilingEnabled) {
        cost += (System.nanoTime() - begin);
      }
    }

  }

  @Override
  public String prettyPrint(int depth, int indent) {
    String result = OExecutionStepInternal.getIndent(depth, indent) + "+ " + orderBy;
    if (profilingEnabled) {
      result += " (" + getCostFormatted() + ")";
    }
    result += (maxResults != null ? "\n  (buffer size: " + maxResults + ")" : "");
    return result;
  }

  @Override
  public long getCost() {
    return cost;
  }
}
