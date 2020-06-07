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
package org.apache.ibatis.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拦截器链，存储了所有定义的拦截器以及相关的几个操作的方法
 * 分别有添加拦截器、为目标对象添加所有拦截器、获取当前所有拦截器三个方法。
 * @author Clinton Begin
 */
public class InterceptorChain {

  private final List<Interceptor> interceptors = new ArrayList<>();

    /**
     * 为目标对象生成代理，之后目标对象调用方法的时候走的不是原方法而是代理方法
     *
     * 调用的地方为之生成插件的时机（换句话说就是pluginAll方法调用的时机）是Executor、ParameterHandler、ResultSetHandler、StatementHandler四个接口实现类生成的时候，
     *
     * 这里值得注意的是：
     *
     *   1、形参Object target，这个是Executor、ParameterHandler、ResultSetHandler、StatementHandler接口的实现类，
     *      换句话说，plugin方法是要为Executor、ParameterHandler、ResultSetHandler、StatementHandler的实现类生成代理，
     *      从而在调用这几个类的方法的时候，其实调用的是InvocationHandler的invoke方法
     *
     *   2、这里的target是通过for循环不断赋值的，也就是说如果有多个拦截器，
     *      那么如果我用P表示代理，生成第一次代理为P(target)，生成第二次代理为P(P(target))，生成第三次代理为P(P(P(target)))，不断嵌套下去，
     *      这就得到一个重要的结论：
     *           <plugins>...</plugins>中后定义的<plugin>实际其拦截器方法先被执行，
     *          因为根据这段代码来看，后定义的<plugin>代理实际后生成，包装了先生成的代理，自然其代理方法也先执行
     * @param target
     * @return
     */
  public Object pluginAll(Object target) {
    for (Interceptor interceptor : interceptors) {
      target = interceptor.plugin(target);
    }
    return target;
  }

  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }

  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
