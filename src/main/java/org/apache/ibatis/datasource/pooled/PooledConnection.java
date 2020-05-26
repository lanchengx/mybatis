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
package org.apache.ibatis.datasource.pooled;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * @author Clinton Begin
 * 它主要是用来管理数据库连接的，它是一个代理类，实现了 InvocationHandler 接口
 */
class PooledConnection implements InvocationHandler {
  // close 方法
  private static final String CLOSE = "close";
  private static final Class<?>[] IFACES = new Class<?>[] { Connection.class };

  private final int hashCode;
  // 记录当前的 PooledConnection 对象所在的 PooledDataSource 对象，
  // 该 PooledConnection 对象是从 PooledDataSource 对象中获取的，
  // 当调用 close 方法时会将 PooledConnection 放回该 PooledDataSource 中去
  private final PooledDataSource dataSource;
  // 真正的数据库连接
  private final Connection realConnection;
  // 数据库连接的代理对象
  private final Connection proxyConnection;
  // 从连接池中取出该连接的时间戳
  private long checkoutTimestamp;
  // 该连接创建的时间戳
  private long createdTimestamp;
  // 该连接最后一次被使用的时间戳
  private long lastUsedTimestamp;
  // 用于标识该连接所在的连接池，由URL+username+password 计算出来的hash值
  private int connectionTypeCode;
  // 该连接是否有效
  private boolean valid;

  /**
   * Constructor for SimplePooledConnection that uses the Connection and PooledDataSource passed in.
   * // 创建连接
   * @param connection
   *          - the connection that is to be presented as a pooled connection
   * @param dataSource
   *          - the dataSource that the connection is from
   */
  public PooledConnection(Connection connection, PooledDataSource dataSource) {
    this.hashCode = connection.hashCode();
    this.realConnection = connection;
    this.dataSource = dataSource;
    this.createdTimestamp = System.currentTimeMillis();
    this.lastUsedTimestamp = System.currentTimeMillis();
    this.valid = true;
    this.proxyConnection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), IFACES, this);
  }

  /**
   * Invalidates the connection.
   *  废弃该连接
   */
  public void invalidate() {
    valid = false;
  }

  /**
   * Method to see if the connection is usable.
   * 判断该连接是否有效
   * 1.判断 valid 字段
   * 2.向数据库中发送检测测试的SQL，查看真正的连接还是否有效
   * @return True if the connection is usable
   */
  public boolean isValid() {
    return valid && realConnection != null && dataSource.pingConnection(this);
  }

  /**
   * Getter for the *real* connection that this wraps.
   *
   * @return The connection
   */
  public Connection getRealConnection() {
    return realConnection;
  }

  /**
   * Getter for the proxy for the connection.
   *
   * @return The proxy
   */
  public Connection getProxyConnection() {
    return proxyConnection;
  }

  /**
   * Gets the hashcode of the real connection (or 0 if it is null).
   *
   * @return The hashcode of the real connection (or 0 if it is null)
   */
  public int getRealHashCode() {
    return realConnection == null ? 0 : realConnection.hashCode();
  }

  /**
   * Getter for the connection type (based on url + user + password).
   *
   * @return The connection type
   */
  public int getConnectionTypeCode() {
    return connectionTypeCode;
  }

  /**
   * Setter for the connection type.
   *
   * @param connectionTypeCode
   *          - the connection type
   */
  public void setConnectionTypeCode(int connectionTypeCode) {
    this.connectionTypeCode = connectionTypeCode;
  }

  /**
   * Getter for the time that the connection was created.
   *
   * @return The creation timestamp
   */
  public long getCreatedTimestamp() {
    return createdTimestamp;
  }

  /**
   * Setter for the time that the connection was created.
   *
   * @param createdTimestamp
   *          - the timestamp
   */
  public void setCreatedTimestamp(long createdTimestamp) {
    this.createdTimestamp = createdTimestamp;
  }

  /**
   * Getter for the time that the connection was last used.
   *
   * @return - the timestamp
   */
  public long getLastUsedTimestamp() {
    return lastUsedTimestamp;
  }

  /**
   * Setter for the time that the connection was last used.
   *
   * @param lastUsedTimestamp
   *          - the timestamp
   */
  public void setLastUsedTimestamp(long lastUsedTimestamp) {
    this.lastUsedTimestamp = lastUsedTimestamp;
  }

  /**
   * Getter for the time since this connection was last used.
   *
   * @return - the time since the last use
   */
  public long getTimeElapsedSinceLastUse() {
    return System.currentTimeMillis() - lastUsedTimestamp;
  }

  /**
   * Getter for the age of the connection.
   *
   * @return the age
   */
  public long getAge() {
    return System.currentTimeMillis() - createdTimestamp;
  }

  /**
   * Getter for the timestamp that this connection was checked out.
   *
   * @return the timestamp
   */
  public long getCheckoutTimestamp() {
    return checkoutTimestamp;
  }

  /**
   * Setter for the timestamp that this connection was checked out.
   *
   * @param timestamp
   *          the timestamp
   */
  public void setCheckoutTimestamp(long timestamp) {
    this.checkoutTimestamp = timestamp;
  }

  /**
   * Getter for the time that this connection has been checked out.
   *
   * @return the time
   */
  public long getCheckoutTime() {
    return System.currentTimeMillis() - checkoutTimestamp;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  /**
   * Allows comparing this connection to another.
   *
   * @param obj
   *          - the other connection to test for equality
   * @see Object#equals(Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof PooledConnection) {
      return realConnection.hashCode() == ((PooledConnection) obj).realConnection.hashCode();
    } else if (obj instanceof Connection) {
      return hashCode == obj.hashCode();
    } else {
      return false;
    }
  }

  /**
   * Required for InvocationHandler implementation.
   *
   * @param proxy
   *          - not used
   * @param method
   *          - the method to be executed
   * @param args
   *          - the parameters to be passed to the method
   * @see java.lang.reflect.InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String methodName = method.getName();
    // *** 如果执行的方法是 close 方法，则会把当前连接放回到 连接池中去，供下次使用，而不是真正的关闭数据库连接
    if (CLOSE.equals(methodName)) {
      dataSource.pushConnection(this);
      return null;
    }
    try {
      // 如果不是 close 方法，则 调用 真正的数据库连接执行
      if (!Object.class.equals(method.getDeclaringClass())) {
        // issue #579 toString() should never fail
        // throw an SQLException instead of a Runtime
        // 执行之前，需要进行连接的检测
        checkConnection();
      }
      // 调用数据库真正的连接进行执行
      return method.invoke(realConnection, args);
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }

  }

  private void checkConnection() throws SQLException {
    if (!valid) {
      throw new SQLException("Error accessing PooledConnection. Connection is invalid.");
    }
  }

}
