/**
 * Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.parsing;

import java.util.Properties;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 * 实例中配置的数据库用户名为${usemame:root｝，其中:是占位符和 默认值的分隔符。 PropertyParser 会在解析后使用
 * usemame 在 variables 集合中查找相应的值，如果查找不到，则使用 root 作为数据库用户名的默认值 。
 */
public class PropertyParser {

    private static final String KEY_PREFIX = "org.apache.ibatis.parsing.PropertyParser.";
    /**
     * The special property key that indicate whether enable a default value on placeholder.
     * <p>
     *   The default value is {@code false} (indicate disable a default value on placeholder)
     *   If you specify the {@code true}, you can specify key and default value on placeholder (e.g. {@code ${db.username:postgres}}).
     * </p>
     * @since 3.4.2
     */
    //在 mybatis-config.xml 中 ＜properties ＞节点下自己置是否开启默认值功能的对应配置项
    public static final String KEY_ENABLE_DEFAULT_VALUE = KEY_PREFIX + "enable-default-value";

    /**
     * The special property key that specify a separator for key and default value on placeholder.
     * <p>
     *   The default separator is {@code ":"}.
     * </p>
     * @since 3.4.2
     */
    //配置 占位符与默认值之间的默认分隔符的对应配置项
    public static final String KEY_DEFAULT_VALUE_SEPARATOR = KEY_PREFIX + "default-value-separator";

    //默认情况下，关闭默认值的功能
    private static final String ENABLE_DEFAULT_VALUE = "false";
    //默认分隔符
    private static final String DEFAULT_VALUE_SEPARATOR = ":";

    private PropertyParser() {
        // Prevent Instantiation
    }

    public static String parse(String string, Properties variables) {
        VariableTokenHandler handler = new VariableTokenHandler(variables);
        //创建 GenericTokenParser对象,并指定其处理的占位符格式为${}
        GenericTokenParser parser = new GenericTokenParser("${", "}", handler);
        return parser.parse(string);
    }

    private static class VariableTokenHandler implements TokenHandler {
        //<properties ＞节点下定义的键位对，用于替换占位符
        private final Properties variables;
        //是否支持占位符中使用默认值的功能
        private final boolean enableDefaultValue;
        //指定占位符和默认值之间的分隔符
        private final String defaultValueSeparator;

        private VariableTokenHandler(Properties variables) {
            this.variables = variables;
            this.enableDefaultValue = Boolean.parseBoolean(getPropertyValue(KEY_ENABLE_DEFAULT_VALUE, ENABLE_DEFAULT_VALUE));
            this.defaultValueSeparator = getPropertyValue(KEY_DEFAULT_VALUE_SEPARATOR, DEFAULT_VALUE_SEPARATOR);
        }

        private String getPropertyValue(String key, String defaultValue) {
            return (variables == null) ? defaultValue : variables.getProperty(key, defaultValue);
        }

        @Override
        public String handleToken(String content) {
            if (variables != null) {
                String key = content;
                //检测是否支持占位符中使用默认佳的功能
                if (enableDefaultValue) {
                    final int separatorIndex = content.indexOf(defaultValueSeparator);
                    String defaultValue = null;
                    if (separatorIndex >= 0) {
                        //获取占位符名称
                        key = content.substring(0, separatorIndex);
                        //获取默认值
                        defaultValue = content.substring(separatorIndex + defaultValueSeparator.length());
                    }
                    if (defaultValue != null) {
                        //查找指定占位符
                        return variables.getProperty(key, defaultValue);
                    }
                }
                //如果不支持默认值,直接查询
                if (variables.containsKey(key)) {
                    return variables.getProperty(key);
                }
            }
            //如果variables为空，拼接
            return "${" + content + "}";
        }
    }

}
