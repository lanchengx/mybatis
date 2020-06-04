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

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.ibatis.builder.BuilderException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XPathParser {
    // 要解析的xml文件被转化成的Document对象。
    private final Document document;
    // 获取document时是否要开启校验，开启校验的话会根据xml配置文件中定义的dtd文件校验xml格式，默认不开启校验。
    private boolean validation;
    // 实体解析器，用于从本地工程目录中引入dtd文件，而不是根据网络URL去加载校验文件。
    private EntityResolver entityResolver;
    // mybatis-config.xml配置文件中，<Properties>节点引入或定义的属性。
    private Properties variables;
    // 封装的XPath对象，用来解析表达式。
    private XPath xpath;

    public XPathParser(String xml) {
        commonConstructor(false, null, null);
        this.document = createDocument(new InputSource(new StringReader(xml)));
    }

    public XPathParser(Reader reader) {
        commonConstructor(false, null, null);
        this.document = createDocument(new InputSource(reader));
    }

    public XPathParser(InputStream inputStream) {
        commonConstructor(false, null, null);
        this.document = createDocument(new InputSource(inputStream));
    }

    public XPathParser(Document document) {
        commonConstructor(false, null, null);
        this.document = document;
    }

    public XPathParser(String xml, boolean validation) {
        commonConstructor(validation, null, null);
        this.document = createDocument(new InputSource(new StringReader(xml)));
    }

    public XPathParser(Reader reader, boolean validation) {
        commonConstructor(validation, null, null);
        this.document = createDocument(new InputSource(reader));
    }

    public XPathParser(InputStream inputStream, boolean validation) {
        commonConstructor(validation, null, null);
        this.document = createDocument(new InputSource(inputStream));
    }

    public XPathParser(Document document, boolean validation) {
        commonConstructor(validation, null, null);
        this.document = document;
    }

    public XPathParser(String xml, boolean validation, Properties variables) {
        commonConstructor(validation, variables, null);
        this.document = createDocument(new InputSource(new StringReader(xml)));
    }

    public XPathParser(Reader reader, boolean validation, Properties variables) {
        commonConstructor(validation, variables, null);
        this.document = createDocument(new InputSource(reader));
    }

    public XPathParser(InputStream inputStream, boolean validation, Properties variables) {
        commonConstructor(validation, variables, null);
        this.document = createDocument(new InputSource(inputStream));
    }

    public XPathParser(Document document, boolean validation, Properties variables) {
        commonConstructor(validation, variables, null);
        this.document = document;
    }

    public XPathParser(String xml, boolean validation, Properties variables, EntityResolver entityResolver) {
        commonConstructor(validation, variables, entityResolver);
        this.document = createDocument(new InputSource(new StringReader(xml)));
    }

    public XPathParser(Reader reader, boolean validation, Properties variables, EntityResolver entityResolver) {
        commonConstructor(validation, variables, entityResolver);
        this.document = createDocument(new InputSource(reader));
    }

    public XPathParser(InputStream inputStream, boolean validation, Properties variables, EntityResolver entityResolver) {
        commonConstructor(validation, variables, entityResolver);
        this.document = createDocument(new InputSource(inputStream));
    }

    public XPathParser(Document document, boolean validation, Properties variables, EntityResolver entityResolver) {
        commonConstructor(validation, variables, entityResolver);
        this.document = document;
    }

    public void setVariables(Properties variables) {
        this.variables = variables;
    }

    /**
     * evalXXX方法,这些方法主要用于解析boolean 、short、long 、int 、String 、Node等类型的信息。底层是通过evaluate()方法实现。
     * 其中，evalString()方法中，会通过调用PropertyParser.parse()处理占位符；
     * evalNode()、evalNodes()方法中，根据解析结果会创建XNode对象。具体创建过程，在XNode类源码分析中学习
     * @param expression
     * @return
     */
    public String evalString(String expression) {
        return evalString(document, expression);
    }

    public String evalString(Object root, String expression) {
        String result = (String) evaluate(expression, root, XPathConstants.STRING);
        result = PropertyParser.parse(result, variables);
        return result;
    }

    public Boolean evalBoolean(String expression) {
        return evalBoolean(document, expression);
    }

    public Boolean evalBoolean(Object root, String expression) {
        return (Boolean) evaluate(expression, root, XPathConstants.BOOLEAN);
    }

    public Short evalShort(String expression) {
        return evalShort(document, expression);
    }

    public Short evalShort(Object root, String expression) {
        return Short.valueOf(evalString(root, expression));
    }

    public Integer evalInteger(String expression) {
        return evalInteger(document, expression);
    }

    public Integer evalInteger(Object root, String expression) {
        return Integer.valueOf(evalString(root, expression));
    }

    public Long evalLong(String expression) {
        return evalLong(document, expression);
    }

    public Long evalLong(Object root, String expression) {
        return Long.valueOf(evalString(root, expression));
    }

    public Float evalFloat(String expression) {
        return evalFloat(document, expression);
    }

    public Float evalFloat(Object root, String expression) {
        return Float.valueOf(evalString(root, expression));
    }

    public Double evalDouble(String expression) {
        return evalDouble(document, expression);
    }

    public Double evalDouble(Object root, String expression) {
        return (Double) evaluate(expression, root, XPathConstants.NUMBER);
    }

    public List<XNode> evalNodes(String expression) {
        return evalNodes(document, expression);
    }

    public List<XNode> evalNodes(Object root, String expression) {
        List<XNode> xnodes = new ArrayList<>();
        NodeList nodes = (NodeList) evaluate(expression, root, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            xnodes.add(new XNode(this, nodes.item(i), variables));
        }
        return xnodes;
    }

    public XNode evalNode(String expression) {
        return evalNode(document, expression);
    }

    public XNode evalNode(Object root, String expression) {
        Node node = (Node) evaluate(expression, root, XPathConstants.NODE);
        if (node == null) {
            return null;
        }
        return new XNode(this, node, variables);
    }

    private Object evaluate(String expression, Object root, QName returnType) {
        try {
            //计算指定上下文中的 XPath 表达式并返回指定类型的结果。
            //如果 returnType 不是 XPathConstants (NUMBER、STRING、BOOLEAN、NODE 或 NODESET) 中定义的某种类型，则抛出 IllegalArgumentException。
            //如果 item 为 null 值，则将使用一个空文档作为上下文。如果 expression 或 returnType 为 null，则抛出 NullPointerException。
            return xpath.evaluate(expression, root, returnType);
        } catch (Exception e) {
            throw new BuilderException("Error evaluating XPath.  Cause: " + e, e);
        }
    }

    /**
     * 将InputSource对象转化为Document对象
     * @param inputSource
     * @return
     */
    private Document createDocument(InputSource inputSource) {
        // important: this must only be called AFTER common constructor
        try {
            //创建DocumentBuilderFactory实例对象
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // 设置是否启用DTD验证
            factory.setValidating(validation);

            // 设置是否支持XML名称空间
            factory.setNamespaceAware(false);
            // 设置解析器是否忽略注释
            factory.setIgnoringComments(true);
            /**
             * 设置必须删除元素内容中的空格（有时也可以称作“可忽略空格”，请参阅 XML Rec 2.10）。
             * 注意，只有在空格直接包含在元素内容中，并且该元素内容是只有一个元素的内容模式时，
             * 才能删除空格（请参阅 XML Rec 3.2.1）。由于依赖于内容模式，因此此设置要求解析器处于验证模式。默认情况下，其值设置为 false。
             */
            factory.setIgnoringElementContentWhitespace(false);
            // 指定由此代码生成的解析器将把 CDATA 节点转换为 Text 节点，并将其附加到相邻（如果有）的 Text 节点。默认情况下，其值设置为 false。
            factory.setCoalescing(false);
            //指定由此代码生成的解析器将扩展实体引用节点。默认情况下，此值设置为 true
            factory.setExpandEntityReferences(true);

            //创建DocumentBuilder实例对象
            DocumentBuilder builder = factory.newDocumentBuilder();
            //指定使用 EntityResolver 解析要解析的 XML 文档中存在的实体。将其设置为 null 将会导致底层实现使用其自身的默认实现和行为。
            builder.setEntityResolver(entityResolver);
            // 定义解析xml文档的错误处理器，如果发生了错误或致命错误则直接抛出异常，如果是警告默认不做处理
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    // NOP
                }
            });
            return builder.parse(inputSource);
        } catch (Exception e) {
            throw new BuilderException("Error creating document instance.  Cause: " + e, e);
        }
    }

    /**
     * 初始化了除Document之外的其他类属性，XPath是通过XPathFactory创建的，源码如下：
     * @param validation
     * @param variables
     * @param entityResolver
     */
    private void commonConstructor(boolean validation, Properties variables, EntityResolver entityResolver) {
        this.validation = validation;
        this.entityResolver = entityResolver;
        this.variables = variables;
        XPathFactory factory = XPathFactory.newInstance();
        this.xpath = factory.newXPath();
    }

}
