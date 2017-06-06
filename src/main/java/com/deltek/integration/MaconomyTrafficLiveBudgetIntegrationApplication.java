package com.deltek.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.solr.SolrAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.deltek.integration.budget.JobToBudgetService;

@Configuration
@ComponentScan
//Autoconfig needs careful exclusion, as the imports from our dependencies
//(e.g. TrafficLiveSharedData for example which could be made much more lightweight)
//contain autoconfig classes that are automatically bootstrapped.
@EnableAutoConfiguration(exclude={HibernateJpaAutoConfiguration.class, 
								  DataSourceAutoConfiguration.class, 
								  XADataSourceAutoConfiguration.class,
								  SolrAutoConfiguration.class})
public class MaconomyTrafficLiveBudgetIntegrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(MaconomyTrafficLiveBudgetIntegrationApplication.class, args);
	}
	
	
}
