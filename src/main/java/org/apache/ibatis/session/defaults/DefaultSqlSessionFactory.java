/**
 * Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.session.defaults;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;

/**
 * @author Clinton Begin
 */
public class DefaultSqlSessionFactory implements SqlSessionFactory {

    private final Configuration configuration;

    public DefaultSqlSessionFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public SqlSession openSession() {
        return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false);
    }

    @Override
    public SqlSession openSession(boolean autoCommit) {
        return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, autoCommit);
    }

    @Override
    public SqlSession openSession(ExecutorType execType) {
        return openSessionFromDataSource(execType, null, false);
    }

    @Override
    public SqlSession openSession(TransactionIsolationLevel level) {
        return openSessionFromDataSource(configuration.getDefaultExecutorType(), level, false);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
        return openSessionFromDataSource(execType, level, false);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
        return openSessionFromDataSource(execType, null, autoCommit);
    }

    @Override
    public SqlSession openSession(Connection connection) {
        return openSessionFromConnection(configuration.getDefaultExecutorType(), connection);
    }

    @Override
    public SqlSession openSession(ExecutorType execType, Connection connection) {
        return openSessionFromConnection(execType, connection);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
    /**
     * 1.       从核心配置文件mybatis-config.xml中获取Environment（这里面是数据源）；
     * 2.       从Environment中取得DataSource；
     * 3.       从Environment中取得TransactionFactory；
     * 4.       从DataSource里获取数据库连接对象Connection；
     * 5.       在取得的数据库连接上创建事务对象Transaction；
     * 6.       创建Executor对象（该对象非常重要，事实上sqlsession的所有操作都是通过它完成的）；
     * 7.       创建sqlsession对象。
     * @param execType
     * @param level
     * @param autoCommit
     * @return
     */
  //从数据源获取数据库连接
  private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {
    Transaction tx = null;
    try {
      //获取mybatis配置文件中的environment对象
      final Environment environment = configuration.getEnvironment();
      //从environment获取transactionFactory对象
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      //创建事务对象
      tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
      //根据配置创建executor
      final Executor executor = configuration.newExecutor(tx, execType);
      //创建DefaultSqlSession
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      closeTransaction(tx); // may have fetched a connection so lets call close()
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  //从数据库连接获取sqlSession
  private SqlSession openSessionFromConnection(ExecutorType execType, Connection connection) {
    try {
      //获取当前连接是否设置事务自动提交
      boolean autoCommit;
      try {
        autoCommit = connection.getAutoCommit();
      } catch (SQLException e) {
        // Failover to true, as most poor drivers
        // or databases won't support transactions
        autoCommit = true;
      }
      //获取mybatis配置文件中的environment对象
      final Environment environment = configuration.getEnvironment();
      //从environment获取transactionFactory对象
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      //创建事务对象
      final Transaction tx = transactionFactory.newTransaction(connection);
      //根据配置创建executor
      final Executor executor = configuration.newExecutor(tx, execType);
      //创建DefaultSqlSession
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

    private TransactionFactory getTransactionFactoryFromEnvironment(Environment environment) {
        if (environment == null || environment.getTransactionFactory() == null) {
            return new ManagedTransactionFactory();
        }
        return environment.getTransactionFactory();
    }

    private void closeTransaction(Transaction tx) {
        if (tx != null) {
            try {
                tx.close();
            } catch (SQLException ignore) {
                // Intentionally ignore. Prefer previous error.
            }
        }
    }

}
