package com.deltek.integration.budget;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.integration.annotation.ServiceActivator;

import com.deltek.integration.BrokerAndIntegrationConfiguration;
import com.deltek.integration.BrokerAndIntegrationConfiguration.EmployeeMessageGateway;
import com.deltek.integration.trafficlive.service.TrafficLiveRestClient;
import com.sohnar.trafficlite.datamodel.enums.event.TrafficEmployeeEventType;
import com.sohnar.trafficlite.integration.data.TrafficEmployeeMessage;
import com.sohnar.trafficlite.integration.data.async.AsyncTaskMessage;
import com.sohnar.trafficlite.transfer.BaseTO;
import com.sohnar.trafficlite.transfer.Identifier;
import com.sohnar.trafficlite.transfer.dbenums.TimeZoneTO;
import com.sohnar.trafficlite.transfer.financial.ChargeBandTO;
import com.sohnar.trafficlite.transfer.project.JobTO;
import com.sohnar.trafficlite.transfer.trafficcompany.TrafficCompanySettingsTO;
import com.sohnar.trafficlite.transfer.trafficcompany.integration.ErpIntegrationSettingsTO;
import com.sohnar.utils.TrafficStringEncryptionUtil;

public class AsyncTaskRequestHandler {

	@Resource
	private JobToBudgetService jobToBudgetService;
	
    @Resource
    private EmployeeMessageGateway employeeMessageGateway;
 
	private final ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(1);
	
	private final Random random = new Random();
	
	@ServiceActivator(inputChannel=BrokerAndIntegrationConfiguration.MACONOMY_BUDGET_ASYNC_REQUEST_CHANNEL)
	public void handleBackgroundTaskRequest(AsyncTaskMessage<ErpIntegrationSettingsTO, JobTO> asyncRequestMessage) {
		try {
			processBackgroundTaskRequest(asyncRequestMessage);
		} catch (Throwable t) {
			employeeMessageGateway.sendMessage(
					createErrorMessage(asyncRequestMessage.getEmployeeMessage().getTrafficEmployeeId(), 
							asyncRequestMessage.getEmployeeMessage().getTrafficEmployeeEvent().getUpdatedLightweightTO(), t));
		}
		
	}
    
	/**
	 * Accept the new Message, extract the Maconomy and TrafficLIVE API Credentials and Run the Merging Logic.
	 * 
	 * Update the Job once the merge succeds and fire off a success event to the topic so the user is notified of completion.
	 * 
	 * An Error event will also be fired if this logic fails for any reason.
	 * 
	 * @param asyncRequestMessage
	 */
	private void processBackgroundTaskRequest(AsyncTaskMessage<ErpIntegrationSettingsTO, JobTO> asyncRequestMessage) {
		JobTO job = asyncRequestMessage.getEmployeeMessage().getTrafficEmployeeEvent().getUpdatedLightweightTO();
		ErpIntegrationSettingsTO settings = asyncRequestMessage.getData();

		String apiPassword = TrafficStringEncryptionUtil.decrypt(settings.getTrafficLiveServicePassword());
		
		TrafficLiveRestClient tlc = new TrafficLiveRestClient(settings.getTrafficLiveServiceUser(), 
							apiPassword, 
							settings.getTrafficLiveServiceUrl());
		
		JobTO jobTO = tlc.job().getById(job.getId());
		jobTO = jobToBudgetService.mergeJobToMaconomyBudget(jobTO, createIntegrationDetailsHolder(tlc, settings));
		JobTO updatedJob = tlc.job().update(jobTO);

		//All good, broadcast a completion message back to the client.
		employeeMessageGateway.sendMessage(createCompleteMessage(
				TrafficEmployeeEventType.BACKGROUND_TASK_COMPLETE, asyncRequestMessage.getEmployeeMessage().getTrafficEmployeeId(), job, 
				"Test Maconomy Sync Background Task Completed for Job Number: "+job.getJobNumber()));
	}
	
	private IntegrationDetailsHolder createIntegrationDetailsHolder(TrafficLiveRestClient tlc,
			ErpIntegrationSettingsTO settings) {
		Map<Identifier, ChargeBandTO> chargeBands = tlc.chargeBands().getAll().stream().collect(
				Collectors.toMap(k -> new Identifier(k.getId()), Function.identity()));
		TrafficCompanySettingsTO companySettings =
				tlc.get("/staff/companysettings", TrafficCompanySettingsTO.class);
		Identifier tzId = companySettings.getTimeZoneId();
		TimeZoneTO companyTimezone = tlc.getById("/application/timezone", tzId.getId(), TimeZoneTO.class);
		IntegrationDetailsHolder integrationDetails = 
				new IntegrationDetailsHolder(chargeBands, 
						settings.getMaconomyRESTServiceURLBase(), 
						settings.getMaconomyServiceUser(), 
						TrafficStringEncryptionUtil.decrypt(settings.getMaconomyServicePassword()), 
						settings.getMaconomyBudgetType(), 
						settings.getMaconomyBudgetUuidProperty(), 
						companyTimezone.getStringId());
		return integrationDetails;
	}

	private void simulateMessageFlow(JobTO job, TrafficEmployeeMessage<JobTO> requestMessage) {
        executorService.submit(()-> {
			try {
				Thread.sleep(10 * 1000);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if(random.nextBoolean()) {
				//This only simulated the interaction.  The processor will handle this event - there will be no user context.
				employeeMessageGateway.sendMessage(createCompleteMessage(
						TrafficEmployeeEventType.BACKGROUND_TASK_COMPLETE, requestMessage.getTrafficEmployeeId(), job, 
						"Test Maconomy Sync Background Task Completed for Job Number: "+job.getJobNumber()));
			} else {
				employeeMessageGateway.sendMessage(
						createErrorMessage(requestMessage.getTrafficEmployeeId(), job, new RuntimeException("Error with Simulation!")));
			}
		});
	}

	//Broadcast a complete back to the user.
	private <TO extends BaseTO> TrafficEmployeeMessage<TO> createCompleteMessage(TrafficEmployeeEventType backgroundTaskComplete,
			Long trafficEmployeeId, TO to, String description) {
		TrafficEmployeeMessage<TO> completeMessage = 
				new TrafficEmployeeMessage<>(TrafficEmployeeEventType.BACKGROUND_TASK_COMPLETE, trafficEmployeeId, description);
		completeMessage.getTrafficEmployeeEvent().setUpdatedLightweightTO(to);
		return completeMessage ;
	}

	//Broadcast an error back to the user.
	private <TO extends BaseTO> TrafficEmployeeMessage<TO> createErrorMessage(Long trafficEmployeeId, TO item, Throwable throwable) {
		TrafficEmployeeMessage<TO> message = new TrafficEmployeeMessage<>(TrafficEmployeeEventType.BACKGROUND_TASK_ERROR, 
												trafficEmployeeId, "Error Encountered During Maconomy Integation Background Processing.");
		message.setException(new BudgetIntegrationException("Error Encountered During Maconomy Integation Background Processing.", throwable));
		message.getTrafficEmployeeEvent().setUpdatedLightweightTO(item);
		return message;
	}
}
