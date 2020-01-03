package com.deltek.integration.budget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.deltek.integration.MaconomyAwareTest;
import com.deltek.integration.budget.JobBudgetMergeActionRequestBuilder.BudgetLineActionRequest;
import com.deltek.integration.domainhelper.JobTaskHelper;
import com.deltek.integration.maconomy.client.MaconomyRestClient;
import com.deltek.integration.maconomy.domain.CardTableContainer;
import com.deltek.integration.maconomy.domain.Record;
import com.deltek.integration.maconomy.psorestclient.MaconomyPSORestContext;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudget;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sohnar.trafficlite.transfer.Identifier;
import com.sohnar.trafficlite.transfer.financial.ChargeBandTO;
import com.sohnar.trafficlite.transfer.financial.MoneyTO;
import com.sohnar.trafficlite.transfer.financial.PrecisionMoneyTO;
import com.sohnar.trafficlite.transfer.project.JobTO;
import com.sohnar.trafficlite.transfer.project.JobTaskTO;

public class MaconomyClobberZeroCreateTest extends MaconomyAwareTest {

	private final Log LOG = LogFactory.getLog(getClass());
	
	@Resource
	private JobToBudgetService jobToBudgetService;

	@Resource
	private ObjectMapper objectMapper;
	
	private MaconomyPSORestContext restClientContext;
	private IntegrationDetailsHolder integrationDetails;

	@Rule
	public ExpectedException expectedEx = ExpectedException.none();

	private String testJobNumber;

	public MaconomyClobberZeroCreateTest(Map<String, String> serverCfg) {
		super(serverCfg);
	}
	
	@Before
	public void setup() {
		 
		 Map<Identifier, ChargeBandTO> chargeBandMap = new HashMap<>();

		 //Integration needs valid chargebands with external codes.
		 ChargeBandTO chargeBand = new ChargeBandTO();
		 chargeBand.setExternalCode(serverCfg.get("chargeBandExternalCode"));
		 //Blank ChargebandExternalCode
		 chargeBand.setSecondaryExternalCode("");
		 chargeBandMap.put(new Identifier(1), chargeBand);
		 integrationDetails = new IntegrationDetailsHolder(
				 chargeBandMap, 
				 serverCfg.get("macRestURL"), 
				 serverCfg.get("user"), 
				 serverCfg.get("pass"), 
				 serverCfg.get("budgetType"), 
				 	"remark10", 
				 	"UTC", 
				 	createTestTrafficEmployee());
		 
		 MaconomyRestClient mrc = new MaconomyRestClient(integrationDetails.getMacaonomyUser(), 
				 integrationDetails.getMacaonomyPassword(), 
				 integrationDetails.getMacaonomyRestServiceURLBase());
		 restClientContext = new MaconomyPSORestContext(mrc);

		 testJobNumber = serverCfg.get("testJobNumber");

	}
	
	@Test
	public void createZeroMonetaryLine() {
		JobTO updatedJob = 
				jobToBudgetService.mergeJobToMaconomyBudget(createJob(testJobNumber), integrationDetails);
		
	}
	
	@Test
	public void zeroLine() {
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", testJobNumber));

        budgetData = jobToBudgetService.updateBudgetType(budgetData, integrationDetails.getMaconomyBudgetType(), restClientContext);
        budgetData = jobToBudgetService.openBudget(budgetData, restClientContext);
        budgetData = jobToBudgetService.buildAndExecuteMergeActions(budgetData, createJob(testJobNumber), integrationDetails, restClientContext);
        
	}
	
	private JobTO createJob(String externalCode) {
		JobTO job = new JobTO();
		job.setExternalCode(externalCode);

		JobTaskTO one =
				JobTaskHelper.builder()
				.randomUUID()
				.description("ONE")
				.cost(MoneyTO.buildDefaultMoney(0f))
				.rate(PrecisionMoneyTO.buildDefaultMoney(0f))
				.lineItemOrder(1)
				.hierarchyOrder(1)
				.quantity(BigDecimal.ONE)
				.chargeBandId(new Identifier(1))
				.build()
				.create();

		job.getJobTasks().add(one);
		return job;
	}
}
