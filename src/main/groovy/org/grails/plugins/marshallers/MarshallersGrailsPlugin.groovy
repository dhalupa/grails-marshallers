package org.grails.plugins.marshallers

import grails.core.ArtefactHandler
import grails.plugins.Plugin
import groovy.util.logging.Log4j

@Log4j
class MarshallersGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.0.16 > *"
    // resources that are excluded from plugin packaging

    def profiles = ['web']

    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    def scm = [url: "https://github.com/pedjak/grails-marshallers"]
    def licence = "APACHE"

    List<ArtefactHandler> artefacts = [
            XmlMarshallerArtefactHandler,
            JsonMarshallerArtefactHandler
    ]

    def watchedResources = [
            'file:./grails-app/domain/*.groovy',
            'file:./grails-app/conf/Config.groovy'
    ]

    def author = "Predrag Knezevic"
    def authorEmail = "pedjak@gmail.com"

    def developers = [[name: "Denis Halupa", email: "denis.halupa@gmail.com"], [name: "Angel Ruiz", email: "aruizca@gmail.com"]]

    def title = "Easy Custom XML and JSON Marshalling for Grails Converters"
    def description = '''\\
Easy registration and usage of custom XML and JSON marshallers supporting hierarchical configurations.
Further documentation can be found on the GitHub repo.
'''

    // URL to the plugin's documentation
    def documentation = "https://github.com/pedjak/grails-marshallers"

    Closure doWithSpring() {
        { ->
            extendedConvertersConfigurationInitializer(ExtendedConvertersConfigurationInitializer)
            ["xml", "json"].each { type ->
                application."${type}MarshallerClasses".each { marshallerClass ->
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

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
