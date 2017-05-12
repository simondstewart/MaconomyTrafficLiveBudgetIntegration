package com.deltek.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.deltek.integration.budget.JobToBudgetService;

@Configuration
@ComponentScan
@EnableAutoConfiguration(exclude={HibernateJpaAutoConfiguration.class, DataSourceAutoConfiguration.class, XADataSourceAutoConfiguration.class})
public class MaconomyTrafficLiveBudgetIntegrationApplication {

	public static void main(String[] args) {
		SpringApplication.run(MaconomyTrafficLiveBudgetIntegrationApplication.class, args);
	}
	
	
}
