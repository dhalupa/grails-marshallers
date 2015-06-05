package marshallers

import grails.plugins.*
import org.grails.plugins.marshallers.ExtendedConvertersConfigurationInitializer
import org.grails.plugins.marshallers.JsonMarshallerArtefactHandler
import org.grails.plugins.marshallers.XmlMarshallerArtefactHandler

class MarshallersGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.0.1 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def artefacts = [
            XmlMarshallerArtefactHandler,
            JsonMarshallerArtefactHandler
    ]

    def watchedResources = [
            'file:./grails-app/domain/*.groovy',
    ]

    def author = "Predrag Knezevic"
    def authorEmail = "pedjak@gmail.com"
    def title = "Easy Custom XML and JSON Marshalling for Grails Converters"
    def description = '''\\
Easy registration and usage of custom XML and JSON marshallers supporting hierarchical configurations.

Further documentation can be found on the GitHub repo.
'''
    def profiles = ['web']


    def documentation = "https://github.com/dhalupa/grails-marshallers"
    def scm = [url: "https://github.com/dhalupa/grails-marshallers"]

    def license = "APACHE"
    def developers = [[name: "Denis Halupa", email: "denis.halupa@gmail.com"], [name: "Angel Ruiz", email: "aruizca@gmail.com"]]


    Closure doWithSpring() {
        { ->
            extendedConvertersConfigurationInitializer(ExtendedConvertersConfigurationInitializer)
            ["xml", "json"].each { type ->
                grailsApplication."${type}MarshallerClasses".each { marshallerClass ->
                    "${marshallerClass.fullName}"(marshallerClass.clazz) { bean ->
                        bean.autowire = "byName"
                    }
                }
            }
        }
    }

    void doWithDynamicMethods() {
        applicationContext.extendedConvertersConfigurationInitializer.initialize()
        log.debug "Marshallers Plugin configured successfully"
    }

    void doWithApplicationContext() {
        // TODO Implement post initialization spring config (optional)
    }

    void onChange(Map<String, Object> event) {
        event.ctx.extendedConvertersConfigurationInitializer.initialize()
    }


}
