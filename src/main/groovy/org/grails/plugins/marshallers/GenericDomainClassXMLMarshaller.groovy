package org.grails.plugins.marshallers

import grails.converters.XML
import grails.core.GrailsApplication
import grails.core.support.proxy.EntityProxyHandler
import grails.core.support.proxy.ProxyHandler
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.plugins.marshallers.config.MarshallingConfig
import org.grails.plugins.marshallers.config.MarshallingConfigPool
import org.grails.web.converters.ConverterUtil
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.converters.marshaller.NameAwareMarshaller
import org.grails.web.converters.marshaller.ObjectMarshaller
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl

/**
 *
 * @author dhalupa*
 */
@Slf4j
class GenericDomainClassXMLMarshaller implements ObjectMarshaller<XML>, NameAwareMarshaller {

    private ProxyHandler proxyHandler
    private GrailsApplication application
    private MarshallingConfigPool configPool
    private static MarshallingContext marshallingContext = new MarshallingContext();


    private static Map<Class, Class> attributeEditors = new HashMap<Class, Class>()

    GenericDomainClassXMLMarshaller(ProxyHandler proxyHandler, GrailsApplication application, MarshallingConfigPool configPool) {
        this.proxyHandler = proxyHandler
        this.application = application
        this.configPool = configPool
    }

    @Override
    boolean supports(Object object) {
        def clazz = proxyHandler.unwrapIfProxy(object).getClass()
        boolean supports = configPool.get(clazz) != null
        log.trace("Support for {} is {}", clazz, supports)
        return supports
    }

    @Override
    void marshalObject(Object v, XML xml) throws ConverterException {
        log.trace("Marshalling of {} started", v)
        def value = proxyHandler.unwrapIfProxy(v)
        Class clazz = value.getClass()
        PersistentEntity domainClass = application.mappingContext.getPersistentEntity(clazz.getName())
        MarshallingConfig mc = configPool.get(clazz)
        BeanWrapper beanWrapper = new BeanWrapperImpl(value)
        if (mc.shouldOutputIdentifier) {
            if (mc.identifier) {
                if (mc.identifier.size() == 1 && mc.identifier[0] instanceof Closure) {
                    mc.identifier[0].call(value, xml)
                } else {
                    mc.identifier.each {
                        def val = beanWrapper.getPropertyValue(it)
                        if (val != null) {
                            xml.attribute(it, val.toString())
                        }
                    }
                }
            } else {
                PersistentProperty id = domainClass.getIdentity()
                Object idValue = beanWrapper.getPropertyValue(id.getName())
                if (idValue != null) xml.attribute("id", String.valueOf(idValue))
            }
        }
        if (mc.shouldOutputVersion) {
            Object versionValue = beanWrapper.getPropertyValue(domainClass.getVersion().getName())
            xml.attribute("version", String.valueOf(versionValue))
        }

        if (mc.shouldOutputClass) {
            xml.attribute("class", clazz.getName())
        }

        mc.attribute?.each { prop ->
            log.trace("Trying to write field as xml attribute: {} on {}", prop, value)
            Object val = beanWrapper.getPropertyValue(prop)
            if (val != null) {
                def editorEntry = attributeEditors.find { it.key.isAssignableFrom(val.getClass()) }
                if (editorEntry) {
                    def editor = editorEntry.value.newInstance()
                    editor.setValue(val)
                    xml.attribute(prop, editor.getAsText())
                } else {
                    xml.attribute(prop, val.toString())
                }
            }
        }

        boolean includeMode = false
        if (mc.include?.size() > 0)
            includeMode = true

        List<PersistentProperty> properties = domainClass.getPersistentProperties()

        for (PersistentProperty property : properties) {
            if (!mc.identifier?.contains(property.getName()) && !mc.attribute?.contains(property.getName()) &&
                    (!includeMode && !mc.ignore?.contains(property.getName())
                            || includeMode && mc.include?.contains(property.getName()))) {
                def serializers = mc?.serializer
                Object val = beanWrapper.getPropertyValue(property.getName())
                if (serializers && serializers[property.name]) {
                    xml.startNode(property.name)
                    serializers[property.name].call(val, xml)
                    xml.end()
                } else {
                    if (val != null) {
                        log.trace("Trying to write field as xml element: {} on {}", property.name, value)
                        writeElement(xml, property, beanWrapper, mc)
                    }
                }
            }
        }
        if (mc.virtual) {
            mc.virtual.each { prop, callable ->
                xml.startNode(prop)
                Closure cl = mc.virtual[prop]
                if (cl.maximumNumberOfParameters == 2) {
                    cl.call(value, xml)
                } else {
                    cl.call(value, xml, marshallingContext)
                }

                xml.end()
            }
        }
    }


    private writeElement(XML xml, PersistentProperty property, BeanWrapper beanWrapper, MarshallingConfig mc) {
        xml.startNode(property.getName())
        if (!(property instanceof Association)) {
            // Write non-relation property
            Object val = beanWrapper.getPropertyValue(property.getName())
            xml.convertAnother(val)
        } else {
            Association association=property as Association
            Object referenceObject = beanWrapper.getPropertyValue(association.getName())
            if (mc.deep?.contains(property.getName())) {
                renderDeep(referenceObject, xml)
            } else {
                if (referenceObject != null) {
                    PersistentEntity referencedDomainClass = association.getAssociatedEntity()

                    // Embedded are now always fully rendered
                    if (referencedDomainClass == null || association.isEmbedded()) {
                        xml.convertAnother(referenceObject)
                    } else if (association instanceof ToOne) {
                        asShortObject(referenceObject, xml, referencedDomainClass.getIdentity(), referencedDomainClass)
                    } else {
                        PersistentProperty referencedIdProperty = referencedDomainClass.getIdentity()

                        if (referenceObject instanceof Collection) {
                            Collection o = (Collection) referenceObject
                            for (Object el : o) {
                                xml.startNode(xml.getElementName(el))
                                asShortObject(el, xml, referencedIdProperty, referencedDomainClass)
                                xml.end()
                            }
                        } else if (referenceObject instanceof Map) {
                            Map<Object, Object> map = (Map<Object, Object>) referenceObject
                            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                String key = String.valueOf(entry.getKey())
                                Object o = entry.getValue()
                                xml.startNode("entry").attribute("key", key)
                                asShortObject(o, xml, referencedIdProperty, referencedDomainClass)
                                xml.end()
                            }
                        }
                    }
                }
            }
        }
        xml.end()
    }

    private void renderDeep(referenceObject, XML xml) {
        if (referenceObject != null) {
            referenceObject = proxyHandler.unwrapIfProxy(referenceObject)
            if (referenceObject instanceof SortedMap) {
                referenceObject = new TreeMap((SortedMap) referenceObject)
            } else if (referenceObject instanceof SortedSet) {
                referenceObject = new TreeSet((SortedSet) referenceObject)
            } else if (referenceObject instanceof Set) {
                referenceObject = new HashSet((Set) referenceObject)
            } else if (referenceObject instanceof Map) {
                referenceObject = new HashMap((Map) referenceObject)
            } else if (referenceObject instanceof Collection) {
                referenceObject = new ArrayList((Collection) referenceObject)
            }
            xml.convertAnother(referenceObject)
        }
    }


    protected void asShortObject(Object refObj, XML xml, PersistentProperty idProperty,
                                 @SuppressWarnings("unused") PersistentEntity referencedDomainClass) throws ConverterException {
        MarshallingConfig refClassConfig = configPool.get(referencedDomainClass.class, true)
        if (refClassConfig?.identifier) {
            if (refClassConfig.identifier.size() == 1 && refClassConfig.identifier[0] instanceof Closure) {
                refClassConfig.identifier[0].call(refObj, xml)
            } else {
                def wrapper = new BeanWrapperImpl(refObj)
                refClassConfig.identifier.each {
                    def val = wrapper.getPropertyValue(it)
                    xml.attribute(it, String.valueOf(val))
                }
            }

        } else {
            Object idValue
            if (proxyHandler instanceof EntityProxyHandler) {
                idValue = ((EntityProxyHandler) proxyHandler).getProxyIdentifier(refObj)
                if (idValue == null) {
                    idValue = new BeanWrapperImpl(refObj).getPropertyValue(idProperty.getName())
                }
            } else {
                idValue = new BeanWrapperImpl(refObj).getPropertyValue(idProperty.getName())
            }
            xml.attribute("id", String.valueOf(idValue))
        }
    }


    static registerAttributeEditor(Class attrType, Class editorType) {
        attributeEditors.put(attrType, editorType)
    }

    @Override
    String getElementName(Object value) {
        log.trace("Fetching element name for {}", value)
        Class clazz = proxyHandler.unwrapIfProxy(value).getClass()
        PersistentEntity domainClass = application.mappingContext.getPersistentEntity(ConverterUtil.trimProxySuffix(clazz.getName()))
        MarshallingConfig mc = configPool.get(clazz, true)
        return mc.elementName ?: domainClass.decapitalizedName
    }

    /**
     *
     * @return marshalling context for the xml marshalling
     */
    static MarshallingContext getMarshallingContext() {
        return marshallingContext
    }
}
