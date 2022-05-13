package org.apache.ibatis.reactive.support.executor.resultset;

import io.r2dbc.spi.Result;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.reactive.support.executor.result.RowResultWrapper;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class R2dbcResultSetsHandler {

  private final LongAdder totalCount = new LongAdder();
  private final AtomicInteger handledResultSetCount = new AtomicInteger();

  private final ReactiveConfiguration configuration;
  private final MappedStatement mappedStatement;
  //  private final RowBounds rowBounds;
//  private final ParameterHandler parameterHandler;
//  private final ResultHandler<?> resultHandler;
//  private final BoundSql boundSql;
  private final TypeHandlerRegistry typeHandlerRegistry;
  private final ObjectFactory objectFactory;
  private final ReflectorFactory reflectorFactory;

  // nested resultmaps
  private final Map<CacheKey, Object> nestedResultObjects = new HashMap<>();
  private final Map<String, Object> ancestorObjects = new HashMap<>();
  private Object previousRowValue;

  // multiple resultsets
  private final Map<String, ResultMapping> nextResultMaps = new HashMap<>();
  private final Map<CacheKey, List<PendingRelation>> pendingRelations = new HashMap<>();

  // Cached Automappings
  private final Map<String, List<UnMappedColumnAutoMapping>> autoMappingsCache = new HashMap<>();

  // temporary marking flag that indicate using constructor mapping (use field to reduce memory usage)
  private boolean useConstructorMappings;

  public R2dbcResultSetsHandler(ReactiveConfiguration configuration, MappedStatement mappedStatement) {
    this.configuration = configuration;
    this.mappedStatement = mappedStatement;
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    this.reflectorFactory = configuration.getReflectorFactory();
  }

  private static class PendingRelation {
    public MetaObject metaObject;
    public ResultMapping propertyMapping;
  }

  private static class UnMappedColumnAutoMapping {
    private final String column;
    private final String property;
    private final TypeHandler<?> typeHandler;
    private final boolean primitive;

    public UnMappedColumnAutoMapping(String column, String property, TypeHandler<?> typeHandler, boolean primitive) {
      this.column = column;
      this.property = property;
      this.typeHandler = typeHandler;
      this.primitive = primitive;
    }
  }

  public <T> Flux<T> handleResultSet(Result result) {
    return handleResult(result, handledResultSetCount.getAndIncrement());
  }

  private <T> Flux<T> handleResult(Result result, int andIncrement) {
    result.map((row, rowMetadata) -> {
        RowResultWrapper rowResultWrapper = new RowResultWrapper(row, rowMetadata, this.configuration);
//            return (List<T>) reactiveResultHandler.handleResult(rowResultWrapper);
        return rowResultWrapper;
      });
  }


//  public <T> List<T> handleResult(RowResultWrapper rowResultWrapper, int resultIndex) {
//    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
//    int resultMapCount = resultMaps.size();
//    validateResultMapsCount(rowResultWrapper, resultMapCount);
//    ResultMap resultMap = resultMaps.get(resultIndex);
//
//  }

  private void validateResultMapsCount(RowResultWrapper rsw, int resultMapCount) {
    if (rsw != null && resultMapCount < 1) {
      throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
        + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
    }
  }
}
