/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultParameterHandler implements ParameterHandler {

  //typeHandler注册中心
  private final TypeHandlerRegistry typeHandlerRegistry;

  //对应的sql节点的信息
  private final MappedStatement mappedStatement;
  //用户传入的参数
  private final Object parameterObject;
  //SQL语句信息，其中还包括占位符和参数名称信息
  private final BoundSql boundSql;
  private final Configuration configuration;

  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  @Override
  public void setParameters(PreparedStatement ps) {
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
    //从boundSql中获取sql语句的占位符对应的参数信息
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    //遍历这个参数列表，把参数设置到PreparedStatement中
    if (parameterMappings != null) {
      for (int i = 0; i < parameterMappings.size(); i++) {
        //获得入参的形参名字，javatype，jdbctype等信息
        ParameterMapping parameterMapping = parameterMappings.get(i);
        //对于存储过程中的参数不处理
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          //绑定的实参值
          Object value;
          //参数的名字
          String propertyName = parameterMapping.getProperty();
          // 获取对应的实参值
          //是否为附加参数
          if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
            value = boundSql.getAdditionalParameter(propertyName);
          //是否为空
          } else if (parameterObject == null) {//是否为空
            value = null;
          //是否要进行指定的类型转换
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {            value = parameterObject;
          } else {//大部分情况走这段
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }
          //从parameterMapping中获取typeHandler对象
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          //获取参数对应的jdbcType
          JdbcType jdbcType = parameterMapping.getJdbcType();
          if (value == null && jdbcType == null) {
            jdbcType = configuration.getJdbcTypeForNull();
          }
          try {
            //为statment中的占位符绑定参数
            typeHandler.setParameter(ps, i + 1, value, jdbcType);
          } catch (TypeException | SQLException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          }
        }
      }
    }
  }

}
