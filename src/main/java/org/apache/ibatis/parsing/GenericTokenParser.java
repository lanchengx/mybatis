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
package org.apache.ibatis.parsing;

/**
 * @author Clinton Begin
 * 通用标记解析器
 * 这里的通用标记解析器处理的是SQL脚本中#{parameter}、${parameter}参数，
 * 根据给定TokenHandler（标记处理器）来进行处理，TokenHandler是标记真正的处理器，而本篇的解析器只是处理器处理的前提工序——解析，
 * 本类重在解析，而非处理，具体的处理会调用具体的TokenHandler的handleToken()方法来完成。
 */
public class GenericTokenParser {

    //有一个开始和结束记号
    private final String openToken;
    private final String closeToken;
    //记号处理器
    private final TokenHandler handler;

    public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
        this.openToken = openToken;
        this.closeToken = closeToken;
        this.handler = handler;
    }

    /**
     * 这个方法的作用就是通过参数的开始标记与结束标记，循环获取SQL串中的参数，
     * 并对其进行一定的处理，组合成新串之后，架构新串返回。
     * @param text
     * @return
     */
    public String parse(String text) {
        // 该方法的参数text其实一般是SQL脚本字符串
        if (text == null || text.isEmpty()) {
            return "";
        }
        // search open token
        // 获取openToken子串在text中的第一次出现的开始下标start
        int start = text.indexOf(openToken);
        if (start == -1) {
            return text;
        }
        // 将给定字符串转为字符数组src，并定义偏移量offset为0，
        char[] src = text.toCharArray();
        int offset = 0;
        final StringBuilder builder = new StringBuilder();
        StringBuilder expression = null;
        // 给定参数text中存在openToken子串判断
        while (start > -1) {
            // 判断在text的start位置的前一位字符是否为"\"（反斜扛）
            // 如果是反斜杠，说明获取到的参数被屏蔽了，我们需要去除这个反斜杠，并重新定位offset。
            // 当然如果不是反斜扛，说明参数正常，则正常执行。
            if (start > 0 && src[start - 1] == '\\') {
                // this open token is escaped. remove the backslash and continue.
                builder.append(src, offset, start - offset - 1).append(openToken);
                offset = start + openToken.length();
            } else {
                // found open token. let's search close token.
                if (expression == null) {
                    expression = new StringBuilder();
                } else {
                    expression.setLength(0);
                }
                builder.append(src, offset, start - offset);
                offset = start + openToken.length();
                // 获取第一个匹配子串的末位位置end如果end为-1，表示不存在closeToken
                int end = text.indexOf(closeToken, offset);
                while (end > -1) {
                    // 存在closeToken
                    // 判断在text的end位置的前一位字符是否为"\"（反斜扛）
                    // 如果是反斜杠，说明获取到的参数被屏蔽了，我们需要去除这个反斜杠，并重新定位offset。
                    // 当然如果不是反斜扛，说明参数正常，则正常执行。
                    if (end > offset && src[end - 1] == '\\') {
                        // this close token is escaped. remove the backslash and continue.
                        expression.append(src, offset, end - offset - 1).append(closeToken);
                        offset = end + closeToken.length();
                        end = text.indexOf(closeToken, offset);
                    } else {
                        // 获取offset 到 end 之间的子串
                        expression.append(src, offset, end - offset);
                        break;
                    }
                }
                // 如果end为-1，表示不存在closeToken
                // 则获取末位end之前的所有串，并重新定位offset为src数组长度
                if (end == -1) {
                    // close token was not found.
                    builder.append(src, start, src.length - start);
                    offset = src.length;
                } else {
                    // 调用TokenHandler的handleToken()方法对获取到的参数串进行处理（比如替换参数之类），然后将处理后的串添加到之前的子串之上
                    builder.append(handler.handleToken(expression.toString()));
                    // 重新定位偏移量offset为结束标记的下一位（end+closeToken的长度=end+1）。
                    offset = end + closeToken.length();
                }
            }
            // 获取text中下一步openToken的开始位置，重置start,执行循环体
            // 处理每一个参数，直到最后一个参数
            start = text.indexOf(openToken, offset);
        }
        //最后验证偏移量offset与src数组的长度，
        // 如果offset小，说明原串还有部分未添加到新串之上，将末尾剩余部分添加到新串，然后将新串返回，
        // 如果offset不小于src的数组长度，则直接返回新串
        if (offset < src.length) {
            builder.append(src, offset, src.length - offset);
        }
        return builder.toString();
    }
}
