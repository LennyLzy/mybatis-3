package org.apache.ibatis.reactive.support.executor.resultset;

import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import org.apache.ibatis.annotations.AutomapConstructor;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.executor.result.DefaultResultHandler;
import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.reactive.support.ProxyInstanceFactory;
import org.apache.ibatis.reactive.support.ReactiveConfiguration;
import org.apache.ibatis.reactive.support.exception.R2dbcResultException;
import org.apache.ibatis.reactive.support.executor.result.DelegateR2dbcResultRowDataHandler;
import org.apache.ibatis.reactive.support.executor.result.RowResultWrapper;
import org.apache.ibatis.reactive.support.executor.type.TypeHandleContext;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class R2dbcResultSetsHandler {

  private static final Object DEFERRED = new Object();

  private final ReactiveConfiguration configuration;
  private final MappedStatement mappedStatement;
  private final TypeHandlerRegistry typeHandlerRegistry;
  private final ObjectFactory objectFactory;
  private final ReflectorFactory reflectorFactory;
  private final TypeHandler delegatedTypeHandler;
  private final ResultHandler<?> resultHandler;

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

  // result context
  private List<Object> multipleResults = Collections.synchronizedList(new ArrayList<>());
  private AtomicInteger handledResultSetCount = new AtomicInteger(0);

  public R2dbcResultSetsHandler(ReactiveConfiguration configuration, MappedStatement mappedStatement, ResultHandler<?> resultHandler) {
    this.configuration = configuration;
    this.mappedStatement = mappedStatement;
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    this.reflectorFactory = configuration.getReflectorFactory();
    this.delegatedTypeHandler = this.initDelegateTypeHandler();
    this.resultHandler = resultHandler;
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


  public Mono<Void> handleResultSet(Result result) {
    int cur = handledResultSetCount.getAndIncrement();
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
    if (result != null && resultMaps.size() < cur) {
      throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
        + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
    }
    ResultMap resultMap = resultMaps.get(cur);
    if (result != null && resultMap != null) {
      return handleResult(result, resultMap, null).then();
    }
    return Mono.empty();
  }

  private Flux<?> handleResult(Result result, ResultMap resultMap, ResultMapping parentMapping) {
    if (parentMapping != null) {
      return handleRowValues(result, resultMap, null, parentMapping);
    } else {
      if (resultHandler == null) {
        DefaultResultHandler defaultResultHandler = new DefaultResultHandler(objectFactory);
        return handleRowValues(result, resultMap, defaultResultHandler, null)
          .doOnComplete(()-> multipleResults.add(defaultResultHandler.getResultList()));
//          .map(rows -> {
//            multipleResults.add(defaultResultHandler.getResultList());
//            return rows;
//          });
      } else {
        return handleRowValues(result, resultMap, resultHandler, null);
      }
    }
  }

  private Flux<?> handleRowValues(Result result, ResultMap resultMap, ResultHandler<?> resultHandler, ResultMapping parentMapping) {
    if (resultMap.hasNestedResultMaps()) {
      checkResultHandler();
      return handleRowValuesForNestedResultMap(result, resultMap, resultHandler, parentMapping);
    } else {
      return handleRowValuesForSimpleResultMap(result, resultMap, resultHandler, parentMapping);
    }
  }

  private Flux<?> handleRowValuesForSimpleResultMap(Result result, ResultMap resultMap, ResultHandler<?> resultHandler, ResultMapping parentMapping) {
    return Flux.from(result.map((row, rowMetadata) -> {
      RowResultWrapper rowResultWrapper = new RowResultWrapper(row, rowMetadata, configuration);
      return rowResultWrapper;
    })).concatMap(rowResultWrapper -> {
      try {
        return Flux.just(handleRowValue(rowResultWrapper, resultMap, resultHandler, parentMapping));
      } catch (SQLException e) {
        e.printStackTrace();
        return Flux.error(e);
      }
    });
  }

  private Object handleRowValue(RowResultWrapper rowResultWrapper, ResultMap resultMap, ResultHandler<?> resultHandler, ResultMapping parentMapping) throws SQLException {
    ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rowResultWrapper, resultMap, null);
    Object rowValue = getRowValue(rowResultWrapper, discriminatedResultMap, null);
    DefaultResultContext<Object> resultContext = new DefaultResultContext<>();
    resultContext.nextResultObject(rowValue);
    ((ResultHandler<Object>) resultHandler).handleResult(resultContext);
    return rowValue;
  }

  private Object getRowValue(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
    Object rowValue = createResultObject(rowResultWrapper, resultMap, columnPrefix);
    if (rowValue != null && !hasTypeHandlerForResultObject(resultMap.getType())) {
      final MetaObject metaObject = configuration.getConfiguration().newMetaObject(rowValue);
      boolean foundValues = this.useConstructorMappings;
      if (shouldApplyAutomaticMappings(resultMap, false)) {
        foundValues = applyAutomaticMappings(rowResultWrapper, resultMap, metaObject, columnPrefix) || foundValues;
      }
      foundValues = applyPropertyMappings(rowResultWrapper, resultMap, metaObject, columnPrefix) || foundValues;
      rowValue = foundValues || configuration.getConfiguration().isReturnInstanceForEmptyRow() ? rowValue : null;
    }
    return rowValue;
  }

  private Object createResultObject(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
    this.useConstructorMappings = false; // reset previous mapping result
    final List<Class<?>> constructorArgTypes = new ArrayList<>();
    final List<Object> constructorArgs = new ArrayList<>();
    Object resultObject = createResultObject(rowResultWrapper, resultMap, constructorArgTypes, constructorArgs, columnPrefix);
    this.useConstructorMappings = resultObject != null && !constructorArgTypes.isEmpty(); // set current mapping result
    return resultObject;
  }

  private Object createResultObject(RowResultWrapper rowResultWrapper, ResultMap resultMap, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix)
    throws SQLException {
    final Class<?> resultType = resultMap.getType();
    final MetaClass metaType = MetaClass.forClass(resultType, reflectorFactory);
    final List<ResultMapping> constructorMappings = resultMap.getConstructorResultMappings();
    if (hasTypeHandlerForResultObject(resultType)) {
      return createPrimitiveResultObject(rowResultWrapper, resultMap, columnPrefix);
    } else if (!constructorMappings.isEmpty()) {
      return createParameterizedResultObject(rowResultWrapper, resultType, constructorMappings, constructorArgTypes, constructorArgs, columnPrefix);
    } else if (resultType.isInterface() || metaType.hasDefaultConstructor()) {
      return objectFactory.create(resultType);
    } else if (shouldApplyAutomaticMappings(resultMap, false)) {
      return createByConstructorSignature(rowResultWrapper, resultType, constructorArgTypes, constructorArgs);
    }
    throw new ExecutorException("Do not know how to create an instance of " + resultType);
  }

  private Object createParameterizedResultObject(RowResultWrapper rowResultWrapper, Class<?> resultType, List<ResultMapping> constructorMappings,
                                                 List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix) {
    boolean foundValues = false;
    for (ResultMapping constructorMapping : constructorMappings) {
      final Class<?> parameterType = constructorMapping.getJavaType();
      final String column = constructorMapping.getColumn();
      final Object value;
      try {
        if (constructorMapping.getNestedQueryId() != null) {
          throw new UnsupportedOperationException("Unsupported constructor with nested query :" + constructorMapping.getNestedQueryId());
        } else if (constructorMapping.getNestedResultMapId() != null) {
          final ResultMap resultMap = configuration.getConfiguration().getResultMap(constructorMapping.getNestedResultMapId());
          value = getRowValueForSimpleResultMap(rowResultWrapper, resultMap, getColumnPrefix(columnPrefix, constructorMapping));
        } else {
          final TypeHandler<?> typeHandler = constructorMapping.getTypeHandler();
          ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
          value = this.delegatedTypeHandler.getResult(null, prependPrefix(column, columnPrefix));
        }
      } catch (ResultMapException | SQLException e) {
        throw new ExecutorException("Could not process result for mapping: " + constructorMapping, e);
      }
      constructorArgTypes.add(parameterType);
      constructorArgs.add(value);
      foundValues = value != null || foundValues;
    }
    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  private Object createByConstructorSignature(RowResultWrapper rowResultWrapper, Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) throws SQLException {
    final Constructor<?>[] constructors = resultType.getDeclaredConstructors();
    final Constructor<?> defaultConstructor = findDefaultConstructor(constructors);
    if (defaultConstructor != null) {
      return createUsingConstructor(rowResultWrapper, resultType, constructorArgTypes, constructorArgs, defaultConstructor);
    } else {
      for (Constructor<?> constructor : constructors) {
        if (allowedConstructorUsingTypeHandlers(constructor)) {
          return createUsingConstructor(rowResultWrapper, resultType, constructorArgTypes, constructorArgs, constructor);
        }
      }
    }
    throw new ExecutorException("No constructor found in " + resultType.getName() + " matching " + rowResultWrapper.getClassNames());
  }

  private Constructor<?> findDefaultConstructor(final Constructor<?>[] constructors) {
    if (constructors.length == 1) {
      return constructors[0];
    }

    for (final Constructor<?> constructor : constructors) {
      if (constructor.isAnnotationPresent(AutomapConstructor.class)) {
        return constructor;
      }
    }
    return null;
  }

  private boolean allowedConstructorUsingTypeHandlers(final Constructor<?> constructor) {
    final Class<?>[] parameterTypes = constructor.getParameterTypes();
    for (int i = 0; i < parameterTypes.length; i++) {
      if (!typeHandlerRegistry.hasTypeHandler(parameterTypes[i])) {
        return false;
      }
    }
    return true;
  }

  private Object createUsingConstructor(RowResultWrapper rowResultWrapper, Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, Constructor<?> constructor) throws SQLException {
    boolean foundValues = false;
    for (int i = 0; i < constructor.getParameterTypes().length; i++) {
      Class<?> parameterType = constructor.getParameterTypes()[i];
      String columnName = rowResultWrapper.getColumnNames().get(i);
      TypeHandler<?> typeHandler = rowResultWrapper.getTypeHandler(parameterType, columnName);
      ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
      Object value = delegatedTypeHandler.getResult(null, columnName);
      constructorArgTypes.add(parameterType);
      constructorArgs.add(value);
      foundValues = value != null || foundValues;
    }
    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
  }

  private String getColumnPrefix(String parentPrefix, ResultMapping resultMapping) {
    final StringBuilder columnPrefixBuilder = new StringBuilder();
    if (parentPrefix != null) {
      columnPrefixBuilder.append(parentPrefix);
    }
    if (resultMapping.getColumnPrefix() != null) {
      columnPrefixBuilder.append(resultMapping.getColumnPrefix());
    }
    return columnPrefixBuilder.length() == 0 ? null : columnPrefixBuilder.toString().toUpperCase(Locale.ENGLISH);
  }

  private Object getRowValueForSimpleResultMap(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
    Object rowValue = createResultObject(rowResultWrapper, resultMap, columnPrefix);
    if (rowValue != null && !hasTypeHandlerForResultObject(resultMap.getType())) {
      final MetaObject metaObject = configuration.getConfiguration().newMetaObject(rowValue);
      boolean foundValues = this.useConstructorMappings;
      if (shouldApplyAutomaticMappings(resultMap, false)) {
        foundValues = applyAutomaticMappings(rowResultWrapper, resultMap, metaObject, columnPrefix) || foundValues;
      }
      foundValues = applyPropertyMappings(rowResultWrapper, resultMap, metaObject, columnPrefix) || foundValues;
      rowValue = foundValues || configuration.getConfiguration().isReturnInstanceForEmptyRow() ? rowValue : null;
    }
    return rowValue;
  }

  private boolean shouldApplyAutomaticMappings(ResultMap resultMap, boolean isNested) {
    if (resultMap.getAutoMapping() != null) {
      return resultMap.getAutoMapping();
    } else {
      if (isNested) {
        return AutoMappingBehavior.FULL == configuration.getConfiguration().getAutoMappingBehavior();
      } else {
        return AutoMappingBehavior.NONE != configuration.getConfiguration().getAutoMappingBehavior();
      }
    }
  }

  private boolean applyAutomaticMappings(RowResultWrapper rowResultWrapper, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    List<UnMappedColumnAutoMapping> autoMapping = createAutomaticMappings(rowResultWrapper, resultMap, metaObject, columnPrefix);
    boolean foundValues = false;
    if (!autoMapping.isEmpty()) {
      for (UnMappedColumnAutoMapping mapping : autoMapping) {
        TypeHandler<?> typeHandler = mapping.typeHandler;
        ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
        final Object value = this.delegatedTypeHandler.getResult(null, mapping.column);
        if (value != null) {
          foundValues = true;
        }
        if (value != null || (configuration.getConfiguration().isCallSettersOnNulls() && !mapping.primitive)) {
          // gcode issue #377, call setter on nulls (value is not 'found')
          metaObject.setValue(mapping.property, value);
        }
      }
    }
    return foundValues;
  }

  private boolean applyPropertyMappings(RowResultWrapper rowResultWrapper, ResultMap resultMap, MetaObject metaObject, String columnPrefix)
    throws SQLException {
    final List<String> mappedColumnNames = rowResultWrapper.getMappedColumnNames(resultMap, columnPrefix);
    boolean foundValues = false;
    final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
    for (ResultMapping propertyMapping : propertyMappings) {
      String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      if (propertyMapping.getNestedResultMapId() != null) {
        // the user added a column attribute to a nested result map, ignore it
        column = null;
      }
      if (propertyMapping.isCompositeResult()
        || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH)))
        || propertyMapping.getResultSet() != null) {
        Object value = getPropertyMappingValue(rowResultWrapper, metaObject, propertyMapping, columnPrefix);
        // issue #541 make property optional
        final String property = propertyMapping.getProperty();
        if (property == null) {
          continue;
        } else if (value == DEFERRED) {
          foundValues = true;
          continue;
        }
        if (value != null) {
          foundValues = true;
        }
        if (value != null || (configuration.getConfiguration().isCallSettersOnNulls() && !metaObject.getSetterType(property).isPrimitive())) {
          // gcode issue #377, call setter on nulls (value is not 'found')
          metaObject.setValue(property, value);
        }
      }
    }
    return foundValues;
  }

  private Object getPropertyMappingValue(RowResultWrapper rowResultWrapper, MetaObject metaResultObject, ResultMapping propertyMapping, String columnPrefix)
    throws SQLException {
    if (propertyMapping.getNestedQueryId() != null) {
      throw new UnsupportedOperationException("Not supported Nested query ");
    } else {
      final TypeHandler<?> typeHandler = propertyMapping.getTypeHandler();
      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
      return this.delegatedTypeHandler.getResult(null, column);
    }
  }

  private List<UnMappedColumnAutoMapping> createAutomaticMappings(RowResultWrapper rowResultWrapper, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    final String mapKey = resultMap.getId() + ":" + columnPrefix;
    List<UnMappedColumnAutoMapping> autoMapping = autoMappingsCache.get(mapKey);
    if (autoMapping == null) {
      autoMapping = new ArrayList<>();
      final List<String> unmappedColumnNames = rowResultWrapper.getUnmappedColumnNames(resultMap, columnPrefix);
      for (String columnName : unmappedColumnNames) {
        String propertyName = columnName;
        if (columnPrefix != null && !columnPrefix.isEmpty()) {
          // When columnPrefix is specified,
          // ignore columns without the prefix.
          if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
            propertyName = columnName.substring(columnPrefix.length());
          } else {
            continue;
          }
        }
        final String property = metaObject.findProperty(propertyName, configuration.getConfiguration().isMapUnderscoreToCamelCase());
        if (property != null && metaObject.hasSetter(property)) {
          if (resultMap.getMappedProperties().contains(property)) {
            continue;
          }
          final Class<?> propertyType = metaObject.getSetterType(property);
          if (typeHandlerRegistry.hasTypeHandler(propertyType)) {
            final TypeHandler<?> typeHandler = rowResultWrapper.getTypeHandler(propertyType, columnName);
            autoMapping.add(new UnMappedColumnAutoMapping(columnName, property, typeHandler, propertyType.isPrimitive()));
          } else {
            configuration.getConfiguration().getAutoMappingUnknownColumnBehavior()
              .doAction(mappedStatement, columnName, property, propertyType);
          }
        } else {
          configuration.getConfiguration().getAutoMappingUnknownColumnBehavior()
            .doAction(mappedStatement, columnName, (property != null) ? property : propertyName, null);
        }
      }
      autoMappingsCache.put(mapKey, autoMapping);
    }
    return autoMapping;
  }

  private boolean hasTypeHandlerForResultObject(Class<?> resultType) {
    return typeHandlerRegistry.hasTypeHandler(resultType);
  }

  private Object createPrimitiveResultObject(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
    final Class<?> resultType = resultMap.getType();
    final String columnName;
    if (!resultMap.getResultMappings().isEmpty()) {
      final List<ResultMapping> resultMappingList = resultMap.getResultMappings();
      final ResultMapping mapping = resultMappingList.get(0);
      columnName = prependPrefix(mapping.getColumn(), columnPrefix);
    } else {
      columnName = rowResultWrapper.getColumnNames().get(0);
    }
    final TypeHandler<?> typeHandler = rowResultWrapper.getTypeHandler(resultType, columnName);
    ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
    return delegatedTypeHandler.getResult(null, columnName);
  }

  public ResultMap resolveDiscriminatedResultMap(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
    Set<String> pastDiscriminators = new HashSet<>();
    Discriminator discriminator = resultMap.getDiscriminator();
    while (discriminator != null) {
      final Object value = getDiscriminatorValue(rowResultWrapper, discriminator, columnPrefix);
      final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));
      if (configuration.getConfiguration().hasResultMap(discriminatedMapId)) {
        resultMap = configuration.getConfiguration().getResultMap(discriminatedMapId);
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
    ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
    return delegatedTypeHandler.getResult(null, prependPrefix(resultMapping.getColumn(), columnPrefix));
  }

  private String prependPrefix(String columnName, String prefix) {
    if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
      return columnName;
    }
    return prefix + columnName;
  }

  protected void checkResultHandler() {
    if (resultHandler != null && configuration.getConfiguration().isSafeResultHandlerEnabled() && !mappedStatement.isResultOrdered()) {
      throw new ExecutorException("Mapped Statements with nested result mappings cannot be safely used with a custom ResultHandler. "
        + "Use safeResultHandlerEnabled=false setting to bypass this check "
        + "or ensure your statement returns ordered data and set resultOrdered=true on it.");
    }
  }

  //  private Mono<Void> handleResult(Result result, int resultSetCount) {
//    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
//    if (result != null && resultMaps.size() < resultSetCount) {
//      throw new ExecutorException("A query was run and no Result Maps were found for the Mapped Statement '" + mappedStatement.getId()
//        + "'.  It's likely that neither a Result Type nor a Result Map was specified.");
//    }
//    ResultMap resultMap = resultMaps.get(resultSetCount);
//    if (resultMap != null) {
//      return Mono.from(result.map((row, rowMetadata) -> {
//        RowResultWrapper rowResultWrapper = new RowResultWrapper(row, rowMetadata, this.configuration);
//        try {
//          return handleRowValues(rowResultWrapper, resultMap);
//        } catch (SQLException e) {
//          e.printStackTrace();
//          return null;
//        }
//      }));
//    }
//    return Mono.empty();
//  }
//
//  public <T> T handleRowValues(RowResultWrapper rsw, ResultMap resultMap) throws SQLException {
//    if (resultMap.hasNestedResultMaps()) {
////      ensureNoRowBounds();
////      checkResultHandler();
////      handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
//      handleRowValuesForNestedResultMap(rsw, resultMap);
//    } else {
////      handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
//      ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw, resultMap, null);
//      Object rowValue = getRowValueForSimpleResultMap(rsw, discriminatedResultMap, null);
////      return (T) rowValue;
//    }
//    return (T) rsw;
//  }
//
//  public ResultMap resolveDiscriminatedResultMap(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
//    Set<String> pastDiscriminators = new HashSet<>();
//    Discriminator discriminator = resultMap.getDiscriminator();
//    while (discriminator != null) {
//      final Object value = getDiscriminatorValue(rowResultWrapper, discriminator, columnPrefix);
//      final String discriminatedMapId = discriminator.getMapIdFor(String.valueOf(value));
//      if (configuration.getConfiguration().hasResultMap(discriminatedMapId)) {
//        resultMap = configuration.getConfiguration().getResultMap(discriminatedMapId);
//        Discriminator lastDiscriminator = discriminator;
//        discriminator = resultMap.getDiscriminator();
//        if (discriminator == lastDiscriminator || !pastDiscriminators.add(discriminatedMapId)) {
//          break;
//        }
//      } else {
//        break;
//      }
//    }
//    return resultMap;
//  }
//
//  private Object getDiscriminatorValue(RowResultWrapper rowResultWrapper, Discriminator discriminator, String columnPrefix) throws SQLException {
//    final ResultMapping resultMapping = discriminator.getResultMapping();
//    final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
//    ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
//    return delegatedTypeHandler.getResult(null, prependPrefix(resultMapping.getColumn(), columnPrefix));
////    return typeHandler.getResult(null, prependPrefix(resultMapping.getColumn(), columnPrefix));
//  }
//
//  private Object getRowValueForSimpleResultMap(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
//    Object rowValue = createResultObject(rowResultWrapper, resultMap, columnPrefix);
//    if (rowValue != null && !hasTypeHandlerForResultObject(resultMap.getType())) {
//      final MetaObject metaObject = configuration.getConfiguration().newMetaObject(rowValue);
//      boolean foundValues = this.useConstructorMappings;
//      if (shouldApplyAutomaticMappings(resultMap, false)) {
//        foundValues = applyAutomaticMappings(rowResultWrapper, resultMap, metaObject, columnPrefix) || foundValues;
//      }
//      foundValues = applyPropertyMappings(rowResultWrapper, resultMap, metaObject, columnPrefix) || foundValues;
//      rowValue = foundValues || configuration.getConfiguration().isReturnInstanceForEmptyRow() ? rowValue : null;
//    }
//    return rowValue;
//  }
//
//  private List<Object> handleRowValuesForNestedResultMap(RowResultWrapper rowResultWrapper, ResultMap resultMap) throws SQLException {
//    final DefaultResultHandler resultHandler = new DefaultResultHandler(objectFactory);
//    final DefaultResultContext<Object> resultContext = new DefaultResultContext<>();
//    Object rowValue = previousRowValue;
//    final ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rowResultWrapper, resultMap, null);
//    final CacheKey rowKey = createRowKey(discriminatedResultMap, rowResultWrapper, null);
//    Object partialObject = nestedResultObjects.get(rowKey);
//    // issue #577 && #542
//    if (mappedStatement.isResultOrdered()) {
//      if (partialObject == null && rowValue != null) {
//        nestedResultObjects.clear();
//        storeObject(resultHandler, resultContext, rowValue, null, rowResultWrapper);
//      }
//      rowValue = getRowValueForNestedResultMap(rowResultWrapper, discriminatedResultMap, rowKey, null, partialObject);
//    } else {
//      rowValue = getRowValueForNestedResultMap(rowResultWrapper, discriminatedResultMap, rowKey, null, partialObject);
//      if (partialObject == null) {
//        storeObject(resultHandler, resultContext, rowValue, null, rowResultWrapper);
//      }
//    }
//    if (rowValue != null && mappedStatement.isResultOrdered()) {
//      storeObject(resultHandler, resultContext, rowValue, null, rowResultWrapper);
//      previousRowValue = null;
//    } else if (rowValue != null) {
//      previousRowValue = rowValue;
//    }
//    this.resultHolder.addAll(resultHandler.getResultList());
//    if (totalCount.intValue() != 0 && null == partialObject) {
//      List<Object> holdResultList = new ArrayList<>(this.resultHolder);
//      this.resultHolder.clear();
//      return holdResultList;
//    }
//    return Collections.singletonList(DEFERRED);
//  }
//
//  private Object getRowValueForNestedResultMap(RowResultWrapper rowResultWrapper, ResultMap resultMap, CacheKey combinedKey, String columnPrefix, Object partialObject) throws SQLException {
//    final String resultMapId = resultMap.getId();
//    Object rowValue = partialObject;
//    if (rowValue != null) {
//      final MetaObject metaObject = configuration.getConfiguration().newMetaObject(rowValue);
//      putAncestor(rowValue, resultMapId);
//      applyNestedResultMappings(rowResultWrapper, resultMap, metaObject, columnPrefix, combinedKey, false);
//      ancestorObjects.remove(resultMapId);
//    } else {
//      rowValue = createResultObject(rowResultWrapper, resultMap, columnPrefix);
//      if (rowValue != null && !hasTypeHandlerForResultObject(resultMap.getType())) {
//        final MetaObject metaObject = configuration.getConfiguration().newMetaObject(rowValue);
//        boolean foundValues = this.useConstructorMappings;
//        if (shouldApplyAutomaticMappings(resultMap, true)) {
//          foundValues = applyAutomaticMappings(rowResultWrapper, resultMap, metaObject, columnPrefix) || foundValues;
//        }
//        foundValues = applyPropertyMappings(rowResultWrapper, resultMap, metaObject, columnPrefix) || foundValues;
//        putAncestor(rowValue, resultMapId);
//        foundValues = applyNestedResultMappings(rowResultWrapper, resultMap, metaObject, columnPrefix, combinedKey, true) || foundValues;
//        ancestorObjects.remove(resultMapId);
//        rowValue = foundValues || configuration.getConfiguration().isReturnInstanceForEmptyRow() ? rowValue : null;
//      }
//      if (combinedKey != CacheKey.NULL_CACHE_KEY) {
//        nestedResultObjects.put(combinedKey, rowValue);
//      }
//    }
//    return rowValue;
//  }
//
//  private void storeObject(ResultHandler<?> resultHandler, DefaultResultContext<Object> resultContext, Object rowValue, ResultMapping parentMapping, RowResultWrapper rowResultWrapper) {
//    if (parentMapping != null) {
//      linkToParents(rowResultWrapper, parentMapping, rowValue);
//    } else {
//      resultContext.nextResultObject(rowValue);
//      ((ResultHandler<Object>) resultHandler).handleResult(resultContext);
//    }
//  }
//
//  private void linkToParents(RowResultWrapper rowResultWrapper, ResultMapping parentMapping, Object rowValue) {
//    CacheKey parentKey = createKeyForMultipleResults(rowResultWrapper, parentMapping, parentMapping.getColumn(), parentMapping.getForeignColumn());
//    List<R2dbcResultSetsHandler.PendingRelation> parents = pendingRelations.get(parentKey);
//    if (parents != null) {
//      for (R2dbcResultSetsHandler.PendingRelation parent : parents) {
//        if (parent != null && rowValue != null) {
//          linkObjects(parent.metaObject, parent.propertyMapping, rowValue);
//        }
//      }
//    }
//  }
//
//  private void linkObjects(MetaObject metaObject, ResultMapping resultMapping, Object rowValue) {
//    final Object collectionProperty = instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject);
//    if (collectionProperty != null) {
//      final MetaObject targetMetaObject = configuration.getConfiguration().newMetaObject(collectionProperty);
//      targetMetaObject.add(rowValue);
//    } else {
//      metaObject.setValue(resultMapping.getProperty(), rowValue);
//    }
//  }
//
//  private Object instantiateCollectionPropertyIfAppropriate(ResultMapping resultMapping, MetaObject metaObject) {
//    final String propertyName = resultMapping.getProperty();
//    Object propertyValue = metaObject.getValue(propertyName);
//    if (propertyValue == null) {
//      Class<?> type = resultMapping.getJavaType();
//      if (type == null) {
//        type = metaObject.getSetterType(propertyName);
//      }
//      try {
//        if (objectFactory.isCollection(type)) {
//          propertyValue = objectFactory.create(type);
//          metaObject.setValue(propertyName, propertyValue);
//          return propertyValue;
//        }
//      } catch (Exception e) {
//        throw new ExecutorException("Error instantiating collection property for result '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
//      }
//    } else if (objectFactory.isCollection(propertyValue.getClass())) {
//      return propertyValue;
//    }
//    return null;
//  }
//
//  private CacheKey createKeyForMultipleResults(RowResultWrapper rowResultWrapper, ResultMapping resultMapping, String names, String columns) {
//    CacheKey cacheKey = new CacheKey();
//    cacheKey.update(resultMapping);
//    if (columns != null && names != null) {
//      String[] columnsArray = columns.split(",");
//      String[] namesArray = names.split(",");
//      Row row = rowResultWrapper.getRow();
//      for (int i = 0; i < columnsArray.length; i++) {
//        Object value = row.get(columnsArray[i]);
//        if (value != null) {
//          cacheKey.update(namesArray[i]);
//          cacheKey.update(value);
//        }
//      }
//    }
//    return cacheKey;
//  }
//
//  private CacheKey createRowKey(ResultMap resultMap, RowResultWrapper rowResultWrapper, String columnPrefix) throws SQLException {
//    final CacheKey cacheKey = new CacheKey();
//    cacheKey.update(resultMap.getId());
//    List<ResultMapping> resultMappings = getResultMappingsForRowKey(resultMap);
//    if (resultMappings.isEmpty()) {
//      if (Map.class.isAssignableFrom(resultMap.getType())) {
//        createRowKeyForMap(rowResultWrapper, cacheKey);
//      } else {
//        createRowKeyForUnmappedProperties(resultMap, rowResultWrapper, cacheKey, columnPrefix);
//      }
//    } else {
//      createRowKeyForMappedProperties(resultMap, rowResultWrapper, cacheKey, resultMappings, columnPrefix);
//    }
//    if (cacheKey.getUpdateCount() < 2) {
//      return CacheKey.NULL_CACHE_KEY;
//    }
//    return cacheKey;
//  }
//
//  private List<ResultMapping> getResultMappingsForRowKey(ResultMap resultMap) {
//    List<ResultMapping> resultMappings = resultMap.getIdResultMappings();
//    if (resultMappings.isEmpty()) {
//      resultMappings = resultMap.getPropertyResultMappings();
//    }
//    return resultMappings;
//  }
//
//  private void createRowKeyForMap(RowResultWrapper rowResultWrapper, CacheKey cacheKey) {
//    List<String> columnNames = rowResultWrapper.getColumnNames();
//    for (String columnName : columnNames) {
//      final String value = rowResultWrapper.getRow().get(columnName, String.class);
//      if (value != null) {
//        cacheKey.update(columnName);
//        cacheKey.update(value);
//      }
//    }
//  }
//
//  private void createRowKeyForUnmappedProperties(ResultMap resultMap, RowResultWrapper rowResultWrapper, CacheKey cacheKey, String columnPrefix) throws SQLException {
//    final MetaClass metaType = MetaClass.forClass(resultMap.getType(), reflectorFactory);
//    List<String> unmappedColumnNames = rowResultWrapper.getUnmappedColumnNames(resultMap, columnPrefix);
//    for (String column : unmappedColumnNames) {
//      String property = column;
//      if (columnPrefix != null && !columnPrefix.isEmpty()) {
//        // When columnPrefix is specified, ignore columns without the prefix.
//        if (column.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
//          property = column.substring(columnPrefix.length());
//        } else {
//          continue;
//        }
//      }
//      if (metaType.findProperty(property, configuration.getConfiguration().isMapUnderscoreToCamelCase()) != null) {
//        String value = rowResultWrapper.getRow().get(column, String.class);
//        if (value != null) {
//          cacheKey.update(column);
//          cacheKey.update(value);
//        }
//      }
//    }
//  }
//
//  private void createRowKeyForMappedProperties(ResultMap resultMap, RowResultWrapper rowResultWrapper, CacheKey cacheKey, List<ResultMapping> resultMappings, String columnPrefix) throws SQLException {
//    for (ResultMapping resultMapping : resultMappings) {
//      if (resultMapping.isSimple()) {
//        final String column = prependPrefix(resultMapping.getColumn(), columnPrefix);
//        final TypeHandler<?> typeHandler = resultMapping.getTypeHandler();
//        List<String> mappedColumnNames = rowResultWrapper.getMappedColumnNames(resultMap, columnPrefix);
//        // Issue #114
//        if (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH))) {
//          ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
//          final Object value = this.delegatedTypeHandler.getResult(null, column);
//          if (value != null || configuration.getConfiguration().isReturnInstanceForEmptyRow()) {
//            cacheKey.update(column);
//            cacheKey.update(value);
//          }
//        }
//      }
//    }
//  }
//
//  private Object createResultObject(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
//    this.useConstructorMappings = false; // reset previous mapping result
//    final List<Class<?>> constructorArgTypes = new ArrayList<>();
//    final List<Object> constructorArgs = new ArrayList<>();
//    Object resultObject = createResultObject(rowResultWrapper, resultMap, constructorArgTypes, constructorArgs, columnPrefix);
//    this.useConstructorMappings = resultObject != null && !constructorArgTypes.isEmpty(); // set current mapping result
//    return resultObject;
//  }
//
//  private Object createResultObject(RowResultWrapper rowResultWrapper, ResultMap resultMap, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix)
//    throws SQLException {
//    final Class<?> resultType = resultMap.getType();
//    final MetaClass metaType = MetaClass.forClass(resultType, reflectorFactory);
//    final List<ResultMapping> constructorMappings = resultMap.getConstructorResultMappings();
//    if (hasTypeHandlerForResultObject(resultType)) {
//      return createPrimitiveResultObject(rowResultWrapper, resultMap, columnPrefix);
//    } else if (!constructorMappings.isEmpty()) {
//      return createParameterizedResultObject(rowResultWrapper, resultType, constructorMappings, constructorArgTypes, constructorArgs, columnPrefix);
//    } else if (resultType.isInterface() || metaType.hasDefaultConstructor()) {
//      return objectFactory.create(resultType);
//    } else if (shouldApplyAutomaticMappings(resultMap, false)) {
//      return createByConstructorSignature(rowResultWrapper, resultType, constructorArgTypes, constructorArgs);
//    }
//    throw new ExecutorException("Do not know how to create an instance of " + resultType);
//  }
//
//  private boolean hasTypeHandlerForResultObject(Class<?> resultType) {
//    return typeHandlerRegistry.hasTypeHandler(resultType);
//  }
//
//  private Object createPrimitiveResultObject(RowResultWrapper rowResultWrapper, ResultMap resultMap, String columnPrefix) throws SQLException {
//    final Class<?> resultType = resultMap.getType();
//    final String columnName;
//    if (!resultMap.getResultMappings().isEmpty()) {
//      final List<ResultMapping> resultMappingList = resultMap.getResultMappings();
//      final ResultMapping mapping = resultMappingList.get(0);
//      columnName = prependPrefix(mapping.getColumn(), columnPrefix);
//    } else {
//      columnName = rowResultWrapper.getColumnNames().get(0);
//    }
//    final TypeHandler<?> typeHandler = rowResultWrapper.getTypeHandler(resultType, columnName);
//    ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
//    return delegatedTypeHandler.getResult(null, columnName);
//  }
//
//  private Object createParameterizedResultObject(RowResultWrapper rowResultWrapper, Class<?> resultType, List<ResultMapping> constructorMappings,
//                                                 List<Class<?>> constructorArgTypes, List<Object> constructorArgs, String columnPrefix) {
//    boolean foundValues = false;
//    for (ResultMapping constructorMapping : constructorMappings) {
//      final Class<?> parameterType = constructorMapping.getJavaType();
//      final String column = constructorMapping.getColumn();
//      final Object value;
//      try {
//        if (constructorMapping.getNestedQueryId() != null) {
//          throw new UnsupportedOperationException("Unsupported constructor with nested query :" + constructorMapping.getNestedQueryId());
//        } else if (constructorMapping.getNestedResultMapId() != null) {
//          final ResultMap resultMap = configuration.getConfiguration().getResultMap(constructorMapping.getNestedResultMapId());
//          value = getRowValueForSimpleResultMap(rowResultWrapper, resultMap, getColumnPrefix(columnPrefix, constructorMapping));
//        } else {
//          final TypeHandler<?> typeHandler = constructorMapping.getTypeHandler();
//          ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
//          value = this.delegatedTypeHandler.getResult(null, prependPrefix(column, columnPrefix));
//        }
//      } catch (ResultMapException | SQLException e) {
//        throw new ExecutorException("Could not process result for mapping: " + constructorMapping, e);
//      }
//      constructorArgTypes.add(parameterType);
//      constructorArgs.add(value);
//      foundValues = value != null || foundValues;
//    }
//    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
//  }
//
//  private boolean shouldApplyAutomaticMappings(ResultMap resultMap, boolean isNested) {
//    if (resultMap.getAutoMapping() != null) {
//      return resultMap.getAutoMapping();
//    } else {
//      if (isNested) {
//        return AutoMappingBehavior.FULL == configuration.getConfiguration().getAutoMappingBehavior();
//      } else {
//        return AutoMappingBehavior.NONE != configuration.getConfiguration().getAutoMappingBehavior();
//      }
//    }
//  }
//
//  private boolean applyAutomaticMappings(RowResultWrapper rowResultWrapper, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
//    List<R2dbcResultSetsHandler.UnMappedColumnAutoMapping> autoMapping = createAutomaticMappings(rowResultWrapper, resultMap, metaObject, columnPrefix);
//    boolean foundValues = false;
//    if (!autoMapping.isEmpty()) {
//      for (R2dbcResultSetsHandler.UnMappedColumnAutoMapping mapping : autoMapping) {
//        TypeHandler<?> typeHandler = mapping.typeHandler;
//        ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
//        final Object value = this.delegatedTypeHandler.getResult(null, mapping.column);
//        if (value != null) {
//          foundValues = true;
//        }
//        if (value != null || (configuration.getConfiguration().isCallSettersOnNulls() && !mapping.primitive)) {
//          // gcode issue #377, call setter on nulls (value is not 'found')
//          metaObject.setValue(mapping.property, value);
//        }
//      }
//    }
//    return foundValues;
//  }
//
//  private List<R2dbcResultSetsHandler.UnMappedColumnAutoMapping> createAutomaticMappings(RowResultWrapper rowResultWrapper, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
//    final String mapKey = resultMap.getId() + ":" + columnPrefix;
//    List<R2dbcResultSetsHandler.UnMappedColumnAutoMapping> autoMapping = autoMappingsCache.get(mapKey);
//    if (autoMapping == null) {
//      autoMapping = new ArrayList<>();
//      final List<String> unmappedColumnNames = rowResultWrapper.getUnmappedColumnNames(resultMap, columnPrefix);
//      for (String columnName : unmappedColumnNames) {
//        String propertyName = columnName;
//        if (columnPrefix != null && !columnPrefix.isEmpty()) {
//          // When columnPrefix is specified,
//          // ignore columns without the prefix.
//          if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix)) {
//            propertyName = columnName.substring(columnPrefix.length());
//          } else {
//            continue;
//          }
//        }
//        final String property = metaObject.findProperty(propertyName, configuration.getConfiguration().isMapUnderscoreToCamelCase());
//        if (property != null && metaObject.hasSetter(property)) {
//          if (resultMap.getMappedProperties().contains(property)) {
//            continue;
//          }
//          final Class<?> propertyType = metaObject.getSetterType(property);
//          if (typeHandlerRegistry.hasTypeHandler(propertyType)) {
//            final TypeHandler<?> typeHandler = rowResultWrapper.getTypeHandler(propertyType, columnName);
//            autoMapping.add(new R2dbcResultSetsHandler.UnMappedColumnAutoMapping(columnName, property, typeHandler, propertyType.isPrimitive()));
//          } else {
//            configuration.getConfiguration().getAutoMappingUnknownColumnBehavior()
//              .doAction(mappedStatement, columnName, property, propertyType);
//          }
//        } else {
//          configuration.getConfiguration().getAutoMappingUnknownColumnBehavior()
//            .doAction(mappedStatement, columnName, (property != null) ? property : propertyName, null);
//        }
//      }
//      autoMappingsCache.put(mapKey, autoMapping);
//    }
//    return autoMapping;
//  }
//
//  private boolean applyPropertyMappings(RowResultWrapper rowResultWrapper, ResultMap resultMap, MetaObject metaObject, String columnPrefix)
//    throws SQLException {
//    final List<String> mappedColumnNames = rowResultWrapper.getMappedColumnNames(resultMap, columnPrefix);
//    boolean foundValues = false;
//    final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
//    for (ResultMapping propertyMapping : propertyMappings) {
//      String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
//      if (propertyMapping.getNestedResultMapId() != null) {
//        // the user added a column attribute to a nested result map, ignore it
//        column = null;
//      }
//      if (propertyMapping.isCompositeResult()
//        || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH)))
//        || propertyMapping.getResultSet() != null) {
//        Object value = getPropertyMappingValue(rowResultWrapper, metaObject, propertyMapping, columnPrefix);
//        // issue #541 make property optional
//        final String property = propertyMapping.getProperty();
//        if (property == null) {
//          continue;
//        } else if (value == DEFERRED) {
//          foundValues = true;
//          continue;
//        }
//        if (value != null) {
//          foundValues = true;
//        }
//        if (value != null || (configuration.getConfiguration().isCallSettersOnNulls() && !metaObject.getSetterType(property).isPrimitive())) {
//          // gcode issue #377, call setter on nulls (value is not 'found')
//          metaObject.setValue(property, value);
//        }
//      }
//    }
//    return foundValues;
//  }
//
//  private Object getPropertyMappingValue(RowResultWrapper rowResultWrapper, MetaObject metaResultObject, ResultMapping propertyMapping, String columnPrefix)
//    throws SQLException {
//    if (propertyMapping.getNestedQueryId() != null) {
//      throw new UnsupportedOperationException("Not supported Nested query ");
//    } else {
//      final TypeHandler<?> typeHandler = propertyMapping.getTypeHandler();
//      final String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
//      ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
//      return this.delegatedTypeHandler.getResult(null, column);
//    }
//  }
//
//  private boolean applyNestedResultMappings(RowResultWrapper rowResultWrapper, ResultMap resultMap, MetaObject metaObject, String parentPrefix, CacheKey parentRowKey, boolean newObject) {
//    boolean foundValues = false;
//    for (ResultMapping resultMapping : resultMap.getPropertyResultMappings()) {
//      final String nestedResultMapId = resultMapping.getNestedResultMapId();
//      if (nestedResultMapId != null && resultMapping.getResultSet() == null) {
//        try {
//          final String columnPrefix = getColumnPrefix(parentPrefix, resultMapping);
//          final ResultMap nestedResultMap = getNestedResultMap(rowResultWrapper, nestedResultMapId, columnPrefix);
//          if (resultMapping.getColumnPrefix() == null) {
//            // try to fill circular reference only when columnPrefix
//            // is not specified for the nested result map (issue #215)
//            Object ancestorObject = ancestorObjects.get(nestedResultMapId);
//            if (ancestorObject != null) {
//              if (newObject) {
//                linkObjects(metaObject, resultMapping, ancestorObject); // issue #385
//              }
//              continue;
//            }
//          }
//          final CacheKey rowKey = createRowKey(nestedResultMap, rowResultWrapper, columnPrefix);
//          final CacheKey combinedKey = combineKeys(rowKey, parentRowKey);
//          Object rowValue = nestedResultObjects.get(combinedKey);
//          boolean knownValue = rowValue != null;
//          instantiateCollectionPropertyIfAppropriate(resultMapping, metaObject); // mandatory
//          if (anyNotNullColumnHasValue(resultMapping, columnPrefix, rowResultWrapper)) {
//            rowValue = getRowValueForNestedResultMap(rowResultWrapper, nestedResultMap, combinedKey, columnPrefix, rowValue);
//            if (rowValue != null && !knownValue) {
//              linkObjects(metaObject, resultMapping, rowValue);
//              foundValues = true;
//            }
//          }
//        } catch (SQLException e) {
//          throw new ExecutorException("Error getting nested result map values for '" + resultMapping.getProperty() + "'.  Cause: " + e, e);
//        }
//      }
//    }
//    return foundValues;
//  }
//
//  private boolean anyNotNullColumnHasValue(ResultMapping resultMapping, String columnPrefix, RowResultWrapper rowResultWrapper) throws SQLException {
//    Set<String> notNullColumns = resultMapping.getNotNullColumns();
//    if (notNullColumns != null && !notNullColumns.isEmpty()) {
//      Row row = rowResultWrapper.getRow();
//      for (String column : notNullColumns) {
//        if (row.get(prependPrefix(column, columnPrefix)) != null) {
//          return true;
//        }
//      }
//      return false;
//    } else if (columnPrefix != null) {
//      for (String columnName : rowResultWrapper.getColumnNames()) {
//        if (columnName.toUpperCase(Locale.ENGLISH).startsWith(columnPrefix.toUpperCase(Locale.ENGLISH))) {
//          return true;
//        }
//      }
//      return false;
//    }
//    return true;
//  }
//
//  private ResultMap getNestedResultMap(RowResultWrapper rowResultWrapper, String nestedResultMapId, String columnPrefix) throws SQLException {
//    ResultMap nestedResultMap = configuration.getConfiguration().getResultMap(nestedResultMapId);
//    return resolveDiscriminatedResultMap(rowResultWrapper, nestedResultMap, columnPrefix);
//  }
//
//  private CacheKey combineKeys(CacheKey rowKey, CacheKey parentRowKey) {
//    if (rowKey.getUpdateCount() > 1 && parentRowKey.getUpdateCount() > 1) {
//      CacheKey combinedKey;
//      try {
//        combinedKey = rowKey.clone();
//      } catch (CloneNotSupportedException e) {
//        throw new ExecutorException("Error cloning cache key.  Cause: " + e, e);
//      }
//      combinedKey.update(parentRowKey);
//      return combinedKey;
//    }
//    return CacheKey.NULL_CACHE_KEY;
//  }
//
//  private Object createByConstructorSignature(RowResultWrapper rowResultWrapper, Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) throws SQLException {
//    final Constructor<?>[] constructors = resultType.getDeclaredConstructors();
//    final Constructor<?> defaultConstructor = findDefaultConstructor(constructors);
//    if (defaultConstructor != null) {
//      return createUsingConstructor(rowResultWrapper, resultType, constructorArgTypes, constructorArgs, defaultConstructor);
//    } else {
//      for (Constructor<?> constructor : constructors) {
//        if (allowedConstructorUsingTypeHandlers(constructor)) {
//          return createUsingConstructor(rowResultWrapper, resultType, constructorArgTypes, constructorArgs, constructor);
//        }
//      }
//    }
//    throw new ExecutorException("No constructor found in " + resultType.getName() + " matching " + rowResultWrapper.getClassNames());
//  }
//
//  private boolean allowedConstructorUsingTypeHandlers(final Constructor<?> constructor) {
//    final Class<?>[] parameterTypes = constructor.getParameterTypes();
//    for (int i = 0; i < parameterTypes.length; i++) {
//      if (!typeHandlerRegistry.hasTypeHandler(parameterTypes[i])) {
//        return false;
//      }
//    }
//    return true;
//  }
//
//  private Constructor<?> findDefaultConstructor(final Constructor<?>[] constructors) {
//    if (constructors.length == 1) {
//      return constructors[0];
//    }
//
//    for (final Constructor<?> constructor : constructors) {
//      if (constructor.isAnnotationPresent(AutomapConstructor.class)) {
//        return constructor;
//      }
//    }
//    return null;
//  }
//
//  private Object createUsingConstructor(RowResultWrapper rowResultWrapper, Class<?> resultType, List<Class<?>> constructorArgTypes, List<Object> constructorArgs, Constructor<?> constructor) throws SQLException {
//    boolean foundValues = false;
//    for (int i = 0; i < constructor.getParameterTypes().length; i++) {
//      Class<?> parameterType = constructor.getParameterTypes()[i];
//      String columnName = rowResultWrapper.getColumnNames().get(i);
//      TypeHandler<?> typeHandler = rowResultWrapper.getTypeHandler(parameterType, columnName);
//      ((TypeHandleContext) this.delegatedTypeHandler).contextWith(typeHandler, rowResultWrapper);
//      Object value = delegatedTypeHandler.getResult(null, columnName);
//      constructorArgTypes.add(parameterType);
//      constructorArgs.add(value);
//      foundValues = value != null || foundValues;
//    }
//    return foundValues ? objectFactory.create(resultType, constructorArgTypes, constructorArgs) : null;
//  }
//
//  private String prependPrefix(String columnName, String prefix) {
//    if (columnName == null || columnName.length() == 0 || prefix == null || prefix.length() == 0) {
//      return columnName;
//    }
//    return prefix + columnName;
//  }
//
//  private String getColumnPrefix(String parentPrefix, ResultMapping resultMapping) {
//    final StringBuilder columnPrefixBuilder = new StringBuilder();
//    if (parentPrefix != null) {
//      columnPrefixBuilder.append(parentPrefix);
//    }
//    if (resultMapping.getColumnPrefix() != null) {
//      columnPrefixBuilder.append(resultMapping.getColumnPrefix());
//    }
//    return columnPrefixBuilder.length() == 0 ? null : columnPrefixBuilder.toString().toUpperCase(Locale.ENGLISH);
//  }
//
//  private void putAncestor(Object resultObject, String resultMapId) {
//    ancestorObjects.put(resultMapId, resultObject);
//  }
//
  private TypeHandler initDelegateTypeHandler() {
    return ProxyInstanceFactory.newInstanceOfInterfaces(
      TypeHandler.class,
      () -> new DelegateR2dbcResultRowDataHandler(
//        this.r2DbcMybatisConfiguration.getNotSupportedDataTypes(),
//        this.r2DbcMybatisConfiguration.getR2dbcTypeHandlerAdapterRegistry().getR2dbcTypeHandlerAdapters()
      ),
      TypeHandleContext.class
    );
  }
}
