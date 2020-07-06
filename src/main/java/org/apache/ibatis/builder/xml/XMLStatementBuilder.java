/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
public class XMLStatementBuilder extends BaseBuilder {

  /**
   * 构建映射器助理
   */
  private final MapperBuilderAssistant builderAssistant;
  /**
   * select|insert|update|delete 标签，下面简称DML标签
   */
  private final XNode context;
  /**
   * 当前项目引用的dataBaseId
   */
  private final String requiredDatabaseId;

  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }
/**
 *
 * @param configuration mybatis全局配置信息
 * @param builderAssistant 构建映射器助理
 * @param context  select|insert|update|delete 标签
 * @param databaseId 当前项目引用的dataBaseId
 */
  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
    super(configuration);
    this.builderAssistant = builderAssistant;
    this.context = context;
    this.requiredDatabaseId = databaseId;
  }
/**
 * 解析DML标签
 * <p>将DML标签中的include标签替换成对应sql标签下的所有子标签</p>
 * <p>将DML标签中的selectKey标签封装成KeyGenerator对象，然后添加到Mybatis全局配置信息信息，然后删除DML标签里的所有selectKey标签</p>
 * <p>将DML标签，封装成MapperStatement对象，然后添加到Mybatis全局配置信息中</p>
 */
  public void parseStatementNode() {
    //获取DML标签的id属性
    String id = context.getStringAttribute("id");
    //获取DML标签的databaseId属性
    String databaseId = context.getStringAttribute("databaseId");
    //如果当前DML标签不对应当前配置的数据库Id
    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      //结束方法
      return;
    }
    //获取DML标签名：select|insert|update|delete 标签
    String nodeName = context.getNode().getNodeName();
    //获取对应的DML标签名的SQLCommentType枚举实例
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    //只有不是select语句，isSelect才会为true
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    //获取DML标签的flushCache属性，flushCache表示执行DML语句时是否刷新缓存，默认是只要不是select语句就会刷新缓存
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    //获取DML标签的flushCache属性，useCache表示是否对该语句进行二级缓存，默认是对select语句进行缓存。
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    /**
     * 这个设置仅针对嵌套结果 select 语句适用：如果为 true，就是假设包含了嵌套结果集或是分组了，
     * 这样的话当返回一个主结果行的时候，就不会发生有对前面结果集的引用的情况。
     * 这就使得在获取嵌套的结果集的时候不至于导致内存不够用。默认值：false。
     */
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    // Include Fragments before parsing
    /**
     * 处理include标签。其实就是将include标签替换成对应sql标签下的所有子标签
     *
     * 根据include标签的refid属性值去找到存在mybatis全局配置里的对应的SQL标签，
     * 如果sql标签和include标签不在同一个mapper.xml中，会将sql标签加入到与include标签对应的
     * mapper.xml中，然后将include标签替换成sql标签,再将sql标签下的所有子节点（sql标签下面的文本也是子节点的一部分）
     * 全部复制插入到sql标签的父标签下，又在sql标签前面的位置。这里的sql父标签其实就是DML标签，
     * 也就是这里context。最后将sql标签删除。
     *
     * 在覆盖过程中，还会对include标签下的property标签传递给sql标签的'${...}'参数，并更改掉
     */
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    includeParser.applyIncludes(context.getNode());

    /**
     * 获取DML标签的parameterType属性。
     * parmeterType:将要传入语句的参数的完全限定类名或别名。这个属性是可选的，
     * 因为 MyBatis 可以通过 TypeHandler 推断出具体传入语句的参数，默认值为 unset。
     */
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);

    /**
     * 获取DML标签的lang属性:
     * lang:指定该DML标签使用的语言驱动
     * 语言驱动：MyBatis 从 3.2 开始支持可插拔脚本语言，这允许你插入一种脚本语言驱动，
     * 并基于这种语言来编写动态 SQL 查询语句
     */
    String lang = context.getStringAttribute("lang");
    LanguageDriver langDriver = getLanguageDriver(lang);

    // Parse selectKey after includes and remove them.
    /**
     * 获取DML标签中的所有对应配置的当前项目引用的databaseId的SelectKey标签，获取没有配置databaseId的SelectKey标签，
     * 遍历selectKey标签集合，将selectKey标签封装成KeyGenerator，然后添加到Mybatis全局配置信息中，
     * 然后删除DML标签中的所有selectKey标签
     */
    processSelectKeyNodes(id, parameterTypeClass, langDriver);

    // Parse the SQL (pre: <selectKey> and <include> were parsed and removed)
    KeyGenerator keyGenerator;
    //拼装selectKey标签的Id,selectKey标签的Id=DML标签ID+!selectKey
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    //检查ID是否简写，简写就应用当前命名空间
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    //如果Mybatis全局配置信息中有keyStatementId对应的KeyGenerator
    if (configuration.hasKeyGenerator(keyStatementId)) {
      //获取keyGenerator
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      //如果DML标签配置了useGeneratorKeys为true，就使用JDBC3KeyGenertor作为keyGenerator;如果为false,就是用NoKeyGenerator
      //默认情况下，配置了MyBatis全局配置信息中的useGenerateKey为true时，只要是SQL是Insert类型的，useGeneratorKeys都会为true.
      /**
       * Jdbc3KeyGenerator:主要用于数据库的自增主键，比如 MySQL、PostgreSQL；会将执行SQL后从Statemenet中获取主键放到参数对象对应的属性里
       * NoKeyGenerator: 什么事情都不干，里面是空实现方法
       */
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }
    //创建DML标签对应的动态SQL
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    //获取StatementType，默认情况下是StatementType.PREPARED
    /**
     * StatementType.STATEMENT:对应于Statement对象，有SQL注入的风险
     * StatementType.PREPARED: PreparedStatement，预编译处理,默认
     * StatementType.CALLABLE:CallableStatement一般调用存储过程的时候使用
     */
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    //fetchSize:尝试让驱动程序每次批量返回的结果行数和这个设置值相等。 默认值为未设置（unset）（依赖驱动）。
    Integer fetchSize = context.getIntAttribute("fetchSize");
    //timeout:这个设置是在抛出异常之前，驱动程序等待数据库返回请求结果的秒数。默认值为未设置（unset）（依赖驱动）。
    Integer timeout = context.getIntAttribute("timeout");
    //获取参数映射ID,对应于parameterMap标签
    String parameterMap = context.getStringAttribute("parameterMap");
    /**
     * 获取结果的类型。MyBatis 通常可以推断出来，但是为了更加精确，写上也不会有什么问题。
     * MyBatis 允许将任何简单类型用作主键的类型，包括字符串。如果希望作用于多个生成的列，则可以使用一个包含期望属性的 Object 或一个 Map。
     */
    String resultType = context.getStringAttribute("resultType");
    //通过类别名注册器typeAliasRegistry解析出对应的类 实际调用{@link #resolveAlias(String)}，返回类实例
    Class<?> resultTypeClass = resolveClass(resultType);
    // 获取结果映射Id，对应于resultMap标签
    String resultMap = context.getStringAttribute("resultMap");
    /**
     * ResultSetType.DEFAULT:依赖驱动,默认
     * ResultSetType.FORWARD_ONLY:结果集的游标只能向下滚动
     * ResultSetType.SCROLL_INSENSITIVE:结果集的游标可以上下移动，当数据库变化时，当前结果集不变
     * ResultSetType.SCROLL_SENSITIVE:返回可滚动的结果集，当数据库变化时，当前结果集同步改变
     */
    String resultSetType = context.getStringAttribute("resultSetType");
    //将resultSetType转换成ResultSetType枚举
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);
    /**
     * （仅对 insert 和 update 有用）唯一标记一个属性，MyBatis 会通过
     * getGeneratedKeys 的返回值或者通过 insert 语句的 selectKey 子元素设置它的键值，
     * 默认值：未设置（unset）。如果希望得到多个生成的列，也可以是逗号分隔的属性名称列表。
     */
    String keyProperty = context.getStringAttribute("keyProperty");
    /**
     * （仅对 insert 和 update 有用）通过生成的键值设置表中的列名，这个设置仅在某些数据库
     * （像 PostgreSQL）是必须的，当主键列不是表中的第一列的时候需要设置。
     * 如果希望使用多个生成的列，也可以设置为逗号分隔的属性名称列表。
     */
    String keyColumn = context.getStringAttribute("keyColumn");
    /**
     * 这个设置仅对多结果集的情况适用。它将列出语句执行后返回的结果集并给每个结果集一个名称，
     * 名称是逗号分隔的。
     */
    String resultSets = context.getStringAttribute("resultSets");
    // 构建MapperStatement对象，并添加到全局配置信息中
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
      fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
      resultSetTypeEnum, flushCache, useCache, resultOrdered,
      keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }

  /**
   * 获取DML标签中的所有对应配置的当前项目引用的databaseId的SelectKey标签，获取没有配置databaseId的SelectKey标签，
   * 遍历selectKey标签集合，将selectKey标签封装成KeyGenerator，然后添加到Mybatis全局配置信息中。
   * 然后删除DML标签中的所有selectKey标签
   * @param id DML标签的id属性
   * @param parameterTypeClass DML标签的parameterType属性
   * @param langDriver 语言驱动
   */
  private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    //获取所有在DML标签里的selectKey标签
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");
    //如果配置的当前项目引用的databaseId
    if (configuration.getDatabaseId() != null) {
      /**
       * 解析多个selectKey标签，遍历selectKey标签集合，将selectKey标签封装成KeyGenerator，然后
       * 添加到Mybatis全局配置信息中
       */
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    /**
     * 解析多个selectKey标签，遍历selectKey标签集合，将selectKey标签封装成KeyGenerator，然后
     * 添加到Mybatis全局配置信息中
     */
    //MyBatis认为没有配置dataBaseId的SelectKey标签，默认是对应当前项目引用的databaseId
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);
    //移除DML标签里的所有SelectKey标签
    removeSelectKeyNodes(selectKeyNodes);
  }

  /**
   * 解析多个selectKey标签，遍历selectKey标签集合，将selectKey标签封装成KeyGenerator，然后
   * 添加到Mybatis全局配置信息中
   * @param parentId DML标签的id属性值
   * @param list selectKey标签集合
   * @param parameterTypeClass DML标签的parameterType
   * @param langDriver 语言驱动
   * @param skRequiredDatabaseId 当前项目应用的dataBaseId
   */
  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
    for (XNode nodeToHandle : list) {
      //id = DML标签的ID属性值+!selectKey
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      //获取selectKey标签的databaseId属性
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      //判断selectKey标签是属于当前项目引用的dataBaseId
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        //解析单个selectKey标签,将selectKey标签封装成KeyGenerator,然后添加到Mybatis全局配置信息中
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  /**
   * 解析单个selectKey标签,将selectKey标签封装成KeyGenerator,然后添加到Mybatis全局配置信息中
   * @param id DML标签的ID属性值+!selectKey
   * @param nodeToHandle selectKey标签
   * @param parameterTypeClass DML标签的parameterType
   * @param langDriver 语言驱动
   * @param databaseId selectKey标签的dataBaseId，也是 当前项目应用的dataBaseId
   */
  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {
    //获取selectKey标签的resultType属性
    String resultType = nodeToHandle.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    /**
     * 获取selectKey标签的statementType属性,默认是StatementType.PREPARED
     * StatementType:STATEMENT(对应于Statement对象，有SQL注入的风险);PreparedStatement(预编译处理);
     *  CallableStatement(一般调用存储过程的时候使用)
     */
    StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    // 获取selectKey标签的keyProperty属性。keyProperty：对应于parameterTypeClass的属性
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    // 获取selectKey标签的keyColumn属性。keyColumn：对应于表中的的列名
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");
    //获取selectKey标签的order属性。order有两个值：BEFORE:表示在执行SQL之前执行，AFTER:表示执行SQL之后执行
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    //defaults 下面代码为默认配置
    //不使用缓存
    boolean useCache = false;
    /**
     * <p>
     * 如果为 true，就是假设包含了嵌套结果集或是分组了，这样的话当返回一个主结果行的时候，
     * 就不会发生有对前面结果集的引用的情况。这就使得在获取嵌套的结果集的时候不至于导致内存不够用。
     * 默认值：false。
     * </p>
     * <p>
     *     参考博客：<a href='https://blog.csdn.net/isea533/article/details/51533296?utm_source=blogxgwz9'>https://blog.csdn.net/isea533/article/details/51533296?utm_source=blogxgwz9</a>
     * </p>
     */
    boolean resultOrdered = false;
    //不会生成任何Key
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    /**
     * <p>
     *     这是尝试影响驱动程序每次批量返回的结果行数和这个设置值相等。默认值为 unset（依赖驱动）。
     * </p>
     * <br/>
     * <p>
     *     假设fetchSize为5，查询总记录数为100，每批5次返回给你，最后结果还是总记录数，只是提高点查询的速度而已
     * </p>
     * <br/>
     * <p>
     *     MySQL不支持fetchSize，默认为一次性取出所有数据。所以容易导致OOM，
     *     如果是Oracle的话就是默认取出fetchSize条数据。
     *     裸露JDBC防止OOM可以调用statement的enableStreamingResults方法,
     *     MyBatis应该在&lt;select fetchSize="-2147483648"&gt;。
     * </p>
     */
    Integer fetchSize = null;
    //超时时间
    Integer timeout = null;
    //不刷新缓存
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;
    //动态SQL源
    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
    //selectKey的SQL一定要是SELECT类型的
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;
    //构建MapperStatement对象，并添加到全局配置信息中
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null);

    id = builderAssistant.applyCurrentNamespace(id, false);
    //因为已经添加到全局配置信息中，所以可以直接获取，keyStatement就是上面的代码构建出来的MappedStatement对象
    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    //新建一个SelectKeyGenerator对象，然后添加到Mybatis全局配置信息中
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  /**
   * 移除在DML标签中 {@code selectKeyNodes} 中的所有selectKey标签
   * @param selectKeyNodes selectKey标签集合
   */
  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    for (XNode nodeToHandle : selectKeyNodes) {
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }

  /**
   * 判断DML标签是属于当前项目引用的dataBaseId
   * @param id DML标签的ID属性
   * @param databaseId DML标签的databBaseId属性
   * @param requiredDatabaseId 当前项目引用的dataBaseId
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    //当前项目引用的dataBaseId是否等于当前项目引用的dataBaseId
    if (requiredDatabaseId != null) {
      return requiredDatabaseId.equals(databaseId);
    }
    /**
     * 到这里requiredDatabaseId就为null，也因为不设置databaseId默认就是对应当前项目引用的dataBaseId，
     * 所以就算dataBaseId有设置，当前项目引用的dataBaseId没有设置，
     * 就相当于一个有值的字符串判断一个空字符串一样，所以认为是不匹配的。
     */
    if (databaseId != null) {
      return false;
    }
    //检查ID是否简写，简写就应用当前命名空间；
    id = builderAssistant.applyCurrentNamespace(id, false);
    /**
     * 没有加入configuration的mappedStatments，统一返回true。因为到这里databaseId就是为null的情况，
     * mybati对不设置databaseId默认认为就是对应当前项目引用的dataBaseId，就相当于
     * 一个空字符串判断一个空字符串一样，所以认为是匹配的。
     */
    if (!this.configuration.hasStatement(id, false)) {
      return true;
    }
    /**
     * 下面代码触发条件是：databaseId为null，使得databaseId不等于requiredDatabaseId，id已经存在
     * configuration的mappedStatments中。
     * 如果我没有推测错误的话，mybatis认为没有设置databaseId的DML标签，是不够明确这个DML标签就是
     * 对应于当前项目引用的dataBaseId，所以当出现多个相同id的DML标签时，mybatis会以最后一个作为真正
     * 对应于当前项目引用的dataBaseId的DML标签，所以这里如果上一个DML标签没有设置databaseId话，
     * 会覆盖掉上一个DML标签。
     */
    // skip this statement if there is a previous one with a not null databaseId
    MappedStatement previous = this.configuration.getMappedStatement(id, false); // issue #2
    return previous.getDatabaseId() == null;
  }
  /**
   * 获取语言驱动
   * @param lang 别名或者包+类名
   * @return 当 {@code lang} 为null，会获取默认的语言驱动实例
   */
  private LanguageDriver getLanguageDriver(String lang) {
    Class<? extends LanguageDriver> langClass = null;
    if (lang != null) {
      langClass = resolveClass(lang);
    }
    return configuration.getLanguageDriver(langClass);
  }

}
