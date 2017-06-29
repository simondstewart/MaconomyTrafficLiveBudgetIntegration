package com.deltek.integration.budget;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.deltek.integration.ApplicationConfiguration;
import com.deltek.integration.MaconomyTrafficLiveBudgetIntegrationApplication;
import com.deltek.integration.budget.JobBudgetMergeActionBuilder.BudgetLineAction;
import com.deltek.integration.maconomy.client.APIContainerHelper;
import com.deltek.integration.maconomy.client.MaconomyRestClient;
import com.deltek.integration.maconomy.client.MaconomyRestClientException;
import com.deltek.integration.maconomy.configuration.MaconomyServerConfiguration;
import com.deltek.integration.maconomy.domain.CardTableContainer;
import com.deltek.integration.maconomy.domain.Endpoint;
import com.deltek.integration.maconomy.domain.FilterContainer;
import com.deltek.integration.maconomy.domain.FilterPanes;
import com.deltek.integration.maconomy.domain.Record;
import com.deltek.integration.maconomy.psorestclient.MaconomyPSORestContext;
import com.deltek.integration.maconomy.psorestclient.domain.EmployeeCard;
import com.deltek.integration.maconomy.psorestclient.domain.EmployeeTable;
import com.deltek.integration.maconomy.psorestclient.domain.HoursJournal;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudget;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.deltek.integration.maconomy.psorestclient.domain.Journal;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes=MaconomyTrafficLiveBudgetIntegrationApplication.class)
public class BudgetTest {

	private final Log log = LogFactory.getLog(getClass());
	
	private MaconomyPSORestContext restClientContext;
	
	private String testJobNumber;
	
	@Resource
	private JobToBudgetService jobToBudgetService;
	
	@Rule
	public ExpectedException expectedEx = ExpectedException.none();

	private String budgetType;

	@Before
	public void setup() {
//		 MaconomyRestClient mrc222 = new MaconomyRestClient("Administrator", "123456", 
//					"http://193.17.206.162:4111/containers/v1/x1demo");
//		 testJobNumber = "1020123"; 
//		 budgetType = "baseline";
		
		 MaconomyRestClient mrc233 = new MaconomyRestClient("Administrator", "123456", 
				 					"http://193.17.206.161:4111/containers/v1/xdemo1");
		 testJobNumber = "10250003";
		 budgetType = "Reference";
		 restClientContext = new MaconomyPSORestContext(mrc233);
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
        
        List<BudgetLineAction> lineActions = new ArrayList<>();
        Record<JobBudgetLine> budgetLine = restClientContext.jobBudget().initTable(budgetData.getPanes().getTable());
        budgetLine.getData().setText("ONE");
        lineActions.add(BudgetLineAction.create(budgetLine));
        lineActions.add(BudgetLineAction.create(budgetLine));
        lineActions.add(BudgetLineAction.create(budgetLine));
        lineActions.add(BudgetLineAction.create(budgetLine));
        lineActions.add(BudgetLineAction.create(budgetLine));
		jobToBudgetService.executeActions(restClientContext, lineActions);
		
		//Lets take the 5 line budget created, remove lines, update lines and create lines and see the effect on concurrency control.
        CardTableContainer<JobBudget, JobBudgetLine> createdBudget = 
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", testJobNumber));
        
        List<BudgetLineAction> cudActions = new ArrayList<>();
        cudActions.add(BudgetLineAction.delete(createdBudget.recordAt(0)));
        cudActions.add(BudgetLineAction.delete(createdBudget.recordAt(1)));
        Record<JobBudgetLine> updatedLineOne = createdBudget.recordAt(2);
        updatedLineOne.getData().setText("UPDATED ONE");
        cudActions.add(BudgetLineAction.update(updatedLineOne));
        Record<JobBudgetLine> updatedLineTwo = createdBudget.recordAt(3);
        updatedLineTwo.getData().setText("UPDATED TWO");
        cudActions.add(BudgetLineAction.update(updatedLineTwo));
        budgetLine.getData().setText("NEW");
        cudActions.add(BudgetLineAction.create(budgetLine));
        cudActions.add(BudgetLineAction.create(budgetLine));
        cudActions.add(BudgetLineAction.create(budgetLine));
        jobToBudgetService.executeActions(restClientContext, cudActions);
        
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
