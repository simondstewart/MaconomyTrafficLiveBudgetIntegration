package com.deltek.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.deltek.integration.budget.JobToBudgetService;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping;
import com.fasterxml.jackson.databind.SerializationFeature;

@Configuration
public class ApplicationConfiguration {

	@Bean
	public JobToBudgetService jobToBudgetService() {
		return new JobToBudgetService();
	}

	@Bean 
	public ObjectMapper objectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.enableDefaultTypingAsProperty(DefaultTyping.JAVA_LANG_OBJECT,
	    		JsonTypeInfo.Id.CLASS.getDefaultPropertyName());
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return objectMapper;
	}
	
}
