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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLMapperBuilder extends BaseBuilder {

  /**
   * XML解析器
   */
  private final XPathParser parser;
  /**
   * 构建映射器助理
   */
  private final MapperBuilderAssistant builderAssistant;
  /**
   * SQL碎片
   */
  private final Map<String, XNode> sqlFragments;
  /**
   * 资源路径
   */
  private final String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
      configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
      configuration, resource, sqlFragments);
  }

  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * 解析Mapper.xml，解析mapper标签下的所有标签，并对解析出来的标签信息加以封装，
   * 然后添加到Mybatis全局配置信息中。然后重新解析Mybatis全局配置信息中未能完成解析的
   * ResultMap标签信息，CacheRef标签信息，DML标签信息
   */
  public void parse() {
    //如果没有加载过resource
    if (!configuration.isResourceLoaded(resource)) {
      //解析mapper标签下的所有标签，并对解析出来的标签信息加以封装，然后添加到Mybatis全局配置信息中
      configurationElement(parser.evalNode("/mapper"));
      //将resouce添加到Mybatis全局配置信息中，以防止重新加载对应的resource
      configuration.addLoadedResource(resource);
      //找出对应当前Mapper.xml的java类型,并添加到Mybatis全局配置信息中
      bindMapperForNamespace();
    }

    //重新解析Mybatis全局配置信息中未能完成解析的ResultMap标签信息
    parsePendingResultMaps();
    //重新解析Mybatis全局配置信息中未能完成解析的CacheRef标签信息
    parsePendingCacheRefs();
    //重新解析Mybatis全局配置信息中未能完成解析的DML标签信息
    parsePendingStatements();
  }

  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  /**
   * 解析mapper标签下的所有标签，并对解析出来的标签信息加以封装，
   * 然后添加到Mybatis全局配置信息中
   *
   * @param context mapper标签
   */
  private void configurationElement(XNode context) {
    try {
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.isEmpty()) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      //解析 cache-ref 标签
      cacheRefElement(context.evalNode("cache-ref"));
      //解析 cache 标签
      cacheElement(context.evalNode("cache"));
      //解析 paramterMap 标签
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      //解析 resultMap 表
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      //解析sql标签
      sqlElement(context.evalNodes("/mapper/sql"));
      //构建每个DML标签对应的XMLStatmentBuilder，并通过XMLStatementBuilder对DML标签进行解析
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }

  /**
   * 构建每个DML标签对应的XMLStatmentBuilder，并通过XMLStatementBuilder对DML标签
   * 进行解析
   *
   * @param list DML标签集合(select|insert|update|delete标签)
   */
  private void buildStatementFromContext(List<XNode> list) {
    //先构建对应mybatis全局配置信息的databaseId的Statement
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    //mybatis认为DML标签没有设置databaseId属性都是对应的mybatis全局配置信息的databaseId
    buildStatementFromContext(list, null);
  }

  /**
   * 构建每个DML标签对应的XMLStatmentBuilder，并通过XMLStatementBuilder对DML标签
   * 进行解析
   *
   * @param list               DML标签集合(select|insert|update|delete标签)
   * @param requiredDatabaseId mybatis全局配置信息的databaseId
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    //遍历DML标签集合
    for (XNode context : list) {
      //对每个DML标签新建一个XML Statement 构建器
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        //解析DML标签，封装成MappedStatement对象，添加到Mybatis全局配置信息中
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        //发生未能完成解析异常时，将XML Statment构建器添加到Mybatis全局配置信息中，后期再尝试解析。
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }
  /**
   * 重新解析Mybatis全局配置信息中未能完成的ResultMap标签信息
   */
  private void parsePendingResultMaps() {
    //获取Mybatis全局配置信息中未能完成的ResultMap标签信息
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    //同步,防止重新解析
    synchronized (incompleteResultMaps) {
      //获取incompleteResultMaps的迭代器
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      //遍历incompleteResultMaps
      while (iter.hasNext()) {
        try {
          //获取ResultMap标签信息解析器重新解析
          iter.next().resolve();
          //如果解析成功，就从集合中移除
          iter.remove();
        } catch (IncompleteElementException e) {
          //抛出异常时，不做任何操作，让未能完成的ResultMap标签信息继续在Mybatis全局配置信息中等待下次解析
          // ResultMap is still missing a resource... ResultMap标签仍然丢失一个资源
        }
      }
    }
  }
  /**
   * 重新解析Mybatis全局配置信息中未能完成解析的CacheRef标签信息
   */
  private void parsePendingCacheRefs() {
    //获取Mybatis全局配置信息中未能完成解析的CacheRef标签信息
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    //同步,防止重新解析
    synchronized (incompleteCacheRefs) {
      //获取incompleteCacheRefs的迭代器
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      //遍历incompleteCacheRefs
      while (iter.hasNext()) {
        try {
          //获取CacheRef标签信息解析器重新解析
          iter.next().resolveCacheRef();
          //如果解析成功，就从集合中移除
          iter.remove();
        } catch (IncompleteElementException e) {
          //抛出异常时，不做任何操作，让未能完成解析的CacheRef标签信息继续在Mybatis全局配置信息中等待下次解析
          // Cache ref is still missing a resource... CacheRef标签仍然丢失一个资源
        }
      }
    }
  }
  /**
   * 重新解析Mybatis全局配置信息中未能完成解析的DML标签信息
   */
  private void parsePendingStatements() {
    //获取Mybatis全局配置信息中未能完成解析的DML标签信息
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    //同步,防止重新解析
    synchronized (incompleteStatements) {
      //获取incompleteStatements的迭代器
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      //遍历incompleteStatements
      while (iter.hasNext()) {
        try {
          //获取DML标签信息解析器重新解析
          iter.next().parseStatementNode();
          //如果解析成功，就从集合中移除
          iter.remove();
        } catch (IncompleteElementException e) {
          //抛出异常时，不做任何操作，让未能完成解析的DML标签信息继续在Mybatis全局配置信息中等待下次解析
          // Statement is still missing a resource... DML标签仍然丢失一个资源
        }
      }
    }
  }

  /**
   * cache-ref 标签解析
   * <p>
   * cache-ref标签作用：若想在命名空间中共享相同的缓存配置和实例，可以使用 cache-ref 元素来引用另外一个缓存。
   * </p>
   * <p>
   * 新建一个{@link CacheRefResolver}实例，并通过{@link CacheRefResolver}实例获取CacheRef,因为在
   * 因为该方法已经将找到的{@link Cache}实例赋值到{@link #builderAssistant}的{@link MapperBuilderAssistant#currentCache}中，
   * 也会更新{@link #builderAssistant}的@link MapperBuilderAssistant#unresolvedCacheRef}为false.
   * </p>
   * <p>
   * 获取更新CachRef期间抛出的{@link IncompleteElementException}都会被处理调用，并将{@link CacheRefResolver}实例
   * 添加{@link #configuration}的{@link Configuration#incompleteCacheRefs}中
   * </p>
   *
   * @param context
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        //解析缓存引用CacheRef
        /**
         * 因为该方法已经将找到的{@link Cache}实例赋值到{@link #builderAssistant}
         *  的{@link MapperBuilderAssistant#currentCache}中，也会更新{@link #builderAssistant}的
         *  {@link MapperBuilderAssistant#unresolvedCacheRef}为false.
         */
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        //添加解析CacheRef失败的{@link CacheRefResolver}，推测是为了统计、报告 TODO 推测
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }

  /**
   * cache标签解析
   * <p>
   * cache作用：给定命名空间的缓存配置。
   * </p>
   * <p>
   * 从Mapper.xml文件中获取Cache标签的配置信息，并根据配置的信息构建Cache的实例对象。
   * </p>
   * <p>
   * 参考博客：<a href='https://blog.csdn.net/qq_28061489/article/details/79329095'>https://blog.csdn.net/qq_28061489/article/details/79329095</a>
   * </p>
   */
  private void cacheElement(XNode context) {
    if (context != null) {
      //type：指定自定义缓存的全类名(实现Cache接口即可),PERPETUAL是别名，对应PerpetualCache缓存类
      String type = context.getStringAttribute("type", "PERPETUAL");
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      /**
       * eviction：缓存的回收策略
       * LRU - 最近最少使用，移除最长时间不被使用的对象,对应实现类:LruCache
       * FIFO - 先进先出，按对象进入缓存的顺序来移除它们,对应实现类:FifoCache
       * SOFT - 软引用，移除基于垃圾回收器状态和软引用规则的对象,对应实现类:LruCache
       * WEAK - 弱引用，更积极地移除基于垃圾收集器和弱引用规则的对象,对应实现类:WeakCache
       */
      String eviction = context.getStringAttribute("eviction", "LRU");
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      //缓存刷新间隔,缓存多长时间清空一次，默认不清空，设置一个毫秒值,只有
      Long flushInterval = context.getLongAttribute("flushInterval");
      //size：缓存存放多少个元素
      Integer size = context.getIntAttribute("size");
      /**
       * readOnly：是否只读
       * true：只读：mybatis认为所有从缓存中获取数据的操作都是只读操作，不会修改数据。
       * false：读写(默认)：mybatis觉得获取的数据可能会被修改
       */
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      //若缓存中找不到对应的key，是否会一直blocking，直到有对应的数据进入缓存。
      boolean blocking = context.getBooleanAttribute("blocking", false);
      Properties props = context.getChildrenAsProperties();
      //构建Cache实例
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }

  /**
   * 解析ParameterMap标签,构建ParameterMap实例对象,并添加到{@link #configuration}中
   */
  private void parameterMapElement(List<XNode> list) {
    for (XNode parameterMapNode : list) {
      //ParameterMap标签的ID属性
      String id = parameterMapNode.getStringAttribute("id");
      //ParameterMap标签的type属性
      String type = parameterMapNode.getStringAttribute("type");
      //获取对应的类
      Class<?> parameterClass = resolveClass(type);
      //获取parameterMap下面的子标签 parameter
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<>();
      for (XNode parameterNode : parameterNodes) {
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        //属性对应的结果映射
        String resultMap = parameterNode.getStringAttribute("resultMap");
        /**
         * mode:当使用存储过程时，需要设置一个参数 mode，其值有 IN（输入参数）、OUT（输出参数）、INOUT（输入 / 输出参数）。
         * 一个存储过程可以有多个 IN 参数，至多有一个 OUT 或 INOUT 参数
         */
        String mode = parameterNode.getStringAttribute("mode");
        //类型接收器
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        //numericScale表示小数点保留的位数
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        //属性对应的java类型
        Class<?> javaTypeClass = resolveClass(javaType);
        //属性对应的jdbc类型
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        //属性对应的TypeHandler实现类
        Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
        // 构建ParameterMapping,一个属性一个ParameterMapping
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      //构建ParameterMap,并添加到{@link #configuration}中
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }


  /**
   * 解析resultMap标签（处理多个）
   */
  private void resultMapElements(List<XNode> list) throws Exception {
    //循环调用reusltMapElemet(XNode)方法
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried 忽略，它会被重试。
      }
    }
  }

  /**
   * 解析resultMap标签（处理单个）
   */
  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.emptyList(), null);
  }

  /**
   * 解析resultMap标签，构建resultMap实例
   *
   * @param resultMapNode            resultMap节点
   * @param additionalResultMappings 额外的resultMapping
   * @param enclosingType            有可能是reusltMap对应的javaType
   * @return 构建成功的ResultMap实例
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings, Class<?> enclosingType)  {
    /**
     * {@link XNode#getValueBasedIdentifier}就是mybatis给XNode的一个标识符。
     * 如：Mapper的ResultMap => Mapper[XXX]_ResultMap[XXX](XXX:显示id的属性值，获取不到就获取value的属性值，
     */
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    //ResultMap对应的javaType的包名+类名，从这里可以看出，有四个属性都可以指定javaType，优先级为：type=>ofType=>resultType=>javaType
    String type = resultMapNode.getStringAttribute("type",
      resultMapNode.getStringAttribute("ofType",
        resultMapNode.getStringAttribute("resultType",
          resultMapNode.getStringAttribute("javaType"))));
    //ResultMap对应的javaType
    Class<?> typeClass = resolveClass(type);
    if (typeClass == null) {
      //针对未定义返回类型的元素的返回值类型解析,看看 association 和 case 元素没有显式地指定返回值类型
      typeClass = inheritEnclosingType(resultMapNode, enclosingType);
    }
    Discriminator discriminator = null;
    List<ResultMapping> resultMappings = new ArrayList<>();
    resultMappings.addAll(additionalResultMappings);
    // 加载子元素
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      if ("constructor".equals(resultChild.getName())) {
        //处理constructor标签
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        //处理discriminator标签
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        //处理id标签
        List<ResultFlag> flags = new ArrayList<>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    //Id,没有设置mybatis就会自动配置一个
    String id = resultMapNode.getStringAttribute("id",
      resultMapNode.getValueBasedIdentifier());
    //继承
    String extend = resultMapNode.getStringAttribute("extends");
    //自动映射
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      //构建{@link ResultMap}实例，并添加到{@link #configuration}
      return resultMapResolver.resolve();
    } catch (IncompleteElementException e) {
      //添加构建ResultMap时抛出异常的resultMapResolver实例
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * 针对未定义返回类型的元素的返回值类型解析,看看 association 和 case 元素没有显式地指定返回值类型
   *
   * @param resultMapNode 标签节点
   * @param enclosingType
   * @return
   */
  protected Class<?> inheritEnclosingType(XNode resultMapNode, Class<?> enclosingType) {
    //association标签是用于配置非简单类型的映射关系
    if ("association".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      String property = resultMapNode.getStringAttribute("property");
      if (property != null && enclosingType != null) {
        // 根据反射信息确定字段的类型,从代码中可以看出用的是proptery对应的setter方法的参数类型。
        MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
        return metaResultType.getSetterType(property);
      }
    } else if ("case".equals(resultMapNode.getName()) && resultMapNode.getStringAttribute("resultMap") == null) {
      // case 元素返回值属性与 resultMap 父元素相同
      return enclosingType;
    }
    return null;
  }

  /**
   * 处理Constructor标签
   *
   * <p>
   * constructor元素 ，指定使用指定参数列表的构造函数来实例化领域模型。注意：其子元素顺序必须与参数列表顺序对应.<br/>
   * idArg子元素 ，标记该入参为主键<br/>
   * arg子元素 ，标记该入参为普通字段(主键使用该子元素设置也是可以的)<br/>
   * 参考博客：<a href='https://www.cnblogs.com/fsjohnhuang/p/4076592.html'>https://www.cnblogs.com/fsjohnhuang/p/4076592.html</a>
   * </p>
   *
   * @param resultChild    子节点
   * @param resultType     resultMap的java类型
   * @param resultMappings resultMap的resultMapping集合
   * @throws Exception
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings)  {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      //每个标签都会有一个flags列表
      List<ResultFlag> flags = new ArrayList<>();
      flags.add(ResultFlag.CONSTRUCTOR);
      if ("idArg".equals(argChild.getName())) {//如果是idArg标签，{@code flag}还会多一个{@link ResultFlag.ID}表明该属性是一个主键
        flags.add(ResultFlag.ID);
      }
      //每个子标签都会封装成ResultMapping并添加到resultMappings
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * 处理discriminator标签
   *
   * @param context        discriminator标签节点
   * @param resultType     ResutMap标签的java类型
   * @param resultMappings {@code context}的ResutMap标签的属性标签封装类集合
   * @return
   * @throws Exception
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings)  {
    //列名
    String column = context.getStringAttribute("column");
    //java类(别名或者包名+类名)
    String javaType = context.getStringAttribute("javaType");
    //jdbc类型
    String jdbcType = context.getStringAttribute("jdbcType");
    //类型处理器（别名或者包名+类名)
    String typeHandler = context.getStringAttribute("typeHandler");
    //java类
    Class<?> javaTypeClass = resolveClass(javaType);
    //类型处理器类
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    //jdbc类型枚举
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // 解析 discriminator 的 case 子元素
    Map<String, String> discriminatorMap = new HashMap<>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      // 解析不同列值对应的不同 resultMap
      String resultMap = caseChild.getStringAttribute("resultMap",
        processNestedResultMappings(caseChild, resultMappings, resultType));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  /**
   * 解析Sql标签
   * <p>
   * 参考博客：<a href='https://segmentfault.com/a/1190000017378490'>https://segmentfault.com/a/1190000017378490</a>
   * </p>
   *
   * @param list sql标签元素
   */
  private void sqlElement(List<XNode> list) {
    //第一次用于处理 databaseId 与全局 Configuration 实例的 databaseId 一致的节点
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    /**
     * 第二次用于处理节点的 databaseId 为 null 的情况，针对同一个 id ，
     * 优先选择存在 databaseId 并且与数据源的一致。
     */
    sqlElement(list, null);
  }

  /**
   * 解析Sql标签
   *
   * @param list               sql标签元素
   * @param requiredDatabaseId 当前项目引用的dataBaseId
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      //对应的数据库Id
      String databaseId = context.getStringAttribute("databaseId");
      //ID
      String id = context.getStringAttribute("id");
      //检查ID是否简写，简写就应用当前命名空间
      id = builderAssistant.applyCurrentNamespace(id, false);
      //找出匹配当前配置的数据库Id的sql标签元素，然后加入sqlFragments
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        sqlFragments.put(id, context);
      }
    }
  }

  /**
   * 匹配当前配置的数据库Id
   *
   * @param id                 SQL标签ID属性
   * @param databaseId         SQL标签databaseId属性
   * @param requiredDatabaseId 当前项目引用的dataBaseId
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    //当前项目引用的dataBaseId是否等于当前项目引用的dataBaseId
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    /**
     * 到这里requiredDatabaseId就为null，也因为不设置databaseId默认就是对应当前项目引用的dataBaseId，
     * 所以就算dataBaseId有设置，当前项目引用的dataBaseId没有设置，
     * 就相当于一个有值的字符串判断一个空字符串一个道理，所以认为是不匹配的。
     */
    if (databaseId != null) {
      return false;
    }

    /**
     * 没有加入sqlFragments，统一返回true。因为到这里databaseId就是为null的情况，
     * 也因为不设置databaseId默认就是对应当前项目引用的dataBaseId，就相当于一个空字符串判断一个空字符串一样，
     * 所以认为是匹配的。
     */
    if (!this.sqlFragments.containsKey(id)) {
      return true;
    }
    /**
     * 下面代码触发条件是：databaseId为null，使得databaseId不等于requiredDatabaseId，id已经存在sqlFargment中。
     * 如果我没有推测错误的话，mybatis认为没有设置databaseId的sql标签，是不够明确认为这个sql标签就是
     * 对应于当前项目引用的dataBaseId，所以当出现多个相同id的sql标签时，mybatis会以最后一个作为真正
     * 对应于当前项目引用的dataBaseId的sql标签，所以这里如果上一个sql标签没有设置databaseId话，
     * 会覆盖掉上一个sql标签。
     */
    // skip this fragment if there is a previous one with a not null databaseId
    //如果它是上一个有databaseId的fragment就跳过
    XNode context = this.sqlFragments.get(id);
    return context.getStringAttribute("databaseId") == null;
  }

  /**
   * 根据{@code context}中构建{@link ResultMapping}
   *
   * @param context    标签节点信息
   * @param resultType resultMap标签对应的javaType
   * @param flags      标签标记，表明{@code context}是个构造函数的参数 {@link ResultFlag#CONSTRUCTOR} ，还是一个ID {@link ResultFlag#ID}
   * @return
   * @throws Exception
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags)  {
    //获取属性名
    String property;
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {//如果是<Constructor>标签下的标签，就会有ResultFlag.CONSTRUCTOR标识
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    //列名
    String column = context.getStringAttribute("column");
    // java 类型
    String javaType = context.getStringAttribute("javaType");
    // jdbc 类型
    String jdbcType = context.getStringAttribute("jdbcType");
    // 嵌套的 select id
    String nestedSelect = context.getStringAttribute("select");
    // 获取嵌套的 resultMap id
    String nestedResultMap = context.getStringAttribute("resultMap", () ->
        processNestedResultMappings(context, Collections.emptyList(), resultType));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    // 列前缀,columnPrefix用法：https://www.cnblogs.com/circlebreak/p/6647710.html
    String columnPrefix = context.getStringAttribute("columnPrefix");
    // 类型转换器
    String typeHandler = context.getStringAttribute("typeHandler");
    //集合的多结果集
    String resultSet = context.getStringAttribute("resultSet");
    //指定外键对应的列名
    String foreignColumn = context.getStringAttribute("foreignColumn");
    //指定外键对应的列名,优先看当前标签的属性设置，没有才看全局配置configuration
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    //加载返回值类型
    Class<?> javaTypeClass = resolveClass(javaType);
    // 加载类型转换器类型
    Class<? extends TypeHandler<?>> typeHandlerClass = resolveClass(typeHandler);
    // 加载 jdbc 类型对象
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }

  /**
   * 处理嵌套的ResultMappings
   * <p>
   * 该方法只会处理association,collection,case标签
   * </p>
   * <p>
   * 如果指定了 select 属性，则映射时只需要获取对应 select 语句的 resultMap；
   * 如果未指定，则需要重新调用 {@link #resultMapElement(XNode, List, Class)}
   * 构建resultMap实例,并返回resultMap实例的Id
   * </p>
   *
   * @param context        节点
   * @param resultMappings resultMap的resultMapping集合
   * @param enclosingType  resultMap对应的javaType
   * @return resultMap实例的Id
   */
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings, Class<?> enclosingType) {
    if (Arrays.asList("association", "collection", "case").contains(context.getName())
      && context.getStringAttribute("select") == null) {
      // 如果是 association、collection 或 case 元素并且没有 select 属性
      // collection 元素没有指定 resultMap 或 javaType 属性，
      // 需要验证 resultMap 父元素对应的返回值类型是否有对当前集合的赋值入口
      validateCollection(context, enclosingType);
      ResultMap resultMap = resultMapElement(context, resultMappings, enclosingType);
      return resultMap.getId();
    }
    return null;
  }

  /**
   * 验证集合
   * <p>
   * 在context为collection标签且没有设置resultMap和javaType的时候，尝试从enclosingType的反射信息中判断有没有context的property的属性的setter方法。
   * 没有就抛出异常{@link BuilderException}。
   * </p>
   *
   * @param context       节点
   * @param enclosingType resultMap的javaType
   */
  protected void validateCollection(XNode context, Class<?> enclosingType) {
    //在context为列表标签且没有设置resultMap和javaType
    if ("collection".equals(context.getName()) && context.getStringAttribute("resultMap") == null
      && context.getStringAttribute("javaType") == null) {
      MetaClass metaResultType = MetaClass.forClass(enclosingType, configuration.getReflectorFactory());
      String property = context.getStringAttribute("property");
      //尝试从enclosingType的反射信息中判断context的property的属性的setter方法。
      if (!metaResultType.hasSetter(property)) {
        throw new BuilderException(
          "Ambiguous collection type for property '" + property + "'. You must specify 'javaType' or 'resultMap'.");
      }
    }
  }

  /**
   * 找出对应当前Mapper.xml的java类型,并添加到Mybatis全局配置信息中
   */
  private void bindMapperForNamespace() {
    //获取当前命名空间
    String namespace = builderAssistant.getCurrentNamespace();
    //如果当前命名空间不为null
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        //获取Mapper.xml需要绑定java类型
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required 因为绑定的java类型并不是必须的，所有忽略
      }
      //如果绑定类型不为null   且如果Mybatis全局配置信息中没有注册boundType
      if (boundType != null && !configuration.hasMapper(boundType)) {
        // Spring may not know the real resource name so we set a flag
        // to prevent loading again this resource from the mapper interface
        // look at MapperAnnotationBuilder#loadXmlResource
        // 译：
        // Spring框架可能不知道真正的资源名称，所以我们设置一个标签
        // 去防止再次加载这个资源映射接口
        // 查看MapperAnnotationBuilder#loadXmlResource
        //将{@code resource} 添加到mybatis全局配置信息的已加载资源集合里
        configuration.addLoadedResource("namespace:" + namespace);
        //将boundType加入到mybatis全局配置信息的mapperRegistry中
        configuration.addMapper(boundType);
      }
    }
  }

}
