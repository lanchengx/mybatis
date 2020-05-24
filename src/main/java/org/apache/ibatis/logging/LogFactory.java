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
package org.apache.ibatis.logging;

import java.lang.reflect.Constructor;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */

/**
 * 日志工厂 日志模块核心类
 */
public final class LogFactory {

  /**
   * Marker to be used by logging implementations that support markers.
   * 给支持marker功能的logger使用(目前有slf4j, log4j2)
   */
  public static final String MARKER = "MYBATIS";

  /**
   * 具体究竟用哪个日志框架，那个框架所对应logger的构造函数
   */
  private static Constructor<? extends Log> logConstructor;

  /**
   * 顺序加载日志框架
   */
  static {
    tryImplementation(LogFactory::useSlf4jLogging);
    tryImplementation(LogFactory::useCommonsLogging);
    tryImplementation(LogFactory::useLog4J2Logging);
    tryImplementation(LogFactory::useLog4JLogging);
    tryImplementation(LogFactory::useJdkLogging);
    tryImplementation(LogFactory::useNoLogging);
  }

  /**
   * 私有构造函数，不允许调用构造函数new对象，保证单例
   */
  private LogFactory() {
    // disable construction
  }

  /**
   *
   * @param clazz 类
   * @return
   */
  public static Log getLog(Class<?> clazz) {
    return getLog(clazz.getName());
  }

  /**
   *
   * @param logger 类名
   * @return
   */
  public static Log getLog(String logger) {
    try {
      // log对象
      return logConstructor.newInstance(logger);
    } catch (Throwable t) {
      throw new LogException("Error creating logger for logger " + logger + ".  Cause: " + t, t);
    }
  }

  /**
   * 自定义logger
   * @param clazz
   */
  public static synchronized void useCustomLogging(Class<? extends Log> clazz) {
    setImplementation(clazz);
  }

  public static synchronized void useSlf4jLogging() {
    setImplementation(org.apache.ibatis.logging.slf4j.Slf4jImpl.class);
  }

  public static synchronized void useCommonsLogging() {
    setImplementation(org.apache.ibatis.logging.commons.JakartaCommonsLoggingImpl.class);
  }

  public static synchronized void useLog4JLogging() {
    setImplementation(org.apache.ibatis.logging.log4j.Log4jImpl.class);
  }

  public static synchronized void useLog4J2Logging() {
    setImplementation(org.apache.ibatis.logging.log4j2.Log4j2Impl.class);
  }

  public static synchronized void useJdkLogging() {
    setImplementation(org.apache.ibatis.logging.jdk14.Jdk14LoggingImpl.class);
  }
  /**
   *   这个没用到
   */
  public static synchronized void useStdOutLogging() {
    setImplementation(org.apache.ibatis.logging.stdout.StdOutImpl.class);
  }

  public static synchronized void useNoLogging() {
    setImplementation(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
  }

  private static void tryImplementation(Runnable runnable) {
    // 未加载过日志时执行加载方法（顺序加载，一旦加载日志框架就不再调用run方法加载后面的日志框架）
    if (logConstructor == null) {
      try {
        // 调用run 方法 而非start ，未启动多线程
        runnable.run();
      } catch (Throwable t) {
        // 忽略异常
        // ignore
      }
    }
  }

  /**
   * 加载日志框架实现方法
   * @param implClass
   */
  private static void setImplementation(Class<? extends Log> implClass) {
    try {
      // 获取构造函数
      Constructor<? extends Log> candidate = implClass.getConstructor(String.class);
      // 实例化Log 对象  打印debug日志
      Log log = candidate.newInstance(LogFactory.class.getName());
      if (log.isDebugEnabled()) {
        log.debug("Logging initialized using '" + implClass + "' adapter.");
      }
      logConstructor = candidate;
    } catch (Throwable t) {
      // 异常时在外层方法忽略
      throw new LogException("Error setting Log implementation.  Cause: " + t, t);
    }
  }

}
