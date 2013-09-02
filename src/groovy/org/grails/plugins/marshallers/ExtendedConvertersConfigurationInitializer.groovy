/*******************************************************************************
 * Copyright 2011 Predrag Knezevic
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.grails.plugins.marshallers

import grails.converters.JSON
import grails.converters.XML
import groovy.util.logging.Log4j;

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClassUtils as GCU
import org.codehaus.groovy.grails.support.proxy.ProxyHandler
import org.codehaus.groovy.grails.web.converters.configuration.ConverterConfiguration;
import org.codehaus.groovy.grails.web.converters.configuration.ConvertersConfigurationHolder
import org.codehaus.groovy.grails.web.converters.configuration.DefaultConverterConfiguration
import org.codehaus.groovy.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.plugins.marshallers.config.ConfigurationRegistry;
import org.grails.plugins.marshallers.config.MarshallingConfig
import org.grails.plugins.marshallers.config.MarshallingConfigBuilder
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

/**
 * @author Predrag Knezevic
 * @version $Date: $
 */
@Log4j
class ExtendedConvertersConfigurationInitializer implements ApplicationContextAware {

	private ApplicationContext applicationContext
	private GrailsApplication application



	void initialize() {
		application=applicationContext.grailsApplication
		ProxyHandler proxyHandler = applicationContext.getBean(ProxyHandler.class)
		ConfigurationRegistry registry=createRegistry()
		[xml: XML, json: JSON].each { type, converterClass ->
			def marshallerCfg = application.config?.grails?.plugins?.marshallers?."${type}"
			processConfig(marshallerCfg, converterClass, type)
			processDomainClassConfig(type,converterClass,registry,proxyHandler)
		}

		
	}

	private ConverterConfiguration getConverterConfiguration(Class converterClass,String name){
		ConverterConfiguration c
		if(name=='default'){
			c= ConvertersConfigurationHolder.getConverterConfiguration(converterClass)
			if (! (c instanceof DefaultConverterConfiguration)) {
				c = new DefaultConverterConfiguration(c)
				ConvertersConfigurationHolder.setDefaultConfiguration(converterClass, c)
			}
		}else{
			c=ConvertersConfigurationHolder.getNamedConverterConfiguration(name, converterClass)
		}
		return c
	}

	private ObjectMarshaller getMarshaller(Class converterClass,proxyHandler, application,configCache){
		if(converterClass==JSON.class){
			return new GenericDomainClassJSONMarshaller(proxyHandler,application,configCache)
		}else{
			return new GenericDomainClassXMLMarshaller(proxyHandler, application,configCache)
		}
	}

	private ConfigurationRegistry createRegistry(){
		ConfigurationRegistry registry=new ConfigurationRegistry()
		application.domainClasses.each{
			Closure mc=GCU.getStaticPropertyValue(it.clazz,'marshalling')
			if(mc){
				MarshallingConfigBuilder builder=new MarshallingConfigBuilder()
				mc.delegate=builder
				mc.resolveStrategy=Closure.DELEGATE_FIRST
				mc()
				MarshallingConfig c=builder.config
				registry.registerConfig(it.clazz,c)
			}
		}
		registry
	}
	
	private void processDomainClassConfig(String type,Class converterClass,ConfigurationRegistry registry,proxyHandler){
		def model=registry["${type}Model"]
		Queue q=[] as Queue
		q<<model
		while(!q.isEmpty()){
			def c=q.poll()
			ConverterConfiguration converterConfig=getConverterConfiguration(converterClass,c.name)
			if(converterConfig==null){
				def parentConverter=getConverterConfiguration(converterClass, c.parent.name)
				converterConfig=new DefaultConverterConfiguration(parentConverter)
				ConvertersConfigurationHolder.setNamedConverterConfiguration(converterClass, c.name, converterConfig)
			}
			ObjectMarshaller marshaller=getMarshaller(converterClass,proxyHandler,application,c.registry)
			converterConfig.registerObjectMarshaller(marshaller)
		}
		
	}

	private void processConfig(cfg, Class converterClass, type) {
		def converterCfg = ConvertersConfigurationHolder.getConverterConfiguration(converterClass)
		def builder = new ConfigurationBuilder(type: type, applicationContext: applicationContext, cfg: converterCfg, log: log, converterClass: converterClass, cfgName: "default")
		builder.registerSpringMarshallers()
		if (cfg != null && cfg instanceof Closure) {
			cfg.delegate = builder
			cfg.resolveStrategy = Closure.DELEGATE_FIRST
			cfg.call()
		}
	}

	void setApplicationContext(ApplicationContext applicationContext){
		this.applicationContext=applicationContext
	}
}

