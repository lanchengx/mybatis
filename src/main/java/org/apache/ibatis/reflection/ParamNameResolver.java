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
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * 构造器会将Method中所有参数级别的注解全部解析出来方法有序参数集中,names中存储形式为<参数下标，参数名>,
 * 如果在注解上设置了参数名,则会直接获取注解的value值,如果没有使用@Param注解,则使用真实的参数名,
 * 注意，真实参数名其实是arg0，arg1....的形式展现的,在判断真实参数名时,Mybatis会检查JDK版本是否包含java.lang.reflect.Parameter类,不存在该类的化会抛出ClassNotFoundException异常。
 *
 * 完成初始化后,就可以调用getNamedParams(args)方法了,该方法使用了类中的names属性,
 * 从有序集合中取出所有的<参数索引,参数名>键值对>,随后填充到另外一个集合中,以<参数名,参数下标索引,>形式呈现,同时会保留一份<param+下标索引,参数下标>的键值对。
 */
public class ParamNameResolver {

    //属性前缀
    public static final String GENERIC_NAME_PREFIX = "param";


    private final boolean useActualParamName;

    /**
     * <p>
     * The key is the index and the value is the name of the parameter.<br />
     * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
     * the parameter index is used. Note that this index could be different from the actual index
     * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
     * 参数集合names
     * </p>
     * <ul>
     * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
     * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
     * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
     * </ul>
     */
    private final SortedMap<Integer, String> names;

    private boolean hasParamAnnotation;

    public ParamNameResolver(Configuration config, Method method) {
        this.useActualParamName = config.isUseActualParamName();
        //获取所有参数类型
        final Class<?>[] paramTypes = method.getParameterTypes();
        //获取方法中的所有注解
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();
        final SortedMap<Integer, String> map = new TreeMap<>();
        //判断注解数组长度
        int paramCount = paramAnnotations.length;
        // get names from @Param annotations
        for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
            if (isSpecialParameter(paramTypes[paramIndex])) {
                // skip special parameters
                continue;
            }
            String name = null;
            for (Annotation annotation : paramAnnotations[paramIndex]) {
                //判断是否是Param标签的子类,也就是说@param中是否存在value值
                if (annotation instanceof Param) {
                    hasParamAnnotation = true;
                    //获取标签中的value值
                    name = ((Param) annotation).value();
                    break;
                }
            }
            if (name == null) {
                // @Param was not specified.
                //是否使用了真实值,也就是说没有设置value值
                if (useActualParamName) {
                    //获取方法中参数的名字
                    name = getActualParamName(method, paramIndex);
                }
                if (name == null) {
                    // use the parameter index as the name ("0", "1", ...)
                    // gcode issue #71
                    name = String.valueOf(map.size());
                }
            }
            map.put(paramIndex, name);
        }
        //设置所有参数名为不可修改的集合
        names = Collections.unmodifiableSortedMap(map);
    }

    private String getActualParamName(Method method, int paramIndex) {
        return ParamNameUtil.getParamNames(method).get(paramIndex);
    }

    private static boolean isSpecialParameter(Class<?> clazz) {
        return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
    }

    /**
     * Returns parameter names referenced by SQL providers.
     *
     * @return the names
     */
    public String[] getNames() {
        return names.values().toArray(new String[0]);
    }

    /**
     * <p>
     * A single non-special parameter is returned without a name.
     * Multiple parameters are named using the naming rule.
     * In addition to the default names, this method also adds the generic names (param1, param2,
     * ...).
     * </p>
     *
     * @param args
     *          the args
     * @return the named params
     */
    /**
     * 创建SqlSession对象需要传递的参数逻辑
     * args是用户mapper所传递的方法参数列表， 如果方法没有参数，则返回null.
     * 如果方法只包含一个参数并且不包含命名参数， 则返回传递的参数值。
     * 如果包含多个参数或包含命名参数，则返回包含名字和对应值的map对象、
     */
    public Object getNamedParams(Object[] args) {
        //判断参数个数
        final int paramCount = names.size();
        if (args == null || paramCount == 0) {
            return null;
        //返回首个参数
        } else if (!hasParamAnnotation && paramCount == 1) {
            Object value = args[names.firstKey()];
            return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
        } else {
            final Map<String, Object> param = new ParamMap<>();
            int i = 0;
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                //设置参数的值和键名
                param.put(entry.getValue(), args[entry.getKey()]);
                // add generic param names (param1, param2, ...)
                //增加参数名
                final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
                // ensure not to overwrite parameter named with @Param
                if (!names.containsValue(genericParamName)) {
                    param.put(genericParamName, args[entry.getKey()]);
                }
                i++;
            }
            return param;
        }
    }

    /**
     * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
     *
     * @param object a parameter object
     * @param actualParamName an actual parameter name
     *                        (If specify a name, set an object to {@link ParamMap} with specified name)
     * @return a {@link ParamMap}
     * @since 3.5.5
     */
    public static Object wrapToMapIfCollection(Object object, String actualParamName) {
        if (object instanceof Collection) {
            ParamMap<Object> map = new ParamMap<>();
            map.put("collection", object);
            if (object instanceof List) {
                map.put("list", object);
            }
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        } else if (object != null && object.getClass().isArray()) {
            ParamMap<Object> map = new ParamMap<>();
            map.put("array", object);
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        }
        return object;
    }

}
