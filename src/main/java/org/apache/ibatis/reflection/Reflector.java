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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {
  //对应的class类型
  private final Class<?> type;
  //可读属性的名称集合，可读属性就是存在相应getter方法的属性
  private final String[] readablePropertyNames;
  //可写属性的名称集合，可写属性就是存在相应setter方法的属性
  private final String[] writablePropertyNames;

  // Method 对象的封装
  //记录了属性相应的setter方法，key是属性名称，value 是Invoker对象，它是对setter方法对应
  private final Map<String, Invoker> setMethods = new HashMap<>();
  //属性相应的getter方法集合，key 是属性名称，value 也是Invoker对象
  private final Map<String, Invoker> getMethods = new HashMap<>();
  //记录了属性相应的setter方法的参数值类型，key是属性名称，value是setter方法的参数类型
  private final Map<String, Class<?>> setTypes = new HashMap<>();
  //记录了属性相应的getter方法的参数值类型，key是属性名称，value是getter方法的参数类型
  private final Map<String, Class<?>> getTypes = new HashMap<>();
  //记录了默认的构造函数
  private Constructor<?> defaultConstructor;
  //记录了所有属性名称的集合
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  public Reflector(Class<?> clazz) {
    //初始化type字段
    type = clazz;
    //查找默认构造函数（无参构造函数）具体实现是以反射遍历所有构造方法
    addDefaultConstructor(clazz);
    //处理class中的getter方法
    addGetMethods(clazz);
    //处理class中的setter方法
    addSetMethods(clazz);
    //处理没有getter或setter方法的字段
    addFields(clazz);
    // 初始化为空数组
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    // 初始化为空数组
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);

    //初始化caseInsensitivePropertyMap集合 其中记录了所有大写格式的属性名称
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
      .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  private void addGetMethods(Class<?> clazz) {
    //conflictingGetters集合 key为属性名称，value为相应的getter方法集合，因为子类可能覆盖父类的getter方法，
    //所以同一属性的名称可能存在多个getter方法
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    //步骤一：获取指定类以及父类和接口中定义的方法
    Method[] methods = getClassMethods(clazz);
    //步骤二：按照javabean规范查找getter方法 添加到conflictingGetters中
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    //对于getter方法进行处理
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 子类覆盖父类的getter方法且返回值发生变化时，
   * 在getClassMethods中就会产生两个签名不同的方法，在Reflector.addUniqueMethods()方法中会被当做两个不同的方法添加到conflictingSetters集合中
   * 调用Reflector.resolveSetterConflicts()方法对这个覆写的方法进行处理，
   * 同时会将得到的getter方法记录到getMethods集合，并将其返回值类型填充到getTypes集合。
   * @param conflictingGetters
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    // 遍历 conflictingGetters 集合
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      //获取属性名
      String propName = entry.getKey();
      //是否是不明确的
      boolean isAmbiguous = false;
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          winner = candidate;
          continue;
        }
        //获取最终选择的方法方法返回值
        Class<?> winnerType = winner.getReturnType();
        //获取候选方法候选类型
        Class<?> candidateType = candidate.getReturnType();
        if (candidateType.equals(winnerType)) {
          //返回值相同
          if (!boolean.class.equals(candidateType)) {
            //不明确的类型 【不明确 抛异常】
            isAmbiguous = true;
            break;
          } else if (candidate.getName().startsWith("is")) {
            //对于boolean类型
            //当前方法返回值是当前最合适的方法的返回值的子类
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          //当前最适合的方法的返回值是当前方法返回值的子类，什么都不做 当前最适合的方法 依然不变
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          //当前返回值是当前最适合的方法的返回值的
          winner = candidate;
        } else {
          //不明确的类型 【不明确 抛异常】
          isAmbiguous = true;
          break;
        }
      }
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  /**
   * 对getMethods集合和getTypes集合进行填充
   * @param name
   * @param method
   * @param isAmbiguous
   */
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    //对于不明确方法 抛异常处理
    MethodInvoker invoker = isAmbiguous
        ? new AmbiguousMethodInvoker(method, MessageFormat.format(
            "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
            name, method.getDeclaringClass().getName()))
        : new MethodInvoker(method);
    //将属性名以及对应的MethodInvoker对象添加到getMethods集合中，Invoker的内容后面解析
    getMethods.put(name, invoker);
    //获取返回值的Type,TypeParameterResolver会在后面分析
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    //将属性名及其getter方法的返回值加到getTypes集合中保存，typeToClass()方法后面分析
    getTypes.put(name, typeToClass(returnType));
  }

  private void addSetMethods(Class<?> clazz) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Method[] methods = getClassMethods(clazz);
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
      .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    if (isValidPropertyName(name)) {
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      String propName = entry.getKey();
      List<Method> setters = entry.getValue();
      Class<?> getterType = getTypes.get(propName);
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      Method match = null;
      for (Method setter : setters) {
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (!isSetterAmbiguous) {
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
            property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, typeToClass(paramTypes[0]));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, typeToClass(paramTypes[0]));
  }

  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance(componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  /**
   * //处理类中定义的所有字段
   * @param clazz
   */
  private void addFields(Class<?> clazz) {
    //提取clazz中定义的所有字段
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        //过滤调static和final修饰的方法
        if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
          //填充setMethods集合和setTypes集合
          addSetField(field);
        }
      }
      //getMethods集合中不包同名属性时，将其记录到getMethods集合和getTypes集合中
      if (!getMethods.containsKey(field.getName())) {
        //填充getMethods集合和getTypes集合
        addGetField(field);
      }
    }
    if (clazz.getSuperclass() != null) {
      //处理父类中定义的字段
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /**
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler <code>Class.getMethods()</code>,
   * because we want to look for private methods as well.
   * 此方法返回包含在这个类和超类的任何声明的所有方法的一个数组。
   * 我们使用这种方法，而不是简单的Class.getMethods()因为我们也要寻找私有方法
   * @param clazz The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> clazz) {
    //用于记录指定类中定义的全部方法的唯一签名对应的Method对象
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      //记录currentClass类中定义的全部方法
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      //记录接口中定义一的方法
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      //获取父类继续while循环
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();
    //转换成methods数组返回
    return methods.toArray(new Method[0]);
  }

  /**
   * 为每个方法生成唯一签名
   * @param uniqueMethods
   * @param methods
   */
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) {
        //通过getSignature()方法得到的方法签名 规则：返回值类型#方法名称：参数类型列表。
        //通过getSignature()方法得到的发放签名是全局唯一的，可以作为该方法的唯一标识
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        //检测是否在子类中已经添加过该方法【如果添加过就忽略】
        if (!uniqueMethods.containsKey(signature)) {
          //记录该签名和方法的对应关系
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName - the name of the property
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
