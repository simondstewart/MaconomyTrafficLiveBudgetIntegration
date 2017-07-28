package com.deltek.integration.budget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.deltek.integration.MaconomyAwareTest;
import com.deltek.integration.budget.JobBudgetMergeActionRequestBuilder.BudgetLineActionRequest;
import com.deltek.integration.maconomy.client.MaconomyRestClient;
import com.deltek.integration.maconomy.domain.CardTableContainer;
import com.deltek.integration.maconomy.domain.Endpoint;
import com.deltek.integration.maconomy.domain.FilterContainer;
import com.deltek.integration.maconomy.domain.FilterPanes;
import com.deltek.integration.maconomy.domain.Record;
import com.deltek.integration.maconomy.psorestclient.MaconomyPSORestContext;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudget;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.sohnar.trafficlite.transfer.Identifier;
import com.sohnar.trafficlite.transfer.financial.ChargeBandTO;

public class BudgetTest extends MaconomyAwareTest {

	private final Log log = LogFactory.getLog(getClass());
	
	private MaconomyPSORestContext restClientContext;
	private String testJobNumber;
	private String budgetType;
	
	@Resource
	private JobToBudgetService jobToBudgetService;
	
	@Rule
	public ExpectedException expectedEx = ExpectedException.none();

	private IntegrationDetailsHolder integrationDetails;

	public BudgetTest(Map<String, String> serverCfg) {
		super(serverCfg);
	}

	@Before
	public void setup() {

		//TODO - This is duplicated between here and the other test case
		 Map<Identifier, ChargeBandTO> chargeBandMap = new HashMap<>();

		 //Integration needs valid chargebands with external codes.
		 ChargeBandTO chargeBand = new ChargeBandTO();
		 chargeBand.setExternalCode(serverCfg.get("chargeBandExternalCode"));
		 //Blank Chargeband
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
		budgetType = serverCfg.get("budgetType");
		
	}
	
	@Test
	public void testBudgetFilter() {
		Endpoint budgetEndpoint = restClientContext.jobBudget().endPoint();
		Assert.assertNotNull(budgetEndpoint);
		FilterContainer<JobBudget> filterResponse = restClientContext.jobBudget().filter();
		Assert.assertNotNull(filterResponse);
		Assert.assertTrue(filterResponse.getPanes() instanceof FilterPanes);
		
		JobBudget firstBudgetRecord = filterResponse.getPanes().getFilter().getRecords().get(0).getData();
		firstBudgetRecord.getTransactiontimestamp();
		firstBudgetRecord.getAccountmanagernumber();
		firstBudgetRecord.getCreateddate();
		
	}
	
	@Test
	public void getDataThenUpdate() {
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", testJobNumber));
        
        Record<JobBudget> budget = budgetData.getPanes().getCard().getRecords().get(0);
        budget.getData().setShowbudgettypevar(budgetType);
        budgetData = restClientContext.jobBudget().update(budget);
	}
	
	@Test
	public void executeActions() {
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", testJobNumber));
        
        budgetData.card().getData().setShowbudgettypevar(budgetType);
        budgetData = restClientContext.jobBudget().update(budgetData.card());
        budgetData = restClientContext.jobBudget().postToAction("action:removebudget", budgetData.card());
        
        List<BudgetLineActionRequest> lineActions = new ArrayList<>();
        Record<JobBudgetLine> budgetLine = restClientContext.jobBudget().initTable(budgetData.getPanes().getTable());
        budgetLine.getData().setText("ONE");
        lineActions.add(BudgetLineActionRequest.create(budgetLine));
        lineActions.add(BudgetLineActionRequest.create(budgetLine));
        lineActions.add(BudgetLineActionRequest.create(budgetLine));
        lineActions.add(BudgetLineActionRequest.create(budgetLine));
        lineActions.add(BudgetLineActionRequest.create(budgetLine));
		jobToBudgetService.executeActions(restClientContext, integrationDetails, budgetData, lineActions);
		
		//Lets take the 5 line budget created, remove lines, update lines and create lines and see the effect on concurrency control.
        CardTableContainer<JobBudget, JobBudgetLine> createdBudget = 
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", testJobNumber));
        
        List<BudgetLineActionRequest> cudActions = new ArrayList<>();
        cudActions.add(BudgetLineActionRequest.delete(createdBudget.recordAt(0)));
        cudActions.add(BudgetLineActionRequest.delete(createdBudget.recordAt(1)));
        Record<JobBudgetLine> updatedLineOne = createdBudget.recordAt(2);
        updatedLineOne.getData().setText("UPDATED ONE");
        cudActions.add(BudgetLineActionRequest.update(updatedLineOne));
        Record<JobBudgetLine> updatedLineTwo = createdBudget.recordAt(3);
        updatedLineTwo.getData().setText("UPDATED TWO");
        cudActions.add(BudgetLineActionRequest.update(updatedLineTwo));
        budgetLine.getData().setText("NEW");
        cudActions.add(BudgetLineActionRequest.create(budgetLine));
        cudActions.add(BudgetLineActionRequest.create(budgetLine));
        cudActions.add(BudgetLineActionRequest.create(budgetLine));
        jobToBudgetService.executeActions(restClientContext, integrationDetails, createdBudget, cudActions);
        
	}
 
	@Test
	public void createMultipleLines() {
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", testJobNumber));
        
        budgetData.card().getData().setShowbudgettypevar(budgetType);
        budgetData = restClientContext.jobBudget().update(budgetData.card());
        budgetData = restClientContext.jobBudget().postToAction("action:removebudget", budgetData.card());
        
        Record<JobBudgetLine> budgetLine = restClientContext.jobBudget().initTable(budgetData.getPanes().getTable());
        budgetLine.getData().setText("ONE");
        budgetData = restClientContext.jobBudget().create(budgetLine);

        budgetLine.getData().setText("TWO");
        budgetLine.getData().setLinenumber(1);
        budgetData = restClientContext.jobBudget().create(budgetLine);
        JobBudgetLine secondLineData = budgetData.lastRecord().getData();
        Assert.assertEquals("TWO", secondLineData.getText());
        Assert.assertEquals(new Integer(2), secondLineData.getLinenumber());
        
        //By following the create action on the existing line item, a new item is created at that index. 
//        Record<JobBudgetLine> secondLine = budgetData.lastRecord();
        budgetLine.getData().setText("THREE");
        budgetLine.getData().setParentjobbudgetlineinstancekey(secondLineData.getInstancekey());
        budgetData = restClientContext.jobBudget().create(budgetLine);

        Record<JobBudgetLine> thirdLine = budgetData.lastRecord();
        Assert.assertEquals("THREE", thirdLine.getData().getText());
        
        //Update THREE to sit under ONE
        thirdLine.getData().setParentjobbudgetlineinstancekey(budgetData.recordAt(0).getData().getInstancekey());
        restClientContext.jobBudget().update(thirdLine);
        
        
        
        
        
	}
	
	
	@Test
	public void reopenBudgetDeleteLines() {
		
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", testJobNumber));

//        budgetData = restClientContext.jobBudget().postToAction("action:reopenbudget", budgetData.card());
        budgetData = restClientContext.jobBudget().postToAction("action:removebudget", budgetData.card());
        
        Record<JobBudgetLine> budgetLine = restClientContext.jobBudget().initTable(budgetData.getPanes().getTable());
        budgetLine.getData().setText("Simons Sample Line");
        budgetData = restClientContext.jobBudget().create(budgetLine);
        Record<JobBudgetLine> newRecord = budgetData.tableRecords().get(0);
        newRecord.getData().setText("UPDATED LINE");
        
        CardTableContainer<JobBudget, JobBudgetLine> updatedBudget = restClientContext.jobBudget().update(newRecord);
        
        Record<JobBudgetLine> budgetLineToDelete = updatedBudget.tableRecords().get(0);
        
        //Create a new line.
        budgetLine.getData().setText("Simons Sample Line TWO");
        budgetData = restClientContext.jobBudget().postToAction("action:create", budgetLine);

        //Delete an existing line.
        budgetData = restClientContext.jobBudget().deleteTableRecord(budgetLineToDelete);
        

	}
}
