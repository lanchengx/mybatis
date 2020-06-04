/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.datasource.unpooled;

import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.datasource.DataSourceException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * @author Clinton Begin
 */
public class UnpooledDataSourceFactory implements DataSourceFactory {

  // 数据库驱动前缀
  private static final String DRIVER_PROPERTY_PREFIX = "driver.";
  private static final int DRIVER_PROPERTY_PREFIX_LENGTH = DRIVER_PROPERTY_PREFIX.length();

  // 对应的数据源，即 UnpooledDataSource
  protected DataSource dataSource;

  public UnpooledDataSourceFactory() {
    this.dataSource = new UnpooledDataSource();
  }

  /**
   * 对数据源 UnpooledDataSource 进行配置
   * @param properties
   */
  @Override
  public void setProperties(Properties properties) {
    Properties driverProperties = new Properties();
    // 创建 DataSource 相应的 MetaObject
    MetaObject metaDataSource = SystemMetaObject.forObject(dataSource);
    // 遍历 properties 集合，该集合中存放了数据源需要的信息
    for (Object key : properties.keySet()) {
      String propertyName = (String) key;
      // 以 "driver." 开头的配置项是对 DataSource 的配置，记录到 driverProperties  中
      if (propertyName.startsWith(DRIVER_PROPERTY_PREFIX)) {
        String value = properties.getProperty(propertyName);
        driverProperties.setProperty(propertyName.substring(DRIVER_PROPERTY_PREFIX_LENGTH), value);
      // 该属性是否有 set 方法
      } else if (metaDataSource.hasSetter(propertyName)) {
        // 获取对应的属性值
        String value = (String) properties.get(propertyName);
        // 根据属性类型进行类型的转换，主要是 Integer, Long, Boolean 三种类型的转换
        Object convertedValue = convertValue(metaDataSource, propertyName, value);
        // 设置DataSource 的相关属性值
        metaDataSource.setValue(propertyName, convertedValue);
      } else {
        throw new DataSourceException("Unknown DataSource property: " + propertyName);
      }
    }
    if (driverProperties.size() > 0) {
      // 设置 DataSource.driverProerties 属性值
      metaDataSource.setValue("driverProperties", driverProperties);
    }
  }

  /**
   * 返回数据源
   * @return
   */
  @Override
  public DataSource getDataSource() {
    return dataSource;
  }

  /**
   *   类型转
   * @param metaDataSource
   * @param propertyName
   * @param value
   * @return
   */
  private Object convertValue(MetaObject metaDataSource, String propertyName, String value) {
    Object convertedValue = value;
    Class<?> targetType = metaDataSource.getSetterType(propertyName);
    if (targetType == Integer.class || targetType == int.class) {
      convertedValue = Integer.valueOf(value);
    } else if (targetType == Long.class || targetType == long.class) {
      convertedValue = Long.valueOf(value);
    } else if (targetType == Boolean.class || targetType == boolean.class) {
      convertedValue = Boolean.valueOf(value);
    }
    return convertedValue;
  }

}
