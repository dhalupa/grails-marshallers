package org.grails.plugins.marshallers


import grails.testing.gorm.DataTest
import org.grails.plugins.marshallers.config.MarshallingConfigBuilder
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer
import spock.lang.Specification

class MarshallingConfigSpec extends Specification implements DataTest {

    def setup() {
        // this has to be called first as there's no declarative way to enforce
        // execution order
        //initGrailsApplication()

        grailsApplication.registerArtefactHandler(new JsonMarshallerArtefactHandler())
        grailsApplication.registerArtefactHandler(new XmlMarshallerArtefactHandler())
        defineBeans {
            convertersConfigurationInitializer(ConvertersConfigurationInitializer)
            extendedConvertersConfigurationInitializer(ExtendedConvertersConfigurationInitializer)
        }
        applicationContext.convertersConfigurationInitializer.initialize()
        applicationContext.extendedConvertersConfigurationInitializer.initialize()
    }

    class A {

    }
    static def testConfig = {
        xml {
            'default' {
                identifier 'some', 'id'
                elementName 'elName'
                attributes 'some', 'some1'
                ignore 'ig', 'big'
            }
            named {
                identifier { val, xml -> println val }
                attributes 'some', 'some1'
                ignore 'ig', 'big'
                serializer {
                    taxonomies { val, xml -> println val }
                }
                virtual {
                    rootNodes { form, xml -> println form }
                }
            }
        }
        json {
            'default' { ignore 'some', 'some1' }
        }
    }


    def "can build marshalling config"() {
        given:
        def cl = {
            identifier 'uuid', 'id'
            shouldOutputClass true
            json {
                shouldOutputClass false
                export {
                    identifier 'id'
                    virtual {
                        some {
                        }
                        other {
                        }
                    }
                    restrictedExport {
                        identifier 'uuid'

                    }
                }
            }
            xml { elementName 'test' }
            some {}
        }

        MarshallingConfigBuilder bldr = new MarshallingConfigBuilder(A)
        cl.delegate = bldr
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        when:
        cl()
        def config = bldr.config
        then:
        config.clazz == A
        config.shouldOutputClass
        config.identifier == ['uuid', 'id']
        config.children.size() == 3
        config.children[0].type == 'json'
        config.children[0].children[0].identifier == ['id']
        config.children[0].children[0].type == 'json'
        config.children[0].children[0].name == 'export'
        config.children[1].elementName == 'test'
        config.findConfigNames('json') == ['default', 'export', 'restrictedExport', 'some'] as Set
        config.findConfigNames('xml') == ['default', 'some'] as Set
        config.findNamedConfig('json', 'export') != null
        def dc = config.findNamedConfig('json', 'default')
        dc != null
        dc.identifier == ['uuid', 'id']
        !dc.shouldOutputClass
        config.findNamedConfig('json', 'export').identifier == ['id']
        config.findNamedConfig('json', 'export').virtual.size() == 2
        config.findNamedConfig('json', 'restrictedExport').identifier == ['uuid']
    }

    def "can build marshalling config without type specified"() {
        given:
        def cl = {
            export {
                identifier 'id'
                restrictedExport { identifier 'uuid' }
            }
        }
        MarshallingConfigBuilder bldr = new MarshallingConfigBuilder(A)
        cl.delegate = bldr
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        when:
        cl()
        def config = bldr.config
        then:
        config.findNamedConfig('json', 'export').identifier == ['id']
        config.findNamedConfig('json', 'restrictedExport').identifier == ['uuid']
    }

    def "can build marshalling config with default configuration"() {
        given:
        def cl = { identifier 'id' }
        MarshallingConfigBuilder bldr = new MarshallingConfigBuilder(A)
        cl.delegate = bldr
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        when:
        cl()
        def config = bldr.config
        then:
        config.findNamedConfig('json', 'default').identifier == ['id']

    }

    def "can build marshalling config for empty config"() {
        given:
        def cl = { json { smart {} } }
        MarshallingConfigBuilder bldr = new MarshallingConfigBuilder(A)
        cl.delegate = bldr
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        when:
        cl()
        def config = bldr.config
        then:
        config.findNamedConfig('json', 'smart') != null

    }
}


