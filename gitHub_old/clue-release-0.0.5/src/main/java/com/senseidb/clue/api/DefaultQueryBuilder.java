package com.senseidb.clue.api;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

public class DefaultQueryBuilder implements QueryBuilder {

  private QueryParser parser = null;
  
  @Override
  public void initialize(String defaultField, Analyzer analyzer)
      throws Exception {
    parser = new QueryParser(Version.LUCENE_47, defaultField, analyzer);
    
  }

  @Override
  public Query build(String rawQuery) throws Exception {
    return parser.parse(rawQuery);
  }

}
