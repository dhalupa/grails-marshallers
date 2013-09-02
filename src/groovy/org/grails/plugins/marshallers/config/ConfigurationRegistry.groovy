package org.grails.plugins.marshallers.config

import grails.converters.JSON

class ConfigurationRegistry {
	def root=[name:'default',registry:[:],children:[]]
	


	void registerConfig(Class domainClass,MarshallingConfig config){


		Queue q=[] as Queue
		q<<config
		while(!q.isEmpty()){
			MarshallingConfig c=q.poll()
			c.children.each{q<<it}
			doRegisterConfig(t,domainClass,c)
		}
	}

	private void doRegisterConfig(type,domainClass,config){
		def model=this["${type}Model"]
		def m
		config.resolvePath().each{p->
			def child=model.children.find{it.name==p}
			if(child==null){
				checkInheritanceConsistency(type, p)
				child=[name:p,parent:model,children:[],registry:[:]]
				model.children<<child
			}
			model=child
		}
		model.registry[domainClass]=config
	}

	private def checkInheritanceConsistency(type,name){
		def model=this["${type}Model"]
		Queue q=[] as Queue
		q<<model
		while(!q.isEmpty()){
			def c=q.poll()
			c.children.each{q<<it}
			if(c.name==name){
				throw new RuntimeException("${type} configuration hierarchy is not consistent for named config ${name}")
			}
		}
	}
}
