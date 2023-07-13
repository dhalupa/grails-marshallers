package org.grails.plugins.marshallers

import grails.converters.JSON
import grails.core.GrailsApplication
import grails.core.support.proxy.EntityProxyHandler
import grails.core.support.proxy.ProxyHandler
import groovy.util.logging.Slf4j
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.plugins.marshallers.config.MarshallingConfig
import org.grails.plugins.marshallers.config.MarshallingConfigPool
import org.grails.web.converters.ConverterUtil
import org.grails.web.converters.exceptions.ConverterException
import org.grails.web.converters.marshaller.ObjectMarshaller
import org.grails.web.json.JSONWriter
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl

/**
 *
 * @author dhalupa*
 */
@Slf4j
class GenericDomainClassJSONMarshaller implements ObjectMarshaller<JSON> {
    private GrailsApplication application
    private ProxyHandler proxyHandler
    private MarshallingConfigPool configPool
    private static MarshallingContext marshallingContext = new MarshallingContext();

    GenericDomainClassJSONMarshaller(ProxyHandler proxyHandler, GrailsApplication application, MarshallingConfigPool configPool) {
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
    void marshalObject(Object v, JSON json) throws ConverterException {
        JSONWriter writer = json.getWriter()
        def value = proxyHandler.unwrapIfProxy(v)
        Class clazz = value.getClass()
        log.trace('Marshalling {}', clazz.name)
        MarshallingConfig mc = configPool.get(clazz, true)
        PersistentEntity domainClass = application.mappingContext.getPersistentEntity(ConverterUtil.trimProxySuffix(clazz.getName()))
        BeanWrapper beanWrapper = new BeanWrapperImpl(value)

        writer.object()
        if (mc.shouldOutputClass) {
            writer.key("class").value(clazz.getName())
        }
        if (mc.shouldOutputIdentifier) {
            PersistentProperty id = domainClass.getIdentity()
            Object idValue = extractValue(value, id)
            json.property("id", idValue)
        }

        if (mc.shouldOutputVersion) {
            PersistentProperty versionProperty = domainClass.getVersion()
            Object version = extractValue(value, versionProperty)
            json.property("version", version)
        }

        List<PersistentProperty> properties = domainClass.getPersistentProperties()

        boolean includeMode = false
        if (mc.include?.size() > 0)
            includeMode = true

        for (PersistentProperty property : properties) {
            if (includeMode && mc.include?.contains(property.getName())) {
                serializeProperty(property, mc, beanWrapper, json, writer, value)
            } else if (!includeMode && !mc.ignore?.contains(property.getName())) {
                serializeProperty(property, mc, beanWrapper, json, writer, value)
            }
        }
        def entityContext = [:]
        mc.virtual?.each { prop, callable ->
            writer.key(prop)
            Closure cl = mc.virtual[prop]
            if (cl.maximumNumberOfParameters == 2) {
                cl.call(value, json)
            } else if (cl.maximumNumberOfParameters == 3) {
                cl.call(value, json, marshallingContext)
            } else {
                cl.call(value, json, entityContext, marshallingContext)
            }

        }

        writer.endObject()
    }

    protected void serializeProperty(PersistentProperty property, MarshallingConfig mc,
                                     BeanWrapper beanWrapper, JSON json, JSONWriter writer, def value) {
        writer.key(property.getName())
        if (mc.serializer?.containsKey(property.getName())) {
            mc.serializer[property.getName()].call(value, writer)
        } else {
            if (!(property instanceof Association)) {
                // Write non-relation property
                Object val = beanWrapper.getPropertyValue(property.getName())
                json.convertAnother(val)
            } else {
                Association association=property as Association
                Object referenceObject = beanWrapper.getPropertyValue(association.getName())
                if (mc.deep?.contains(association.getName())) {
                    if (referenceObject == null) {
                        writer.value(null)
                    } else {
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
                        json.convertAnother(referenceObject)
                    }
                } else {
                    if (referenceObject == null) {
                        json.value(null)
                    } else {
                        PersistentEntity referencedDomainClass = association.getAssociatedEntity()

                        // Embedded are now always fully rendered
                        if (referencedDomainClass == null || association.isEmbedded()) {
                            json.convertAnother(referenceObject)
                        } else if (association instanceof ToOne) {
                            asShortObject(referenceObject, json, referencedDomainClass.getIdentity(), referencedDomainClass)
                        } else {
                            PersistentProperty referencedIdProperty = referencedDomainClass.getIdentity()


                            if (referenceObject instanceof Collection) {
                                Collection o = (Collection) referenceObject
                                writer.array()
                                for (Object el : o) {
                                    asShortObject(el, json, referencedIdProperty, referencedDomainClass)
                                }
                                writer.endArray()
                            } else if (referenceObject instanceof Map) {
                                Map<Object, Object> map = (Map<Object, Object>) referenceObject
                                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                    String key = String.valueOf(entry.getKey())
                                    Object o = entry.getValue()
                                    writer.object()
                                    writer.key(key)
                                    asShortObject(o, json, referencedIdProperty, referencedDomainClass)
                                    writer.endObject()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected void asShortObject(Object refObj, JSON json, PersistentProperty idProperty, PersistentEntity referencedDomainClass) throws ConverterException {
        Object idValue
        if (proxyHandler instanceof EntityProxyHandler) {
            idValue = ((EntityProxyHandler) proxyHandler).getProxyIdentifier(refObj)
            if (idValue == null) {
                idValue = extractValue(refObj, idProperty)
            }
        } else {
            idValue = extractValue(refObj, idProperty)
        }
        JSONWriter writer = json.getWriter()
        writer.object()
        writer.key("id").value(idValue)
        writer.endObject()
    }

    protected Object extractValue(Object domainObject, PersistentProperty property) {
        BeanWrapper beanWrapper = new BeanWrapperImpl(domainObject)
        return beanWrapper.getPropertyValue(property.getName())
    }

    protected boolean isRenderDomainClassRelations() {
        return false
    }

    static MarshallingContext getMarshallingContext() {
        return marshallingContext
    }


}
