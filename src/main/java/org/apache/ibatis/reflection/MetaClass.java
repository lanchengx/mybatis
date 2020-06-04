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
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
public class MetaClass {

  //用于缓存Reflector对象
  private final ReflectorFactory reflectorFactory;
  //在创建MetaClasyos时会指定一个类，该Reflector对象会用于记录该类相关的元数据
    private final Reflector reflector;

  //MetaClass的构造方法是使用private修饰的
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    //创建Reflector对象
    this.reflector = reflectorFactory.findForClass(type);
  }

  //使用静态方法创建MetaClass对象
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  public MetaClass metaClassForProperty(String name) {
    //查找指定属性对应的class
    Class<?> propType = reflector.getGetterType(name);
    //为改属性创建对应的MetaClass对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  public String findProperty(String name) {
    //委托给buildProperty()方法实现
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Class<?> getGetterType(PropertyTokenizer prop) {
    //获取属性类型
    Class<?> type = reflector.getGetterType(prop.getName());
    //该表达式中是否试用"[]"指定了下标，切是Collect子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
      //通过TypeParameterResolver工具类解析属性的类型
      Type returnType = getGenericGetterType(prop.getName());
      //针对ParameterizedType进行处理，既针对泛型类型进行处理
      if (returnType instanceof ParameterizedType) {
        //获取实际的类型参数
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          //泛型的类型
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }

  private Type getGenericGetterType(String propertyName) {
    try {
      //根据Reflector.getMethods集合中记录的invoker实现类的类型，决定解析getter方法返回值类型还是解析字段类型
      Invoker invoker = reflector.getGetInvoker(propertyName);
      if (invoker instanceof MethodInvoker) {
        Field declaredMethod = MethodInvoker.class.getDeclaredField("method");
        declaredMethod.setAccessible(true);
        Method method = (Method) declaredMethod.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        Field declaredField = GetFieldInvoker.class.getDeclaredField("field");
        declaredField.setAccessible(true);
        Field field = (Field) declaredField.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException | IllegalAccessException e) {
      // Ignored
    }
    return null;
  }

  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

  public boolean hasGetter(String name) {
    //解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //存在待处理的子表达式
    if (prop.hasNext()) {
      //PropertyTokenizer.name指定的属性有getter方法 才能处理子表达式
      if (reflector.hasGetter(prop.getName())) {
        //metaClassForProperty(PropertyTokenizer)是上面metaClassForProperty(String)的重载 但是逻辑有很大差异
        MetaClass metaProp = metaClassForProperty(prop);
        //递归入口
        return metaProp.hasGetter(prop.getChildren());
      } else {
        //递归出口
        return false;
      }
    } else {
      //递归出口
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  private StringBuilder buildProperty(String name, StringBuilder builder) {
    //解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //是否有子表达式
    if (prop.hasNext()) {
      //查找PropertyTokenizer.name对应的属性
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        //追加属性名
        builder.append(propertyName);
        builder.append(".");
        //为该属性创建MataClass对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        //递归解析PropertyTokenizer.children字段，并将解析结果添加到builder中保存
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      //递归出口
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
