package com.deltek.integration;

import java.util.Arrays;

import org.dozer.DozerBeanMapper;
import org.dozer.loader.api.BeanMappingBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MappingConfiguration {

	@Bean
	DozerBeanMapper beanMapper() {
		DozerBeanMapper beanMapper = new DozerBeanMapper();
		//TODO here is how to install custom class converters.
		beanMapper.setCustomConverters(Arrays.asList());
		beanMapper.addMapping(beanMappingBuilder());
		return beanMapper;
	}
	
	@Bean
	public BeanMappingBuilder beanMappingBuilder() {
		return new BeanMappingBuilder() {
			
			@Override
			protected void configure() {
//				mapping(JobBudgetLine.class, AbstractLineItemTO.class);
//				mapping(JobBudgetLine.class, JobStageTO.class);
//				mapping(JobBudgetLine.class, JobTaskTO.class);
//				mapping(JobBudgetLine.class, JobThirdPartyCostTO.class);
//				mapping(JobBudgetLine.class, JobExpenseTO.class);
			}
		};
	}
	
}
