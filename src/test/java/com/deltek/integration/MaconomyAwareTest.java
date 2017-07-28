package com.deltek.integration;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import com.sohnar.trafficlite.transfer.trafficcompany.TrafficEmployeeTO;

@RunWith(value = Parameterized.class)
@SpringBootTest(classes=MaconomyTrafficLiveBudgetIntegrationApplication.class)
public class MaconomyAwareTest {
	
    @ClassRule
    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

	protected final Map<String, String> serverCfg;
    
	
	//An individual test run will be made for each item in the data() collection.
    public MaconomyAwareTest(Map<String, String> serverCfg) {
    	this.serverCfg = serverCfg;
    }

    @Parameters
    public static Collection<?> data() {
    	return Arrays.asList(
    		//Collection items need to be arrays signifying constructor arguments.
    		Arrays.asList(build222ServerConfig()).toArray(), 
    		Arrays.asList(build233ServerConfig()).toArray()
    		);
    }

    private static Map<String, String> build222ServerConfig() {
		Map<String, String> cfg = new HashMap<>();
		cfg.put("macRestURL", "http://193.17.206.162:4111/containers/v1/x1demo");
		cfg.put("user", "Administrator");
		cfg.put("pass", "123456");
		cfg.put("budgetType", "baseline");
		cfg.put("chargeBandExternalCode", "100");
		cfg.put("testJobNumber", "1020123");
		return cfg;
	}	

	private static Map<String, String> build233ServerConfig() {
		Map<String, String> cfg = new HashMap<>();
		cfg.put("macRestURL", "http://193.17.206.161:4111/containers/v1/xdemo1");
		cfg.put("user", "Administrator");
		cfg.put("pass", "123456");
		cfg.put("budgetType", "Reference");
		cfg.put("chargeBandExternalCode", "IoA, Fixed Price");
		cfg.put("testJobNumber", "10250003");
		return cfg ;
	}
	
	protected TrafficEmployeeTO createTestTrafficEmployee() {
		TrafficEmployeeTO employee = new TrafficEmployeeTO();
		employee.setUserName("simonstewart@deltek.com");
		return employee ;
	}
	
	@Test
	public void testPrintConfig() {
		System.out.println("Config: "+serverCfg);
	}
}
