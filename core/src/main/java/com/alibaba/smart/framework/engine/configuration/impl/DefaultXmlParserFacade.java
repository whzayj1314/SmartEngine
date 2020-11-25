package com.alibaba.smart.framework.engine.configuration.impl;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

import com.alibaba.smart.framework.engine.common.util.MapUtil;
import com.alibaba.smart.framework.engine.common.util.StringUtil;
import com.alibaba.smart.framework.engine.configuration.ProcessEngineConfiguration;
import com.alibaba.smart.framework.engine.configuration.aware.ProcessEngineConfigurationAware;
import com.alibaba.smart.framework.engine.exception.EngineException;
import com.alibaba.smart.framework.engine.extension.annoation.ExtensionBinding;
import com.alibaba.smart.framework.engine.extension.constant.ExtensionConstant;
import com.alibaba.smart.framework.engine.xml.parser.AttributeParser;
import com.alibaba.smart.framework.engine.xml.parser.ElementParser;
import com.alibaba.smart.framework.engine.xml.parser.ParseContext;
import com.alibaba.smart.framework.engine.xml.parser.XmlParserFacade;

/**
 * 默认处理器扩展点 Created by ettear on 16-4-12.
 */
@SuppressWarnings("rawtypes")
@ExtensionBinding(group = ExtensionConstant.COMMON, bindKey = XmlParserFacade.class)
public class DefaultXmlParserFacade implements
    XmlParserFacade, ProcessEngineConfigurationAware {

    private Map<QName, ElementParser> artifactParsers = MapUtil.newHashMap();

    private Map<QName, AttributeParser> attributeParsers = MapUtil.newHashMap();

    private ProcessEngineConfiguration processEngineConfiguration;

    private Map<Class,Object> bindings;

    private Map<QName, Object> bindingsWithQName = MapUtil.newHashMap();

    @Override
    public void start() {
        this.bindings = processEngineConfiguration.getAnnotationScanner().getScanResult().get(
            ExtensionConstant.ELEMENT_PARSER).getBindingMap();
        Set<Entry<Class, Object>> entries = bindings.entrySet();
        for (Entry<Class, Object> entry : entries) {
            try {
                Class aClass = entry.getKey();
                Object o = aClass.newInstance();

                Field field = null;
                QName qName = null;
                try {
                    field = aClass.getField("qtype");
                    qName = (QName) field.get(o);
                    bindingsWithQName.put(qName, entry.getValue());
                } catch (Throwable e) {
                    // ignore NoSuchFieldException
                }

                // for list
                try {
                    field = aClass.getField("qtypes");
                    List<QName> qNames=  (List<QName>)field.get(o);
                    if(qNames != null && qNames.size() > 0) {
                        for(QName item : qNames) {
                            // single already registered, skip
                            if(item.equals(qName)) {
                                continue;
                            }
                            bindingsWithQName.put(item, entry.getValue());
                        }
                    }
                } catch (Throwable e) {
                    // ignore NoSuchFieldException
                }


            } catch (Exception e) {
                //TUNE 堆栈有些乱
                throw new RuntimeException(e);
            }
        }

    }

    @Override
    public void stop() {
        //for (ElementParser stAXArtifactParser : artifactParsers.values()) {
        //    stAXArtifactParser.stop();
        //}
        //for (AttributeParser attributeParser : attributeParsers.values()) {
        //    attributeParser.stop();
        //}
    }


    @Override
    public Object parseElement(XMLStreamReader reader, ParseContext context) {
        QName nodeQname = reader.getName();
        ElementParser artifactParser = (ElementParser)  bindingsWithQName.get(nodeQname);
        try {
            if(null != artifactParser){
                return artifactParser.parseElement(reader, context);
            }
        } catch (Exception e) {
            //TUNE 堆栈有些乱
            throw new RuntimeException(e);
        }

        throw new EngineException("No parser found for QName: " + nodeQname);
    }

    @Override
    public Object parseAttribute(QName attributeQName, XMLStreamReader reader, ParseContext context) {
        if (null == attributeQName) {
            return null;
        }
        QName currentNode = reader.getName();
        String currentNodeNamespaceURI = currentNode.getNamespaceURI();


        QName tunedAttributeQname;

        String attributeNamespaceURI = attributeQName.getNamespaceURI();
        String attributeLocalPart = attributeQName.getLocalPart();

        if(StringUtil.isEmpty(attributeNamespaceURI)){
            tunedAttributeQname=new QName(currentNodeNamespaceURI, attributeLocalPart);
        }else{
            tunedAttributeQname=attributeQName;
        }

        AttributeParser attributeParser = this.attributeParsers.get(tunedAttributeQname);
        if (null == attributeParser) {
            attributeParser = this.attributeParsers.get(currentNode);
        }

        if (null != attributeParser) {
            return attributeParser.parseAttribute(attributeQName, reader, context);
        } else if (StringUtil.equals(currentNodeNamespaceURI, attributeNamespaceURI)) {
            return reader.getAttributeValue(attributeNamespaceURI, attributeLocalPart);
        } else {
            return null;
        }
    }

    @Override
    public void setProcessEngineConfiguration(ProcessEngineConfiguration processEngineConfiguration) {
        this.processEngineConfiguration = processEngineConfiguration;
    }
}
