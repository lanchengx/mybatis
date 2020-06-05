/**
 * Copyright 2009-2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.mapping.CacheBuilder;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * /**
 * * 构建映射器助理
 * * <p>
 * *     参考博客：<a href='https://www.jb51.net/article/85669.htm'>https://www.jb51.net/article/85669.htm</a>
 * * </p>
 * * @author Clinton Begin
 */
public class MapperBuilderAssistant extends BaseBuilder {

  /**
   * 当前命名空间
   */
  private String currentNamespace;

  /**
   * 类的java文件相等路径。如：'bin/study/mapper/IUser.java (best guess)'
   * <p>
   * 资源路径，当前项目，有可能是java文件也有可能是.xml文件
   */
  private final String resource;
  /**
   * 当前Cache实例，在构建MapperStatement对象时，会将currentCache对象传进去
   */
  private Cache currentCache;
  /**
   * 未成功解析CacheRef标记
   */
  private boolean unresolvedCacheRef; // issue #676

  /**
   * @param configuration mybatis全局配置信息
   * @param resource      资源路径，当前项目，有可能是java文件也有可能是.xml文件
   */
  public MapperBuilderAssistant(Configuration configuration, String resource) {
    super(configuration);
    ErrorContext.instance().resource(resource);
    this.resource = resource;
  }

  /**
   * 获取当前命名空间
   */
  public String getCurrentNamespace() {
    return currentNamespace;
  }

  /**
   * 设置当前命名空间
   * <p>
   * {@link @currentNamespace}如果为null，
   * </p>
   *
   * @param currentNamespace 如果为null，或者 当前{@link #currentNamespace}已经有值而且传进来的{@link @currentNamespace}
   *                         和{@link #currentNamespace}不一样都会导致抛出{@link BuilderException}
   */
  public void setCurrentNamespace(String currentNamespace) {
    if (currentNamespace == null) {
      throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
    }

    //后面的这个等于看起来有点对于，我推测它存在的意义是为了抛出异常的时候能够更加清晰的描述给用户。
    if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
      throw new BuilderException("Wrong namespace. Expected '"
        + this.currentNamespace + "' but found '" + currentNamespace + "'.");
    }

    this.currentNamespace = currentNamespace;
  }

  /**
   * 检查ID是否简写，简写就应用当前命名空间；
   * <p>检查便签ID{@link @base}的语法,并返回合法的标签ID.</p>
   * <p>
   * 如果{@link @base}是引用别的命名空间声明的，就判断base是否已经声明了命名空间，声明了就直接返回base，没有声明的默认给base的开头加上当前的命名空间
   * 后再返回
   * </p>
   * <p>
   * 如果{@link @base}是引用当前的命名空间声明的，就判断base的开头是否是当前命名空间，是就直接返回，不是就再判断是否引用了其他的命名空间，是就抛出异常，
   * 不是，就认为base只是省略了当前的命名空间，然后mybatis默认给base的开头加上当前的命名空间后再返回
   * </p>
   *
   * @param base        标签ID
   * @param isReference 是否可能是引用别的命名空间声明，true为是，false为否
   * @return
   */
  public String applyCurrentNamespace(String base, boolean isReference) {
    if (base == null) {
      return null;
    }
    if (isReference) {
      // is it qualified with any namespace yet?  与任何名称空间是合格的吗?
      //因为已经加上了命名空间，所以只需要检查是否带有'.'，即base的语法检查。这里不会检查到base是否存在配置项中，因为base有可能是其他mapper的声明的。
      if (base.contains(".")) {
        return base;
      }
    } else {
      // is it qualified with this namespace yet?
      //因为引用的当前命名空间，所以要检查是否是以当前命名空间开头
      if (base.startsWith(currentNamespace + ".")) {
        return base;
      }
      //如果是引用当前命名空间，但是又没有引用了其他的命名空间，这是不允许的。
      if (base.contains(".")) {
        throw new BuilderException("Dots are not allowed in element names, please remove it from " + base);
      }
    }
    //到这一步，就认识是：引用的是当前命名空间，但是又省略加上当前命名空间作为开头的base。
    //这种情况就要mybatis会自动加上命名空间+'.'
    return currentNamespace + "." + base;
  }

  /**
   * CacheRef对应的Cache对象，会从Mybatis全局配信息中获取 {@code namespace} 对应的Cache对象，
   * 如果没有找到，会抛出IncompleteElementException异常，找到会将Cache对象赋值给
   * currentCache，在构建MapperStatement对象时，将currentCache传进去
   *
   * @param namespace 如果为null，抛出{@link BuilderException}
   * @return
   */
  public Cache useCacheRef(String namespace) {
    if (namespace == null) {
      throw new BuilderException("cache-ref element requires a namespace attribute.");
    }
    try {
      //未解析缓存引用标记，在成功获取到CacheRef对应的缓存后会设置成false
      unresolvedCacheRef = true;
      //获取缓存
      Cache cache = configuration.getCache(namespace);
      if (cache == null) {
        throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.");
      }
      //设置成当前缓存
      currentCache = cache;
      unresolvedCacheRef = false;
      return cache;
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.", e);
    }
  }

  /**
   * 构建一个缓存实例
   * <p>
   * 通过{@link CacheBuilder}构建出Cache实例对象,并将Cache实例对象添加到{@link #configuration},并赋值到
   * {@link #currentCache}
   * </p>
   *
   * @param typeClass     cache使用的类型，默认是PerpetualCache，这在一级缓存中提到过。
   * @param evictionClass 定义回收的策略，常见的有FIFO，LRU
   * @param flushInterval 配置一定时间自动刷新缓存，单位是毫秒
   * @param size          最多缓存对象的个数
   * @param readWrite     是否只读，若配置可读写，则需要对应的实体类能够序列化。
   * @param blocking      若缓存中找不到对应的key，是否会一直blocking，直到有对应的数据进入缓存。
   * @param props         自定义的属性，供给自定义的Cache实现类
   */
  public Cache useNewCache(Class<? extends Cache> typeClass,
                           Class<? extends Cache> evictionClass,
                           Long flushInterval,
                           Integer size,
                           boolean readWrite,
                           boolean blocking,
                           Properties props) {
    //构建Cache实例对象，构建出来的Cache实例是经过一层又一层装饰的装饰类
    Cache cache = new CacheBuilder(currentNamespace)
      .implementation(valueOrDefault(typeClass, PerpetualCache.class))
      .addDecorator(valueOrDefault(evictionClass, LruCache.class))
      .clearInterval(flushInterval)
      .size(size)
      .readWrite(readWrite)
      .blocking(blocking)
      .properties(props)
      .build();
    //添加Cache实例
    configuration.addCache(cache);
    currentCache = cache;
    return cache;
  }

  /**
   * 构建ParameterMap,并添加到{@link #configuration}中
   *
   * @param id                ParameterMap标签的ID属性
   * @param parameterClass    ParamterMap标签的type属性，即参数类型
   * @param parameterMappings ParamterMap标签的所有paramter标签，即参数的所有属性列表
   */
  public ParameterMap addParameterMap(String id, Class<?> parameterClass, List<ParameterMapping> parameterMappings) {
    //将id带上currentNamespace前缀，如：id='user',currentNamespace='bin.study.mapper' ==> id='bin.study.mapper.user'
    id = applyCurrentNamespace(id, false);
    // 构建ParameterMap
    ParameterMap parameterMap = new ParameterMap.Builder(configuration, id, parameterClass, parameterMappings).build();
    //// configuration添加parameterMap
    configuration.addParameterMap(parameterMap);
    return parameterMap;
  }

  /**
   * 构造ParameterMapper
   *
   * @param parameterType 参数java类型
   * @param property      参数的属性名
   * @param javaType      参数的属性类型
   * @param jdbcType      参数的属性对应的jdbcType
   * @param resultMap     参数的属性对应的结果映射
   * @param parameterMode 参数的属性，当使用存储过程时，需要设置一个参数 mode，其值有 IN（输入参数）、OUT（输出参数）、INOUT（输入 / 输出参数）。
   * @param typeHandler   参数的属性对应的TypeHandler
   * @param numericScale  参数的属性的小数点保留的位数
   * @return
   */
  public ParameterMapping buildParameterMapping(
    Class<?> parameterType,
    String property,
    Class<?> javaType,
    JdbcType jdbcType,
    String resultMap,
    ParameterMode parameterMode,
    Class<? extends TypeHandler<?>> typeHandler,
    Integer numericScale) {
    // resultMap如果已经有命名空间作为前缀，经过下面代码将不会出现改变，但如果没有命名空间，就会加上当前命名空间作为前缀
    resultMap = applyCurrentNamespace(resultMap, true);

    // Class parameterType = parameterMapBuilder.type();
    //解析获取参数或者结果的属性的java类型
    Class<?> javaTypeClass = resolveParameterJavaType(parameterType, property, javaType, jdbcType);
    // 获取接收javaTypeClass的TypeHandler实例
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);

    return new ParameterMapping.Builder(configuration, property, javaTypeClass)
      .jdbcType(jdbcType)
      .resultMapId(resultMap)
      .mode(parameterMode)
      .numericScale(numericScale)
      .typeHandler(typeHandlerInstance)
      .build();
  }

  /**
   * 构建{@link ResultMap}实例，并添加到{@link #configuration}
   *
   * @param id             ResultMap标签的ID
   * @param type           ResultMap对应的java类型
   * @param extend         继承的ResultMap，这里是继承的ResultMap的Id
   * @param discriminator  鉴别器
   * @param resultMappings ResultMap的子标签结果映射
   * @param autoMapping    是否自动映射
   */
  public ResultMap addResultMap(
    String id,
    Class<?> type,
    String extend,
    Discriminator discriminator,
    List<ResultMapping> resultMappings,
    Boolean autoMapping) {
    //验证并构建正确的Id命名
    id = applyCurrentNamespace(id, false);
    //验证并构建正确的Id命名
    extend = applyCurrentNamespace(extend, true);
    if (extend != null) {
      //extend的ReusltMap必须必当前ResultMap先注册。
      if (!configuration.hasResultMap(extend)) {
        throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
      }
      ResultMap resultMap = configuration.getResultMap(extend);
      /**
       * 复制多一个extendReusltMap的ResultMapping,因为出于保护原数据的考虑，
       * resultMap的中的ResultMapping是不能更改的。
       */
      List<ResultMapping> extendedResultMappings = new ArrayList<>(resultMap.getResultMappings());
      //删除被重写了的ResultMapping
      extendedResultMappings.removeAll(resultMappings);
      // Remove parent constructor if this resultMap declares a constructor.
      //删除父类构造方法如果子类声明了构造方法
      boolean declaresConstructor = false;
      for (ResultMapping resultMapping : resultMappings) {
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          declaresConstructor = true;
          break;
        }
      }
      if (declaresConstructor) {
        extendedResultMappings.removeIf(resultMapping -> resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR));
      }
      //将继承的resultMapping放进reusltMappings中
      resultMappings.addAll(extendedResultMappings);
    }
    //构建resultMap实例
    ResultMap resultMap = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping)
      .discriminator(discriminator)
      .build();
    //添加reusltMap实例
    configuration.addResultMap(resultMap);
    return resultMap;
  }

  /**
   * 构建鉴别器
   *
   * @param resultType       ResutMap标签的java类型
   * @param column           列名
   * @param javaType         Discriminator标签的java类型
   * @param jdbcType         Discriminator标签的jdbc类型
   * @param typeHandler      Discriminator标签的typeHandler
   * @param discriminatorMap discriminator 的 case 子元素
   * @return
   */
  public Discriminator buildDiscriminator(
    Class<?> resultType,
    String column,
    Class<?> javaType,
    JdbcType jdbcType,
    Class<? extends TypeHandler<?>> typeHandler,
    Map<String, String> discriminatorMap) {
    //构建Discriminator对应的ResultMapping,Discriminator是不支持懒加载的。
    ResultMapping resultMapping = buildResultMapping(
      resultType,
      null,
      column,
      javaType,
      jdbcType,
      null,
      null,
      null,
      null,
      typeHandler,
      new ArrayList<>(),
      null,
      null,
      false);
    Map<String, String> namespaceDiscriminatorMap = new HashMap<>();
    for (Map.Entry<String, String> e : discriminatorMap.entrySet()) {
      String resultMap = e.getValue();
      resultMap = applyCurrentNamespace(resultMap, true);
      namespaceDiscriminatorMap.put(e.getKey(), resultMap);
    }
    return new Discriminator.Builder(configuration, resultMapping, namespaceDiscriminatorMap).build();
  }

  /**
   * 构建MapperStatement对象，并添加到Mybatis全局配置信息中
   *
   * @param id             DML标签的ID
   * @param sqlSource      动态SQL源
   * @param statementType  Statement类型
   * @param sqlCommandType SQL指令类型
   * @param fetchSize      这是尝试影响驱动程序每次批量返回的结果行数和这个设置值相等。默认值为 unset（依赖驱动）。
   * @param timeout        这个设置是在抛出异常之前，驱动程序等待数据库返回请求结果的秒数。默认值为 unset（依赖驱动）。
   * @param parameterMap   paramterMapId,参数映射信息封装类，Mapper.xml的parameterMap标签
   * @param parameterType  参数类型
   * @param resultMap      resultMapId,结果映射信息封装类，Mapper.xml的resultMap标签 集合
   * @param resultType     结果映射类型
   * @param resultSetType  ResultSetType枚举
   * @param flushCache     刷新缓存标记
   * @param useCache       使用缓存标记
   * @param resultOrdered  参考博客：<a href='https://blog.csdn.net/isea533/article/details/51533296?utm_source=blogxgwz9'>https://blog.csdn.net/isea533/article/details/51533296?utm_source=blogxgwz9</a>
   * @param keyGenerator   Key生成器
   * @param keyProperty    仅对 insert 和 update 有用）唯一标记一个属性，MyBatis 会通过 getGeneratedKeys 的返回值或者通过 insert 语句的 selectKey 子元素设置它的键值，默认：unset。如果希望得到多个生成的列，也可以是逗号分隔的属性名称列表。
   * @param keyColumn      （仅对 insert 和 update 有用）通过生成的键值设置表中的列名，这个设置仅在某些数据库（像 PostgreSQL）是必须的，当主键列不是表中的第一列的时候需要设置。 如果希望得到多个生成的列，也可以是逗号分隔的属性名称列表
   * @param databaseId     DML标签中配置的databaseId属性
   * @param lang           语言驱动，默认是 {@link org.apache.ibatis.scripting.xmltags.XMLLanguageDriver}
   * @param resultSets     结果集
   * @return MappedStatement对象
   */
  public MappedStatement addMappedStatement(
    String id,
    SqlSource sqlSource,
    StatementType statementType,
    SqlCommandType sqlCommandType,
    Integer fetchSize,
    Integer timeout,
    String parameterMap,
    Class<?> parameterType,
    String resultMap,
    Class<?> resultType,
    ResultSetType resultSetType,
    boolean flushCache,
    boolean useCache,
    boolean resultOrdered,
    KeyGenerator keyGenerator,
    String keyProperty,
    String keyColumn,
    String databaseId,
    LanguageDriver lang,
    String resultSets) {

    //未成功解析CacheRef在这里会抛出异常
    if (unresolvedCacheRef) {
      throw new IncompleteElementException("Cache-ref not yet resolved");
    }

    //检查ID是否简写，简写就应用当前命名空间；
    id = applyCurrentNamespace(id, false);
    //是否是Select标签
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    //构建MappedStatement对象

    MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType)
      .resource(resource)
      .fetchSize(fetchSize)
      .timeout(timeout)
      .statementType(statementType)
      .keyGenerator(keyGenerator)
      .keyProperty(keyProperty)
      .keyColumn(keyColumn)
      .databaseId(databaseId)
      .lang(lang)
      .resultOrdered(resultOrdered)
      .resultSets(resultSets)
      //获取resultMap对应的ResultMaps对象集合
      .resultMaps(getStatementResultMaps(resultMap, resultType, id))
      .resultSetType(resultSetType)
      //如果不是select指令的话，默认是缓存的
      .flushCacheRequired(valueOrDefault(flushCache, !isSelect))
      //使用缓存，默认如果是select指令是使用缓存的
      .useCache(valueOrDefault(useCache, isSelect))
      //缓存使用当前的缓存实例
      .cache(currentCache);

    //获取paramterMap对应ParameterMap对象
    ParameterMap statementParameterMap = getStatementParameterMap(parameterMap, parameterType, id);
    if (statementParameterMap != null) {
      statementBuilder.parameterMap(statementParameterMap);
    }
    //构建MapperStatement对象
    MappedStatement statement = statementBuilder.build();
    //添加到mybatis的全局配置信息中
    configuration.addMappedStatement(statement);
    return statement;
  }

  /**
   * Backward compatibility signature 'addMappedStatement'.
   *
   * @param id             the id
   * @param sqlSource      the sql source
   * @param statementType  the statement type
   * @param sqlCommandType the sql command type
   * @param fetchSize      the fetch size
   * @param timeout        the timeout
   * @param parameterMap   the parameter map
   * @param parameterType  the parameter type
   * @param resultMap      the result map
   * @param resultType     the result type
   * @param resultSetType  the result set type
   * @param flushCache     the flush cache
   * @param useCache       the use cache
   * @param resultOrdered  the result ordered
   * @param keyGenerator   the key generator
   * @param keyProperty    the key property
   * @param keyColumn      the key column
   * @param databaseId     the database id
   * @param lang           the lang
   * @return the mapped statement
   */
  public MappedStatement addMappedStatement(String id, SqlSource sqlSource, StatementType statementType,
                                            SqlCommandType sqlCommandType, Integer fetchSize, Integer timeout, String parameterMap, Class<?> parameterType,
                                            String resultMap, Class<?> resultType, ResultSetType resultSetType, boolean flushCache, boolean useCache,
                                            boolean resultOrdered, KeyGenerator keyGenerator, String keyProperty, String keyColumn, String databaseId,
                                            LanguageDriver lang) {
    return addMappedStatement(
      id, sqlSource, statementType, sqlCommandType, fetchSize, timeout,
      parameterMap, parameterType, resultMap, resultType, resultSetType,
      flushCache, useCache, resultOrdered, keyGenerator, keyProperty,
      keyColumn, databaseId, lang, null);
  }

  /**
   * 当value为null时，返回default
   *
   * @param value        值
   * @param defaultValue 默认值
   * @param <T>          返回类型
   * @return 当value为null时，返回default
   */
  private <T> T valueOrDefault(T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }

  /**
   * 获取Statment的参数映射
   * @param parameterMapName 参数映射Id
   * @param parameterTypeClass 参数类型
   * @param statementId DML标签的ID
   * @return ParameterMap对象
   */
  private ParameterMap getStatementParameterMap(
    String parameterMapName,
    Class<?> parameterTypeClass,
    String statementId) {
    //检查ID是否简写，简写就应用当前命名空间；
    parameterMapName = applyCurrentNamespace(parameterMapName, true);
    ParameterMap parameterMap = null;
    if (parameterMapName != null) {
      try {
        //查找出parameterMapName对应的ParameterMap对象
        parameterMap = configuration.getParameterMap(parameterMapName);
      } catch (IllegalArgumentException e) {
        throw new IncompleteElementException("Could not find parameter map " + parameterMapName, e);
      }
    } else if (parameterTypeClass != null) {
      //构建parameterTypeClass的parameterMap对象
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      parameterMap = new ParameterMap.Builder(
        configuration,
        statementId + "-Inline",
        parameterTypeClass,
        parameterMappings).build();
    }
    return parameterMap;
  }
  /**
   * 获取Statment的结果映射集合
   * @param resultMap resultMapId
   * @param resultType 结果映射类型
   * @param statementId  DML标签的ID
   * @return ResultMap对象集合
   */
  private List<ResultMap> getStatementResultMaps(
    String resultMap,
    Class<?> resultType,
    String statementId) {
    //检查ID是否简写，简写就应用当前命名空间；
    resultMap = applyCurrentNamespace(resultMap, true);

    List<ResultMap> resultMaps = new ArrayList<>();
    if (resultMap != null) {
      //查找出resultMapName对应的resultMap对象,并添加到resultMaps中
      String[] resultMapNames = resultMap.split(",");
      for (String resultMapName : resultMapNames) {
        try {
          resultMaps.add(configuration.getResultMap(resultMapName.trim()));
        } catch (IllegalArgumentException e) {
          throw new IncompleteElementException("Could not find result map '" + resultMapName + "' referenced from '" + statementId + "'", e);
        }
      }
    } else if (resultType != null) {
      //构建resultType的ResultMap
      ResultMap inlineResultMap = new ResultMap.Builder(
        configuration,
        statementId + "-Inline",
        resultType,
        new ArrayList<>(),
        null).build();
      resultMaps.add(inlineResultMap);
    }
    return resultMaps;
  }
/**
 * 对ResutMap标签的result标签封装ResultMapping对象
 * @param resultType resultMap标签对应的javaType
 * @param property 属性名
 * @param column 列名
 * @param javaType 属性的java类型
 * @param jdbcType 属性的jdbc类型
 * @param nestedSelect 嵌套的 select id
 * @param nestedResultMap 获取嵌套的 resultMap id
 * @param notNullColumn 获取指定的不为空才创建实例的列
 * @param columnPrefix 列前缀,columnPrefix用法：https://www.cnblogs.com/circlebreak/p/6647710.html
 * @param typeHandler 类型转换器
 * @param flags 属性标记
 * @param resultSet 集合的多结果集
 * @param foreignColumn 指定外键对应的列名
 * @param lazy 懒加载
 */
  public ResultMapping buildResultMapping(
    Class<?> resultType,
    String property,
    String column,
    Class<?> javaType,
    JdbcType jdbcType,
    String nestedSelect,
    String nestedResultMap,
    String notNullColumn,
    String columnPrefix,
    Class<? extends TypeHandler<?>> typeHandler,
    List<ResultFlag> flags,
    String resultSet,
    String foreignColumn,
    boolean lazy) {
    /**
     * 获取结果属性的javaType，只有javaType为null的情况下，就会自动获取javaType并返回；
     * javaType不为null时，直接返回javaType
     */
    Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);
    //构建TypeHandler实例，没有注册到{@link #typeHandlerRegistry}，只是返回TypeHandler实例对象
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
    List<ResultMapping> composites;
    if ((nestedSelect == null || nestedSelect.isEmpty()) && (foreignColumn == null || foreignColumn.isEmpty())) {
      composites = Collections.emptyList();
    } else {
      //复合映射
      composites = parseCompositeColumnName(column);
    }
    //封装ResutMap标签的属性标签封装
    return new ResultMapping.Builder(configuration, property, column, javaTypeClass)
      .jdbcType(jdbcType)
      .nestedQueryId(applyCurrentNamespace(nestedSelect, true))
      .nestedResultMapId(applyCurrentNamespace(nestedResultMap, true))
      .resultSet(resultSet)
      .typeHandler(typeHandlerInstance)
      .flags(flags == null ? new ArrayList<>() : flags)
      .composites(composites)
      .notNullColumns(parseMultipleColumnNames(notNullColumn))
      .columnPrefix(columnPrefix)
      .foreignColumn(foreignColumn)
      .lazy(lazy)
      .build();
  }

  /**
   * Backward compatibility signature 'buildResultMapping'.
   *
   * @param resultType      the result type
   * @param property        the property
   * @param column          the column
   * @param javaType        the java type
   * @param jdbcType        the jdbc type
   * @param nestedSelect    the nested select
   * @param nestedResultMap the nested result map
   * @param notNullColumn   the not null column
   * @param columnPrefix    the column prefix
   * @param typeHandler     the type handler
   * @param flags           the flags
   * @return the result mapping
   */
  public ResultMapping buildResultMapping(Class<?> resultType, String property, String column, Class<?> javaType,
                                          JdbcType jdbcType, String nestedSelect, String nestedResultMap, String notNullColumn, String columnPrefix,
                                          Class<? extends TypeHandler<?>> typeHandler, List<ResultFlag> flags) {
    return buildResultMapping(
      resultType, property, column, javaType, jdbcType, nestedSelect,
      nestedResultMap, notNullColumn, columnPrefix, typeHandler, flags, null, null, configuration.isLazyLoadingEnabled());
  }

  /**
   * Gets the language driver.
   *
   * @param langClass the lang class
   * @return the language driver
   * @deprecated Use {@link Configuration#getLanguageDriver(Class)}
   */
  @Deprecated
  public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass) {
    return configuration.getLanguageDriver(langClass);
  }
  /**
   * 分割列名，并返回
   * @param columnName 列名
   * @return eg:'{col1,col2}' => [col1,col2]
   */
  private Set<String> parseMultipleColumnNames(String columnName) {
    Set<String> columns = new HashSet<>();
    if (columnName != null) {
      if (columnName.indexOf(',') > -1) {
        StringTokenizer parser = new StringTokenizer(columnName, "{}, ", false);
        while (parser.hasMoreTokens()) {
          String column = parser.nextToken();
          columns.add(column);
        }
      } else {
        columns.add(columnName);
      }
    }
    return columns;
  }
/**
 * 解析复合映射
 * <p>
 *     参考博客:<a href='https://www.cnblogs.com/wei-zw/p/9001906.html'>https://www.cnblogs.com/wei-zw/p/9001906.html</a>
 * </p>
 * @param columnName 列名
 * @return ResutMap标签的属性标签封装集合
 */
  private List<ResultMapping> parseCompositeColumnName(String columnName) {
    //ResutMap标签的属性标签封装集合
    List<ResultMapping> composites = new ArrayList<>();
    //如果columnName不为null 同时colunmnName中含有"=" 或者含有","号
    if (columnName != null && (columnName.indexOf('=') > -1 || columnName.indexOf(',') > -1)) {
      //分割字符串
      StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
      while (parser.hasMoreTokens()) {
        //获取属性
        String property = parser.nextToken();
        ////获取列
        String column = parser.nextToken();
        /**
         * 构建ResutMap标签的属性标签封装，这里的TypeHandler采用的是UnknownTypeHandler,该TypeHandler
         * 在{@link BaseTypeHandler}的抽象方法中根据返回的结果集提供的列去获取对应的TypeHandler时候，
         * 在获取不到的情况下，就会使用{@link ObjectTypeHandler}处理
         */
        ResultMapping complexResultMapping = new ResultMapping.Builder(
          configuration, property, column, configuration.getTypeHandlerRegistry().getUnknownTypeHandler()).build();
        composites.add(complexResultMapping);
      }
    }
    return composites;
  }
/**
 * 分析结果属性的javaType
 * <p>
 *     在{@code javaType}为null的情况下，通过{@code resultType}的反射信息，
 *     从中获取对应{@code property}的setter方法的参数类型,没有获取到的情况下，就会返回Object
 *     ；否则直接返回{@code javaType}
 * </p>
 * @param resultType 结果类型
 * @param property 结果属性
 * @param javaType 参数不为null，方法就直接返回参数，不做任何处理；
 * @return 结果属性的javaType
 */
  private Class<?> resolveResultJavaType(Class<?> resultType, String property, Class<?> javaType) {
    if (javaType == null && property != null) {
      try {
        //封装resultType的反射信息
        MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
        //获取对应{@code property}的setter方法的参数类型
        javaType = metaResultType.getSetterType(property);
      } catch (Exception e) {
        // ignore, following null check statement will deal with the situation
      }
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }
/**
 * 解析获取参数或者结果的属性的java类型
 * <p>
 *     javaType不为null的情况下，会原封不动的返回javaType,但是为空的话，会反射resultType的property属性的getter方法的返回类型作为javaType。
 * </p>
 * 一些特殊情况：
 * <ol style='margin-top:0px'>
 *     <li>如果jdbcType等于{@link JdbcType#CURSOR}，javaType为{@link java.sql.ResultSet}</li>
 *     <li>如果reusltType是{@link Map}的实现类，javaType为Object</li>
 *     <li>如果到最后还是无法确定javaType的对应类型，默认返回Object</li>
 * </ol>
 * @param resultType 参数或者结果的类型
 * @param property 参数或者结果的属性名
 * @param javaType 参数或者结果的属性java类型
 * @param jdbcType 参数或者结果的属性jdbc类型
 */
  private Class<?> resolveParameterJavaType(Class<?> resultType, String property, Class<?> javaType, JdbcType jdbcType) {
    if (javaType == null) {
      if (JdbcType.CURSOR.equals(jdbcType)) {
        // jdbcType为JdbcType.CURSOR，则javaType为java.sql.ResultSet.class
        javaType = java.sql.ResultSet.class;
      } else if (Map.class.isAssignableFrom(resultType)) {
        // resultType为Map系列，javaType为Object.class
        javaType = Object.class;
      } else {
        // MetaClass是mybatis用于简化反射操作的封装类
        MetaClass metaResultType = MetaClass.forClass(resultType, configuration.getReflectorFactory());
        //用property属性的getter方法的返回类型作为javaType。
        javaType = metaResultType.getGetterType(property);
      }
    }
    if (javaType == null) {
      javaType = Object.class;
    }
    return javaType;
  }

}
