package com.deltek.integration.budget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.testng.Assert;

import com.deltek.integration.budget.JobBudgetMergeActionBuilder.Action;
import com.deltek.integration.budget.JobBudgetMergeActionBuilder.BudgetLineAction;
import com.deltek.integration.maconomy.client.MaconomyRestClient;
import com.deltek.integration.maconomy.domain.CardTableContainer;
import com.deltek.integration.maconomy.domain.Record;
import com.deltek.integration.maconomy.psorestclient.MaconomyPSORestContext;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudget;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.sohnar.trafficlite.transfer.project.JobTO;
import com.sohnar.trafficlite.transfer.project.JobTaskTO;

public class JobToBudgetServiceTest {

	private final Log log = LogFactory.getLog(getClass());
	
	private MaconomyPSORestContext restClientContext;
	private JobToBudgetService jobToBudgetService;
	private IntegrationDetailsHolder integrationDetails;

	@Rule
	public ExpectedException expectedEx = ExpectedException.none();

	
	@Before
	public void setup() {
		 String macRestURL = "http://193.17.206.161:4111/containers/v1/x1demo";
		 String user = "Administrator";
		 String pass = "123456";
		 MaconomyRestClient mrc = new MaconomyRestClient(user, pass, 
				 									macRestURL);
		 restClientContext = new MaconomyPSORestContext(mrc);
		 jobToBudgetService = new JobToBudgetService();
		 integrationDetails = new IntegrationDetailsHolder(new HashMap<>(), 
				 macRestURL, 
				 	user, pass, "baseline", "remark10", "UTC");
	}
	
	@Test
	public void createBudgetLineActions() {
		CardTableContainer<JobBudget, JobBudgetLine> emptyBudget = clearBudget("1020123");
		List<BudgetLineAction> mergeActions = jobToBudgetService.createMergeLineActions(emptyBudget, 
													createJob(), 
													integrationDetails, 
													restClientContext);
		
		Assert.assertEquals(2, mergeActions.stream().filter(
											item -> Action.CREATE.equals(item.getAction()))
											.count());
	}
	
	private JobTO createJob() {
		JobTO job = new JobTO();
		job.getJobTasks().add(createJobTask("ONE"));
		job.getJobTasks().add(createJobTask("TWO"));
		return job ;
	}

	private JobTaskTO createJobTask(String description) {
		JobTaskTO jobTask = new JobTaskTO();
		jobTask.setUuid(UUID.randomUUID().toString());
		jobTask.setDescription(description);
		return jobTask ;
	}

	@Test
	public void executeActions() {
		CardTableContainer<JobBudget, JobBudgetLine> budgetData = clearBudget("1020123");
        
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
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", "1020123"));
        
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
	
	private CardTableContainer<JobBudget, JobBudgetLine> clearBudget(String string) {
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		restClientContext.jobBudget().data(String.format("jobnumber=%s", string));
        
        budgetData.card().getData().setShowbudgettypevar("Baseline");
        budgetData = restClientContext.jobBudget().update(budgetData.card());
        budgetData = restClientContext.jobBudget().postToAction("action:removebudget", budgetData.card());
        return budgetData;
	}

}
