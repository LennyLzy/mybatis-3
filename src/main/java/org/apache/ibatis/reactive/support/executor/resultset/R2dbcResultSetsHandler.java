package org.apache.ibatis.reactive.support.executor.resultset;

import io.r2dbc.spi.Result;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.reactive.support.exception.R2dbcResultException;
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

import java.sql.SQLException;
import java.util.*;
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

  public Integer getResultRowTotalCount() {
    return totalCount.intValue();
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

  private <T> Flux<T> handleResult(Result result, int resultSetCount) {
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
    if (result != null && resultMaps.size() < resultSetCount) {
      throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
        + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
    }
    ResultMap resultMap = resultMaps.get(resultSetCount);
    if (resultMap != null) {
      return Flux.from(result.map((row, rowMetadata) -> {
        RowResultWrapper rowResultWrapper = new RowResultWrapper(row, rowMetadata, this.configuration);
        return handleRowValues(rowResultWrapper, resultMap);
//        return (T)rowResultWrapper;
      }));
    }
    return Flux.empty();
  }

  public <T> T handleRowValues(RowResultWrapper rsw, ResultMap resultMap) {
    if (resultMap.hasNestedResultMaps()) {
//      ensureNoRowBounds();
//      checkResultHandler();
//      handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    } else {
//      handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
      ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw, resultMap, null);
      Object rowValue = getRowValueForSimpleResultMap(rowResultWrapper, discriminatedResultMap, null);
    }
//    if (!resultMap.hasNestedResultMaps()) {
//      try {
//        ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw, resultMap, null);
//        Object rowValue = getRowValueForSimpleResultMap(rowResultWrapper, discriminatedResultMap, null);
//        totalCount.increment();
//        return Collections.singletonList((T) rowValue);
//      } catch (SQLException e) {
//        throw new R2dbcResultException(e);
//      }
//    }
  }

  public ResultMap resolveDiscriminatedResultMap(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
    Set<String> pastDiscriminators = new HashSet<>();
    Discriminator discriminator = resultMap.getDiscriminator();
    while (discriminator != null) {
      final Object value = getDiscriminatorValue(rowResultWrapper, discriminator, columnPrefix);
      final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));
      if (configuration.hasResultMap(discriminatedMapId)) {
        resultMap = configuration.getResultMap(discriminatedMapId);
        Discriminator lastDiscriminator = discriminator;
        discriminator = resultMap.getDiscriminator();
        if (discriminator == lastDiscriminator || !pastDiscriminators.add(discriminatedMapId)) {
          break;
        }
      } else {
        break;
      }
    }
    return resultMap;
  }

  private Object getDiscriminatorValue(RowResultWrapper rowResultWrapper, Discriminator discriminator, String columnPrefix) throws SQLException {
    final ResultMapping resultMapping = discriminator.getResultMapping();
    final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
//    ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
//    return delegatedTypeHandler.getResult(null, prependPrefix(resultMapping.getColumn(), columnPrefix));
    return typeHandler.getResult(null, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  private String prependPrefix(String columnName, String prefix) {
    if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
      return columnName;
    }
    return prefix + columnName;
  }
}
