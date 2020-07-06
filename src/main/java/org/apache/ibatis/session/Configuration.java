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
package org.apache.ibatis.session;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.builder.annotation.MethodResolver;
import org.apache.ibatis.builder.xml.XMLStatementBuilder;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.decorators.FifoCache;
import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.decorators.SoftCache;
import org.apache.ibatis.cache.decorators.WeakCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.datasource.jndi.JndiDataSourceFactory;
import org.apache.ibatis.datasource.pooled.PooledDataSourceFactory;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSourceFactory;
import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.CachingExecutor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ReuseExecutor;
import org.apache.ibatis.executor.SimpleExecutor;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.executor.loader.cglib.CglibProxyFactory;
import org.apache.ibatis.executor.loader.javassist.JavassistProxyFactory;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl;
import org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl;
import org.apache.ibatis.logging.log4j.Log4jImpl;
import org.apache.ibatis.logging.log4j2.Log4j2Impl;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMap;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.InterceptorChain;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.scripting.LanguageDriverRegistry;
import org.apache.ibatis.scripting.defaults.RawLanguageDriver;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
public class Configuration {

    protected Environment environment;

    /**
     * 允许在嵌套语句中使用分页（RowBounds）。如果允许使用则设置为 false。
     */
    protected boolean safeRowBoundsEnabled;
    /**
     * 允许在嵌套语句中使用分页（ResultHandler）。如果允许使用则设置为 false。
     */
    protected boolean safeResultHandlerEnabled = true;
    /**
     *    是否开启自动驼峰命名规则（camel case）映射，即从经典数据库列名 A_COLUMN 到经典 Java 属性名 aColumn 的类似映射。
     */
    protected boolean mapUnderscoreToCamelCase;
    /**
     * 当开启时，任何方法的调用都会加载该对象的所有属性。 否则，每个属性会按需加载（参考 lazyLoadTriggerMethods)。
     * 默认为false （在 3.4.1 及之前的版本默认值为 true）
     */
    protected boolean aggressiveLazyLoading;
    /**
     * 是否允许单一语句返回多结果集（需要驱动支持）。
     */
    protected boolean multipleResultSetsEnabled = true;
    /**
     * 允许 JDBC 支持自动生成主键，需要驱动支持。 如果设置为 true 则这个设置强制使用自动生成主键，尽管一些驱动不能支持但仍可正常工作（比如 Derby）
     */
    protected boolean useGeneratedKeys;
    /**
     * 使用列标签代替列名。不同的驱动在这方面会有不同的表现，具体可参考相关驱动文档或通过测试这两种不同的模式来观察所用驱动的结果。
     * <p>
     *     isUseColumnLabel，默认为true。根据Javadoc，这个ColumnLabel就是AS后的那个名字，如果没有AS的话，就是获取的原生的字段名
     * </p>
     */
    protected boolean useColumnLabel = true;
    /**
     * 全局地开启或关闭配置文件中的所有映射器已经配置的任何缓存。默认开启
     */
    protected boolean cacheEnabled = true;
    /**
     * 指定当结果集中值为 null 的时候是否调用映射对象的 setter（map 对象时为 put）方法，
     * 这在依赖于 Map.keySet() 或 null 值初始化的时候比较有用。注意基本类型（int、boolean 等）是不能设置成 null 的。
     */
    protected boolean callSettersOnNulls;
    /**
     * 允许使用构造函数的参数名作为语句参数名称。
     * 为了使用该特性，你的项目必须采用 Java 8 编译。
     */
    protected boolean useActualParamName = true;
    /**
     * 当返回行的所有列都是空时，MyBatis默认返回 null。
     * 当开启这个设置时，MyBatis会返回一个空实例。 请注意，它也适用于嵌套的结果集 （如集合或关联）。（新增于 3.4.2）
     */
    protected boolean returnInstanceForEmptyRow;
    protected boolean shrinkWhitespacesInSql;

    protected String logPrefix;
    protected Class<? extends Log> logImpl;
    /**
     * VFS(virtual File System)的作用就是采用标准的Unix系统调用读写位于不同物理介质上的不同文件系统,
     * 即为各类文件系统提供了一个统一的操作界面和应用编程接口
     */
    protected Class<? extends VFS> vfsImpl;
    /**
     * 本地缓存作用域
     * <ol>
     *     <li>LocalCacheScope.SESSION：数据库对象</li>
     *     <li>LocalCacheScope.STATEMENT:SQL腳本</li>
     * </ol>
     */
    protected LocalCacheScope localCacheScope = LocalCacheScope.SESSION;
    /**
     * null对应jdbcType
     */
    protected JdbcType jdbcTypeForNull = JdbcType.OTHER;
    /**
     * 指定哪个对象的方法触发一次延迟加载。默认方法为：equals,clone,hashCode,toString
     */
    protected Set<String> lazyLoadTriggerMethods = new HashSet<>(Arrays.asList("equals", "clone", "hashCode", "toString"));
    /**
     *设置超时时间，它决定驱动等待数据库响应的秒数。
     */
    protected Integer defaultStatementTimeout;
    /**
     * 为驱动的结果集获取数量（fetchSize）设置一个提示值。此参数只可以在查询设置中被覆盖。
     */
    protected Integer defaultFetchSize;
    protected ResultSetType defaultResultSetType;
    /**
     * 配置默认的执行器。
     * <ul>
     *     <li>SIMPLE 就是普通的执行器，默认；</li>
     *     <li>REUSE 执行器会重用预处理语句（prepared statements）；</li>
     *     <li>BATCH 执行器将重用语句并执行批量更新</li>
     * </ul>
     */
    protected ExecutorType defaultExecutorType = ExecutorType.SIMPLE;
    /**
     * 指定 MyBatis 应如何自动映射列到字段或属性。 NONE 表示取消自动映射；
     * PARTIAL 只会自动映射没有定义嵌套结果集映射的结果集。 FULL 会自动映射任意复杂的结果集（无论是否嵌套）。
     */
    protected AutoMappingBehavior autoMappingBehavior = AutoMappingBehavior.PARTIAL;
    /**
     * 指定发现自动映射目标未知列（或者未知属性类型）的行为。
     * <ol>
     *     <li>AutoMappingUnknownColumnBehavior.NONE:不做任何反应</li>
     *     <li>AutoMappingUnknownColumnBehavior.WARNING:输出提醒日志</li>
     *     <li>AutoMappingUnknownColumnBehavior.FAILING:映射失败 (抛出 SqlSessionException)</li>
     * </ol>
     */
    protected AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior = AutoMappingUnknownColumnBehavior.NONE;

    protected Properties variables = new Properties();
    protected ReflectorFactory reflectorFactory = new DefaultReflectorFactory();
    protected ObjectFactory objectFactory = new DefaultObjectFactory();
    protected ObjectWrapperFactory objectWrapperFactory = new DefaultObjectWrapperFactory();

    /**
     * 延迟加载的全局开关。当开启时，所有关联对象都会延迟加载。 特定关联关系中可通过设置fetchType属性来覆盖该项的开关状态。
     */
    protected boolean lazyLoadingEnabled = false;
    /**
     * Javassist代理工厂
     */
    protected ProxyFactory proxyFactory = new JavassistProxyFactory(); // #224 Using internal Javassist instead of OGNL
    /**
     * 当前项目引用的dataBaseId
     * <p>
     *     dataBaseId的作用：使得引用mybatis的项目，根据不同的数据库进行编写对应的数据库SQL
     * </p>
     */
    protected String databaseId;
    /**
     * Configuration factory class.
     * Used to create Configuration for loading deserialized unread properties.
     *
     * @see <a href='https://code.google.com/p/mybatis/issues/detail?id=300'>Issue 300 (google code)</a>
     */
    /**
     *  用户获取数据库连接的工厂类
     * Configuration factory class.
     * Used to create Configuration for loading deserialized unread properties.
     * 用于创建配置加载反序列化未读属性。;
     * @see <a href='https://code.google.com/p/mybatis/issues/detail?id=300'>Issue 300 (google code)</a>
     */
    protected Class<?> configurationFactory;

    /**
     * 映射器 注册类
     */
    protected final MapperRegistry mapperRegistry = new MapperRegistry(this);
    /**
     * 拦截器链
     */
    protected final InterceptorChain interceptorChain = new InterceptorChain();
    /**
     * 类型 处理 注册器
     */
    protected final TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry(this);
    /**
     * 类型别名注册器
     */
    protected final TypeAliasRegistry typeAliasRegistry = new TypeAliasRegistry();
    /**
     * 语言驱动注册器
     */
    protected final LanguageDriverRegistry languageRegistry = new LanguageDriverRegistry();

    protected final Map<String, MappedStatement> mappedStatements = new StrictMap<MappedStatement>("Mapped Statements collection")
            .conflictMessageProducer((savedValue, targetValue) ->
                    ". please check " + savedValue.getResource() + " and " + targetValue.getResource());
    /**
     * Mapper.xml的命名空间 - Cache实例【是一个经历了一层又一层装饰得到的装饰类】
     */
    protected final Map<String, Cache> caches = new StrictMap<>("Caches collection");
    /**
     * resultMap的StrictMap
     */
    protected final Map<String, ResultMap> resultMaps = new StrictMap<>("Result Maps collection");
    /**
     * Mapper.xml的ParameterMap标签信息。 key=ParameterMap标签的ID,value=ParameterMap标签的内容。
     */
    protected final Map<String, ParameterMap> parameterMaps = new StrictMap<>("Parameter Maps collection");
    /**
     * KeyGentertos的StrictMap
     */
    protected final Map<String, KeyGenerator> keyGenerators = new StrictMap<>("Key Generators collection");

    /**
     * 加载资源,就是mapper.xml
     */
    protected final Set<String> loadedResources = new HashSet<>();
    /**
     * 存放着SQL的StrictMap
     */
    protected final Map<String, XNode> sqlFragments = new StrictMap<>("XML fragments parsed from previous mappers");

    /**
     * XML Statment 构建类 集合LinkedList
     */
    protected final Collection<XMLStatementBuilder> incompleteStatements = new LinkedList<>();
    /**
     * 解析CacheRef失败的{@link CacheRefResolver} 集合LinkedList
     */
    protected final Collection<CacheRefResolver> incompleteCacheRefs = new LinkedList<>();
    /**
     * 结果映射解析 集合LinkedList，存放着构建ResultMap时抛出BuilderException异常的resultMapResolver实例
     */
    protected final Collection<ResultMapResolver> incompleteResultMaps = new LinkedList<>();
    /**
     * 方法映射解析 集合LinkedList
     */
    protected final Collection<MethodResolver> incompleteMethods = new LinkedList<>();

    /*
     * A map holds cache-ref relationship. The key is the namespace that
     * references a cache bound to another namespace and the value is the
     * namespace which the actual cache is bound to.
     */
    protected final Map<String, String> cacheRefMap = new HashMap<>();

    public Configuration(Environment environment) {
        this();
        this.environment = environment;
    }

    public Configuration() {
        //以下为默认 typeAlias(别名)
        typeAliasRegistry.registerAlias("JDBC", JdbcTransactionFactory.class);
        typeAliasRegistry.registerAlias("MANAGED", ManagedTransactionFactory.class);

        typeAliasRegistry.registerAlias("JNDI", JndiDataSourceFactory.class);
        typeAliasRegistry.registerAlias("POOLED", PooledDataSourceFactory.class);
        typeAliasRegistry.registerAlias("UNPOOLED", UnpooledDataSourceFactory.class);

        typeAliasRegistry.registerAlias("PERPETUAL", PerpetualCache.class);
        typeAliasRegistry.registerAlias("FIFO", FifoCache.class);
        typeAliasRegistry.registerAlias("LRU", LruCache.class);
        typeAliasRegistry.registerAlias("SOFT", SoftCache.class);
        typeAliasRegistry.registerAlias("WEAK", WeakCache.class);

        typeAliasRegistry.registerAlias("DB_VENDOR", VendorDatabaseIdProvider.class);

        typeAliasRegistry.registerAlias("XML", XMLLanguageDriver.class);
        typeAliasRegistry.registerAlias("RAW", RawLanguageDriver.class);

        typeAliasRegistry.registerAlias("SLF4J", Slf4jImpl.class);
        typeAliasRegistry.registerAlias("COMMONS_LOGGING", JakartaCommonsLoggingImpl.class);
        typeAliasRegistry.registerAlias("LOG4J", Log4jImpl.class);
        typeAliasRegistry.registerAlias("LOG4J2", Log4j2Impl.class);
        typeAliasRegistry.registerAlias("JDK_LOGGING", Jdk14LoggingImpl.class);
        typeAliasRegistry.registerAlias("STDOUT_LOGGING", StdOutImpl.class);
        typeAliasRegistry.registerAlias("NO_LOGGING", NoLoggingImpl.class);

        typeAliasRegistry.registerAlias("CGLIB", CglibProxyFactory.class);
        typeAliasRegistry.registerAlias("JAVASSIST", JavassistProxyFactory.class);

        languageRegistry.setDefaultDriverClass(XMLLanguageDriver.class);
        languageRegistry.register(RawLanguageDriver.class);
    }

    public String getLogPrefix() {
        return logPrefix;
    }

    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    public Class<? extends Log> getLogImpl() {
        return logImpl;
    }

    public void setLogImpl(Class<? extends Log> logImpl) {
        if (logImpl != null) {
            this.logImpl = logImpl;
            LogFactory.useCustomLogging(this.logImpl);
        }
    }

    public Class<? extends VFS> getVfsImpl() {
        return this.vfsImpl;
    }

    public void setVfsImpl(Class<? extends VFS> vfsImpl) {
        if (vfsImpl != null) {
            this.vfsImpl = vfsImpl;
            VFS.addImplClass(this.vfsImpl);
        }
    }

    public boolean isCallSettersOnNulls() {
        return callSettersOnNulls;
    }

    public void setCallSettersOnNulls(boolean callSettersOnNulls) {
        this.callSettersOnNulls = callSettersOnNulls;
    }

    public boolean isUseActualParamName() {
        return useActualParamName;
    }

    public void setUseActualParamName(boolean useActualParamName) {
        this.useActualParamName = useActualParamName;
    }

    public boolean isReturnInstanceForEmptyRow() {
        return returnInstanceForEmptyRow;
    }

    public void setReturnInstanceForEmptyRow(boolean returnEmptyInstance) {
        this.returnInstanceForEmptyRow = returnEmptyInstance;
    }

    public boolean isShrinkWhitespacesInSql() {
        return shrinkWhitespacesInSql;
    }

    public void setShrinkWhitespacesInSql(boolean shrinkWhitespacesInSql) {
        this.shrinkWhitespacesInSql = shrinkWhitespacesInSql;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public Class<?> getConfigurationFactory() {
        return configurationFactory;
    }

    public void setConfigurationFactory(Class<?> configurationFactory) {
        this.configurationFactory = configurationFactory;
    }

    public boolean isSafeResultHandlerEnabled() {
        return safeResultHandlerEnabled;
    }

    public void setSafeResultHandlerEnabled(boolean safeResultHandlerEnabled) {
        this.safeResultHandlerEnabled = safeResultHandlerEnabled;
    }

    public boolean isSafeRowBoundsEnabled() {
        return safeRowBoundsEnabled;
    }

    public void setSafeRowBoundsEnabled(boolean safeRowBoundsEnabled) {
        this.safeRowBoundsEnabled = safeRowBoundsEnabled;
    }

    public boolean isMapUnderscoreToCamelCase() {
        return mapUnderscoreToCamelCase;
    }

    public void setMapUnderscoreToCamelCase(boolean mapUnderscoreToCamelCase) {
        this.mapUnderscoreToCamelCase = mapUnderscoreToCamelCase;
    }

    public void addLoadedResource(String resource) {
        loadedResources.add(resource);
    }

    public boolean isResourceLoaded(String resource) {
        return loadedResources.contains(resource);
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public AutoMappingBehavior getAutoMappingBehavior() {
        return autoMappingBehavior;
    }

    public void setAutoMappingBehavior(AutoMappingBehavior autoMappingBehavior) {
        this.autoMappingBehavior = autoMappingBehavior;
    }

    /**
     * Gets the auto mapping unknown column behavior.
     *
     * @return the auto mapping unknown column behavior
     * @since 3.4.0
     */
    public AutoMappingUnknownColumnBehavior getAutoMappingUnknownColumnBehavior() {
        return autoMappingUnknownColumnBehavior;
    }

    /**
     * Sets the auto mapping unknown column behavior.
     *
     * @param autoMappingUnknownColumnBehavior
     *          the new auto mapping unknown column behavior
     * @since 3.4.0
     */
    public void setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior autoMappingUnknownColumnBehavior) {
        this.autoMappingUnknownColumnBehavior = autoMappingUnknownColumnBehavior;
    }

    public boolean isLazyLoadingEnabled() {
        return lazyLoadingEnabled;
    }

    public void setLazyLoadingEnabled(boolean lazyLoadingEnabled) {
        this.lazyLoadingEnabled = lazyLoadingEnabled;
    }

    public ProxyFactory getProxyFactory() {
        return proxyFactory;
    }

    public void setProxyFactory(ProxyFactory proxyFactory) {
        if (proxyFactory == null) {
            proxyFactory = new JavassistProxyFactory();
        }
        this.proxyFactory = proxyFactory;
    }

    public boolean isAggressiveLazyLoading() {
        return aggressiveLazyLoading;
    }

    public void setAggressiveLazyLoading(boolean aggressiveLazyLoading) {
        this.aggressiveLazyLoading = aggressiveLazyLoading;
    }

    public boolean isMultipleResultSetsEnabled() {
        return multipleResultSetsEnabled;
    }

    public void setMultipleResultSetsEnabled(boolean multipleResultSetsEnabled) {
        this.multipleResultSetsEnabled = multipleResultSetsEnabled;
    }

    public Set<String> getLazyLoadTriggerMethods() {
        return lazyLoadTriggerMethods;
    }

    public void setLazyLoadTriggerMethods(Set<String> lazyLoadTriggerMethods) {
        this.lazyLoadTriggerMethods = lazyLoadTriggerMethods;
    }

    public boolean isUseGeneratedKeys() {
        return useGeneratedKeys;
    }

    public void setUseGeneratedKeys(boolean useGeneratedKeys) {
        this.useGeneratedKeys = useGeneratedKeys;
    }

    public ExecutorType getDefaultExecutorType() {
        return defaultExecutorType;
    }

    public void setDefaultExecutorType(ExecutorType defaultExecutorType) {
        this.defaultExecutorType = defaultExecutorType;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public Integer getDefaultStatementTimeout() {
        return defaultStatementTimeout;
    }

    public void setDefaultStatementTimeout(Integer defaultStatementTimeout) {
        this.defaultStatementTimeout = defaultStatementTimeout;
    }

    /**
     * Gets the default fetch size.
     *
     * @return the default fetch size
     * @since 3.3.0
     */
    public Integer getDefaultFetchSize() {
        return defaultFetchSize;
    }

    /**
     * Sets the default fetch size.
     *
     * @param defaultFetchSize
     *          the new default fetch size
     * @since 3.3.0
     */
    public void setDefaultFetchSize(Integer defaultFetchSize) {
        this.defaultFetchSize = defaultFetchSize;
    }

    /**
     * Gets the default result set type.
     *
     * @return the default result set type
     * @since 3.5.2
     */
    public ResultSetType getDefaultResultSetType() {
        return defaultResultSetType;
    }

    /**
     * Sets the default result set type.
     *
     * @param defaultResultSetType
     *          the new default result set type
     * @since 3.5.2
     */
    public void setDefaultResultSetType(ResultSetType defaultResultSetType) {
        this.defaultResultSetType = defaultResultSetType;
    }

    public boolean isUseColumnLabel() {
        return useColumnLabel;
    }

    public void setUseColumnLabel(boolean useColumnLabel) {
        this.useColumnLabel = useColumnLabel;
    }

    public LocalCacheScope getLocalCacheScope() {
        return localCacheScope;
    }

    public void setLocalCacheScope(LocalCacheScope localCacheScope) {
        this.localCacheScope = localCacheScope;
    }

    public JdbcType getJdbcTypeForNull() {
        return jdbcTypeForNull;
    }

    public void setJdbcTypeForNull(JdbcType jdbcTypeForNull) {
        this.jdbcTypeForNull = jdbcTypeForNull;
    }

    public Properties getVariables() {
        return variables;
    }

    public void setVariables(Properties variables) {
        this.variables = variables;
    }

    public TypeHandlerRegistry getTypeHandlerRegistry() {
        return typeHandlerRegistry;
    }

    /**
     * Set a default {@link TypeHandler} class for {@link Enum}.
     * A default {@link TypeHandler} is {@link org.apache.ibatis.type.EnumTypeHandler}.
     * @param typeHandler a type handler class for {@link Enum}
     * @since 3.4.5
     */
    public void setDefaultEnumTypeHandler(Class<? extends TypeHandler> typeHandler) {
        if (typeHandler != null) {
            getTypeHandlerRegistry().setDefaultEnumTypeHandler(typeHandler);
        }
    }

    public TypeAliasRegistry getTypeAliasRegistry() {
        return typeAliasRegistry;
    }

    /**
     * Gets the mapper registry.
     *
     * @return the mapper registry
     * @since 3.2.2
     */
    public MapperRegistry getMapperRegistry() {
        return mapperRegistry;
    }

    public ReflectorFactory getReflectorFactory() {
        return reflectorFactory;
    }

    public void setReflectorFactory(ReflectorFactory reflectorFactory) {
        this.reflectorFactory = reflectorFactory;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public void setObjectFactory(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    public ObjectWrapperFactory getObjectWrapperFactory() {
        return objectWrapperFactory;
    }

    public void setObjectWrapperFactory(ObjectWrapperFactory objectWrapperFactory) {
        this.objectWrapperFactory = objectWrapperFactory;
    }

    /**
     * Gets the interceptors.
     *
     * @return the interceptors
     * @since 3.2.2
     */
    public List<Interceptor> getInterceptors() {
        return interceptorChain.getInterceptors();
    }

    public LanguageDriverRegistry getLanguageRegistry() {
        return languageRegistry;
    }

    public void setDefaultScriptingLanguage(Class<? extends LanguageDriver> driver) {
        if (driver == null) {
            driver = XMLLanguageDriver.class;
        }
        getLanguageRegistry().setDefaultDriverClass(driver);
    }

    public LanguageDriver getDefaultScriptingLanguageInstance() {
        return languageRegistry.getDefaultDriver();
    }

    /**
     * Gets the language driver.
     *
     * @param langClass
     *          the lang class
     * @return the language driver
     * @since 3.5.1
     */
    public LanguageDriver getLanguageDriver(Class<? extends LanguageDriver> langClass) {
        if (langClass == null) {
            return languageRegistry.getDefaultDriver();
        }
        languageRegistry.register(langClass);
        return languageRegistry.getDriver(langClass);
    }

    /**
     * Gets the default scripting lanuage instance.
     *
     * @return the default scripting lanuage instance
     * @deprecated Use {@link #getDefaultScriptingLanguageInstance()}
     */
    @Deprecated
    public LanguageDriver getDefaultScriptingLanuageInstance() {
        return getDefaultScriptingLanguageInstance();
    }

    public MetaObject newMetaObject(Object object) {
        return MetaObject.forObject(object, objectFactory, objectWrapperFactory, reflectorFactory);
    }

    public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
        ParameterHandler parameterHandler = mappedStatement.getLang().createParameterHandler(mappedStatement, parameterObject, boundSql);
        parameterHandler = (ParameterHandler) interceptorChain.pluginAll(parameterHandler);
        return parameterHandler;
    }

    public ResultSetHandler newResultSetHandler(Executor executor, MappedStatement mappedStatement,
                                                RowBounds rowBounds, ParameterHandler parameterHandler,
                                                ResultHandler resultHandler, BoundSql boundSql) {
        ResultSetHandler resultSetHandler = new DefaultResultSetHandler(executor, mappedStatement, parameterHandler,
                resultHandler, boundSql, rowBounds);
        resultSetHandler = (ResultSetHandler) interceptorChain.pluginAll(resultSetHandler);
        return resultSetHandler;
    }

    public StatementHandler newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        StatementHandler statementHandler = new RoutingStatementHandler(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);
        statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
        return statementHandler;
    }

    public Executor newExecutor(Transaction transaction) {
        return newExecutor(transaction, defaultExecutorType);
    }

    public Executor newExecutor(Transaction transaction, ExecutorType executorType) {
        executorType = executorType == null ? defaultExecutorType : executorType;
        executorType = executorType == null ? ExecutorType.SIMPLE : executorType;
        Executor executor;
        if (ExecutorType.BATCH == executorType) {
            executor = new BatchExecutor(this, transaction);
        } else if (ExecutorType.REUSE == executorType) {
            executor = new ReuseExecutor(this, transaction);
        } else {
            executor = new SimpleExecutor(this, transaction);
        }
        if (cacheEnabled) {
            executor = new CachingExecutor(executor);
        }
        executor = (Executor) interceptorChain.pluginAll(executor);
        return executor;
    }

    public void addKeyGenerator(String id, KeyGenerator keyGenerator) {
        keyGenerators.put(id, keyGenerator);
    }

    public Collection<String> getKeyGeneratorNames() {
        return keyGenerators.keySet();
    }

    public Collection<KeyGenerator> getKeyGenerators() {
        return keyGenerators.values();
    }

    public KeyGenerator getKeyGenerator(String id) {
        return keyGenerators.get(id);
    }

    public boolean hasKeyGenerator(String id) {
        return keyGenerators.containsKey(id);
    }

    public void addCache(Cache cache) {
        caches.put(cache.getId(), cache);
    }

    public Collection<String> getCacheNames() {
        return caches.keySet();
    }

    public Collection<Cache> getCaches() {
        return caches.values();
    }

    public Cache getCache(String id) {
        return caches.get(id);
    }

    public boolean hasCache(String id) {
        return caches.containsKey(id);
    }

    public void addResultMap(ResultMap rm) {
        resultMaps.put(rm.getId(), rm);
        checkLocallyForDiscriminatedNestedResultMaps(rm);
        checkGloballyForDiscriminatedNestedResultMaps(rm);
    }

    public Collection<String> getResultMapNames() {
        return resultMaps.keySet();
    }

    public Collection<ResultMap> getResultMaps() {
        return resultMaps.values();
    }

    public ResultMap getResultMap(String id) {
        return resultMaps.get(id);
    }

    public boolean hasResultMap(String id) {
        return resultMaps.containsKey(id);
    }

    public void addParameterMap(ParameterMap pm) {
        parameterMaps.put(pm.getId(), pm);
    }

    public Collection<String> getParameterMapNames() {
        return parameterMaps.keySet();
    }

    public Collection<ParameterMap> getParameterMaps() {
        return parameterMaps.values();
    }

    public ParameterMap getParameterMap(String id) {
        return parameterMaps.get(id);
    }

    public boolean hasParameterMap(String id) {
        return parameterMaps.containsKey(id);
    }

    public void addMappedStatement(MappedStatement ms) {
        mappedStatements.put(ms.getId(), ms);
    }

    public Collection<String> getMappedStatementNames() {
        buildAllStatements();
        return mappedStatements.keySet();
    }

    public Collection<MappedStatement> getMappedStatements() {
        buildAllStatements();
        return mappedStatements.values();
    }

    public Collection<XMLStatementBuilder> getIncompleteStatements() {
        return incompleteStatements;
    }

    public void addIncompleteStatement(XMLStatementBuilder incompleteStatement) {
        incompleteStatements.add(incompleteStatement);
    }

    public Collection<CacheRefResolver> getIncompleteCacheRefs() {
        return incompleteCacheRefs;
    }

    public void addIncompleteCacheRef(CacheRefResolver incompleteCacheRef) {
        incompleteCacheRefs.add(incompleteCacheRef);
    }

    public Collection<ResultMapResolver> getIncompleteResultMaps() {
        return incompleteResultMaps;
    }

    public void addIncompleteResultMap(ResultMapResolver resultMapResolver) {
        incompleteResultMaps.add(resultMapResolver);
    }

    public void addIncompleteMethod(MethodResolver builder) {
        incompleteMethods.add(builder);
    }

    public Collection<MethodResolver> getIncompleteMethods() {
        return incompleteMethods;
    }

    public MappedStatement getMappedStatement(String id) {
        return this.getMappedStatement(id, true);
    }

    public MappedStatement getMappedStatement(String id, boolean validateIncompleteStatements) {
        if (validateIncompleteStatements) {
            buildAllStatements();
        }
        return mappedStatements.get(id);
    }

    public Map<String, XNode> getSqlFragments() {
        return sqlFragments;
    }

    public void addInterceptor(Interceptor interceptor) {
        interceptorChain.addInterceptor(interceptor);
    }

    public void addMappers(String packageName, Class<?> superType) {
        mapperRegistry.addMappers(packageName, superType);
    }

    public void addMappers(String packageName) {
        mapperRegistry.addMappers(packageName);
    }

    public <T> void addMapper(Class<T> type) {
        mapperRegistry.addMapper(type);
    }

    public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
        return mapperRegistry.getMapper(type, sqlSession);
    }

    public boolean hasMapper(Class<?> type) {
        return mapperRegistry.hasMapper(type);
    }

    public boolean hasStatement(String statementName) {
        return hasStatement(statementName, true);
    }

    public boolean hasStatement(String statementName, boolean validateIncompleteStatements) {
        if (validateIncompleteStatements) {
            buildAllStatements();
        }
        return mappedStatements.containsKey(statementName);
    }

    public void addCacheRef(String namespace, String referencedNamespace) {
        cacheRefMap.put(namespace, referencedNamespace);
    }

    /*
     * Parses all the unprocessed statement nodes in the cache. It is recommended
     * to call this method once all the mappers are added as it provides fail-fast
     * statement validation.
     */
    protected void buildAllStatements() {
        parsePendingResultMaps();
        if (!incompleteCacheRefs.isEmpty()) {
            synchronized (incompleteCacheRefs) {
                incompleteCacheRefs.removeIf(x -> x.resolveCacheRef() != null);
            }
        }
        if (!incompleteStatements.isEmpty()) {
            synchronized (incompleteStatements) {
                incompleteStatements.removeIf(x -> {
                    x.parseStatementNode();
                    return true;
                });
            }
        }
        if (!incompleteMethods.isEmpty()) {
            synchronized (incompleteMethods) {
                incompleteMethods.removeIf(x -> {
                    x.resolve();
                    return true;
                });
            }
        }
    }

    private void parsePendingResultMaps() {
        if (incompleteResultMaps.isEmpty()) {
            return;
        }
        synchronized (incompleteResultMaps) {
            boolean resolved;
            IncompleteElementException ex = null;
            do {
                resolved = false;
                Iterator<ResultMapResolver> iterator = incompleteResultMaps.iterator();
                while (iterator.hasNext()) {
                    try {
                        iterator.next().resolve();
                        iterator.remove();
                        resolved = true;
                    } catch (IncompleteElementException e) {
                        ex = e;
                    }
                }
            } while (resolved);
            if (!incompleteResultMaps.isEmpty() && ex != null) {
                // At least one result map is unresolvable.
                throw ex;
            }
        }
    }

    /**
     * Extracts namespace from fully qualified statement id.
     *
     * @param statementId
     *          the statement id
     * @return namespace or null when id does not contain period.
     */
    protected String extractNamespace(String statementId) {
        int lastPeriod = statementId.lastIndexOf('.');
        return lastPeriod > 0 ? statementId.substring(0, lastPeriod) : null;
    }

    // Slow but a one time cost. A better solution is welcome.
    protected void checkGloballyForDiscriminatedNestedResultMaps(ResultMap rm) {
        if (rm.hasNestedResultMaps()) {
            for (Map.Entry<String, ResultMap> entry : resultMaps.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof ResultMap) {
                    ResultMap entryResultMap = (ResultMap) value;
                    if (!entryResultMap.hasNestedResultMaps() && entryResultMap.getDiscriminator() != null) {
                        Collection<String> discriminatedResultMapNames = entryResultMap.getDiscriminator().getDiscriminatorMap().values();
                        if (discriminatedResultMapNames.contains(rm.getId())) {
                            entryResultMap.forceNestedResultMaps();
                        }
                    }
                }
            }
        }
    }

    // Slow but a one time cost. A better solution is welcome.
    protected void checkLocallyForDiscriminatedNestedResultMaps(ResultMap rm) {
        if (!rm.hasNestedResultMaps() && rm.getDiscriminator() != null) {
            for (Map.Entry<String, String> entry : rm.getDiscriminator().getDiscriminatorMap().entrySet()) {
                String discriminatedResultMapName = entry.getValue();
                if (hasResultMap(discriminatedResultMapName)) {
                    ResultMap discriminatedResultMap = resultMaps.get(discriminatedResultMapName);
                    if (discriminatedResultMap.hasNestedResultMaps()) {
                        rm.forceNestedResultMaps();
                        break;
                    }
                }
            }
        }
    }

    protected static class StrictMap<V> extends HashMap<String, V> {

        private static final long serialVersionUID = -4950446264854982944L;
        private final String name;
        private BiFunction<V, V, String> conflictMessageProducer;

        public StrictMap(String name, int initialCapacity, float loadFactor) {
            super(initialCapacity, loadFactor);
            this.name = name;
        }

        public StrictMap(String name, int initialCapacity) {
            super(initialCapacity);
            this.name = name;
        }

        public StrictMap(String name) {
            super();
            this.name = name;
        }

        public StrictMap(String name, Map<String, ? extends V> m) {
            super(m);
            this.name = name;
        }

        /**
         * Assign a function for producing a conflict error message when contains value with the same key.
         * <p>
         * function arguments are 1st is saved value and 2nd is target value.
         * @param conflictMessageProducer A function for producing a conflict error message
         * @return a conflict error message
         * @since 3.5.0
         */
        public StrictMap<V> conflictMessageProducer(BiFunction<V, V, String> conflictMessageProducer) {
            this.conflictMessageProducer = conflictMessageProducer;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V put(String key, V value) {
            if (containsKey(key)) {
                throw new IllegalArgumentException(name + " already contains value for " + key
                        + (conflictMessageProducer == null ? "" : conflictMessageProducer.apply(super.get(key), value)));
            }
            if (key.contains(".")) {
                final String shortKey = getShortName(key);
                if (super.get(shortKey) == null) {
                    super.put(shortKey, value);
                } else {
                    super.put(shortKey, (V) new Ambiguity(shortKey));
                }
            }
            return super.put(key, value);
        }

        @Override
        public V get(Object key) {
            V value = super.get(key);
            if (value == null) {
                throw new IllegalArgumentException(name + " does not contain value for " + key);
            }
            if (value instanceof Ambiguity) {
                throw new IllegalArgumentException(((Ambiguity) value).getSubject() + " is ambiguous in " + name
                        + " (try using the full name including the namespace, or rename one of the entries)");
            }
            return value;
        }

        protected static class Ambiguity {
            private final String subject;

            public Ambiguity(String subject) {
                this.subject = subject;
            }

            public String getSubject() {
                return subject;
            }
        }

        private String getShortName(String key) {
            final String[] keyParts = key.split("\\.");
            return keyParts[keyParts.length - 1];
        }
    }

}
