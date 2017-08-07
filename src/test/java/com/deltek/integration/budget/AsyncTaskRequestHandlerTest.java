package com.deltek.integration.budget;

import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.deltek.integration.ApplicationConfiguration;
import com.deltek.integration.BrokerAndIntegrationConfiguration;
import com.deltek.integration.MaconomyAwareTest;
import com.deltek.integration.MaconomyTrafficLiveBudgetIntegrationApplication;
import com.deltek.integration.trafficlive.query.Comparator;
import com.deltek.integration.trafficlive.query.ComparatorType;
import com.deltek.integration.trafficlive.query.SearchCriteria;
import com.deltek.integration.trafficlive.service.TrafficLiveRestClient;
import com.sohnar.trafficlite.datamodel.enums.event.TrafficEmployeeEventType;
import com.sohnar.trafficlite.integration.data.TrafficEmployeeMessage;
import com.sohnar.trafficlite.integration.data.async.AsyncTaskMessage;
import com.sohnar.trafficlite.transfer.Identifier;
import com.sohnar.trafficlite.transfer.event.TrafficEmployeeEventTO;
import com.sohnar.trafficlite.transfer.project.JobTO;
import com.sohnar.trafficlite.transfer.trafficcompany.TrafficCompanySettingsTO;
import com.sohnar.trafficlite.transfer.trafficcompany.TrafficEmployeeTO;
import com.sohnar.trafficlite.transfer.trafficcompany.integration.ErpIntegrationSettingsTO;

@RunWith(SpringRunner.class)
@SpringBootTest(classes={MaconomyTrafficLiveBudgetIntegrationApplication.class, ApplicationConfiguration.class, BrokerAndIntegrationConfiguration.class})
public class AsyncTaskRequestHandlerTest {

	@Resource
	private AsyncTaskRequestHandler asyncTaskRequestHandler;
	
//	@Test
	public void retrieveTrafficLiveJobAndMerge() {
		asyncTaskRequestHandler.handleBackgroundTaskRequest(buildTestMessage());
	}

	private AsyncTaskMessage<ErpIntegrationSettingsTO, JobTO> buildTestMessage() {

		String userName = "redgum2k4+token@gmail.com";
		TrafficLiveRestClient tlc = new TrafficLiveRestClient(userName, 
				"p210pC2dOK6Y7e0SstciVXrOJycqd30GJRM0FIpj", 
				"https://stage-api.sohnar.com/TrafficLiteServer/openapi");

		//Find this employee.
		Optional<TrafficEmployeeTO> emp = tlc.employee().getAll(1, SearchCriteria.create().with(new Comparator(ComparatorType.EQ, "userName", userName))).stream().findAny();
		Assert.assertTrue(emp.isPresent());
		
		//Retrieve the Integration Settings.
		TrafficCompanySettingsTO companySettings =
				tlc.get("/staff/companysettings", TrafficCompanySettingsTO.class);
		
		//Load the test Job, then build the message from it.
		JobTO job = tlc.job().getById(2367396l);
		
		TrafficEmployeeEventTO<JobTO> event = new TrafficEmployeeEventTO<JobTO>(TrafficEmployeeEventType.BACKGROUND_TASK_REQUSTED, 
																				new Identifier(emp.get().getId()), 
																				job);
		TrafficEmployeeMessage<JobTO> employeeMessage = new TrafficEmployeeMessage<>(TrafficEmployeeEventType.BACKGROUND_TASK_REQUSTED,
				emp.get().getId(),
				"Maconomy Job Merge Message");
		employeeMessage.getTrafficEmployeeEvent().setUpdatedLightweightTO(job);
		AsyncTaskMessage<ErpIntegrationSettingsTO, JobTO> result = 
				new AsyncTaskMessage<ErpIntegrationSettingsTO, JobTO>("MACONOMY_BUDGET", employeeMessage, companySettings.getErpIntegrationSettings());
		return result;
	}
	
	
	

}
