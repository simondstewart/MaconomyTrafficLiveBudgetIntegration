package com.deltek.integration.budget;

import static com.deltek.integration.budget.domainmapper.ConversionConstants.DATE_TIME_FORMAT;

import java.util.Calendar;
import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.deltek.integration.budget.JobBudgetMergeActionRequestBuilder.BudgetLineActionRequest;
import com.deltek.integration.maconomy.client.MaconomyRestClient;
import com.deltek.integration.maconomy.client.MaconomyRestClientException;
import com.deltek.integration.maconomy.domain.CardTableContainer;
import com.deltek.integration.maconomy.domain.Record;
import com.deltek.integration.maconomy.psorestclient.MaconomyPSORestContext;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudget;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.sohnar.trafficlite.transfer.project.JobTO;

public class JobToBudgetService {

	private static final Log LOG = LogFactory.getLog(JobToBudgetService.class);
	
    @Resource(name="objectMapper")
    private ObjectMapper objectMapper;
    
    /**
     * Push the TrafficLIVE Job State to a Maconomy Budget.
     * 
     * This method will attempt to syncronise the JobTO state from the parameter into the 
     * corresponding Maconomy Budget whose budget type is defined in the IntegrationDetailsHolder.
     * 
     * If the job is being synced for the first time, then a new budget will be created.  On subsequent
     * calls to this method for the same job, any changes on the job will be merged into the budget.
     * 
     * Existing budget lines will be removed if no longer in the job, or updated accordingly if their values have changed.
     * 
     * In addition, new lines created on the job non-existent in the Budget will be created.
     * 
     */
	public JobTO mergeJobToMaconomyBudget(JobTO trafficJob, IntegrationDetailsHolder integrationDetails) {
		
        MaconomyPSORestContext mrc = buildMaconomyContext(integrationDetails);
        String maconomyJobNumber = trafficJob.getExternalCode();
        
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		mrc.jobBudget().data(String.format("jobnumber=%s", maconomyJobNumber));

        budgetData = updateBudgetType(budgetData, integrationDetails.getMaconomyBudgetType(), mrc);

        budgetData = openBudget(budgetData, mrc);
        
        budgetData = buildAndExecuteMergeActions(budgetData, trafficJob, integrationDetails, mrc);
        budgetData.card().getData()
                .setRevisionremark1var(String.format("Synced at %s (UTC) from TrafficLIVE  by %s", 
                		DATE_TIME_FORMAT.format(Calendar.getInstance().getTime()), integrationDetails.getRequestEmployee().getUserName()));
        budgetData = mrc.jobBudget().update(budgetData.card());
        budgetData = mrc.jobBudget().postToAction("action:submitbudget", budgetData.card());
        budgetData = mrc.jobBudget().postToAction("action:approvebudget", budgetData.card());
        trafficJob.setExternalData(jsonLastUpdatedObject("Last budget sent:"));
        return trafficJob;
    }
    
	public CardTableContainer<JobBudget, JobBudgetLine> buildAndExecuteMergeActions(CardTableContainer<JobBudget, JobBudgetLine> budgetData,
																			JobTO job,
																			IntegrationDetailsHolder integrationDetails, 
																			MaconomyPSORestContext mrc) {
		
		List<BudgetLineActionRequest> lineActions = createMergeLineActions(budgetData, job, integrationDetails, mrc);
		
		//Populate any Hierarchy relationships that exist.  It would be nice if the ActionBuilder could do this
		//but the granularity of the Action prevents it.
		JobBudgetActionHierarchyPreProcessor hierarchyProcessor =
				new JobBudgetActionHierarchyPreProcessor(job, budgetData, lineActions, integrationDetails);
		
		lineActions = hierarchyProcessor.process();
		if(lineActions.isEmpty()) 
			return budgetData;
		
		return executeActions(mrc, integrationDetails, budgetData, lineActions);

	}

	public List<BudgetLineActionRequest> createMergeLineActions(CardTableContainer<JobBudget, JobBudgetLine> budgetData,
			JobTO job, IntegrationDetailsHolder integrationDetails, MaconomyPSORestContext mrc) {

		Record<JobBudgetLine> templateLine = mrc.jobBudget().initTable(budgetData.getPanes().getTable());

		// Generate a collection of lines actions, based on the state to be merged.
		JobBudgetMergeActionRequestBuilder actionBuilder = new JobBudgetMergeActionRequestBuilder(job, budgetData, objectMapper,
				templateLine, integrationDetails);

		List<BudgetLineActionRequest> lineActions = actionBuilder.deletes();
		lineActions.addAll(actionBuilder.creates());
		lineActions.addAll(actionBuilder.updates());

		return lineActions;
	}

	/**
	 * Execution of the order of actions is important.  We make the following assumptions:
	 * Safe to execute all DELETES first (this will reduce the payload size and fail fast if a line cannot be deleted)
	 * Then we execute all CREATES - so new lines will exist on maconomy.
	 * Finally UDPATES, so new lines (if moved, can link to either existing or new lines)
	 * Store the state of the container, as we need to refresh the concurrency control for every line.
	 * @param integrationDetails 
	 * @param budgetData 
	 */
	 public CardTableContainer<JobBudget, JobBudgetLine> executeActions(MaconomyPSORestContext mrc, 
			 																IntegrationDetailsHolder integrationDetails, 
			 																CardTableContainer<JobBudget, JobBudgetLine> budgetData, 
			 																List<BudgetLineActionRequest> lineActions) {
		CardTableContainer<JobBudget, JobBudgetLine> updatedBudget = budgetData;
		ActionRequestProcessor actionProcessor = new ActionRequestProcessor(integrationDetails, objectMapper);
		
		for(BudgetLineActionRequest action : lineActions) {

			updatedBudget = actionProcessor.executeAction(mrc, integrationDetails, action, updatedBudget, lineActions);
			
		}
		return updatedBudget;
	} 
	 
    private MaconomyPSORestContext buildMaconomyContext(IntegrationDetailsHolder integrationDetails) {
    	MaconomyRestClient client = new MaconomyRestClient(integrationDetails.getMacaonomyUser(), 
    														integrationDetails.getMacaonomyPassword(),
    														integrationDetails.getMacaonomyRestServiceURLBase());
    	return new MaconomyPSORestContext(client);
    	
    }

	private String jsonLastUpdatedObject(String label){
        JsonObject lastUpdateObject = new JsonObject();
        lastUpdateObject.add("label", new JsonPrimitive(label));
        lastUpdateObject.add("date", new JsonPrimitive(Calendar.getInstance().getTimeInMillis()));
        Gson gsonParser = new Gson();
        return gsonParser.toJson(lastUpdateObject);
    }

    public CardTableContainer<JobBudget, JobBudgetLine> updateBudgetType(CardTableContainer<JobBudget, JobBudgetLine> budgetData, 
    														String budgetType, MaconomyPSORestContext mrc) {
        Record<JobBudget> budget = budgetData.card();

        if (!StringUtils.equals(budget.getData().getShowbudgettypevar(), budgetType)) {
            budget.getData().setShowbudgettypevar(budgetType);
            budgetData = mrc.jobBudget().update(budget);
        }

        return budgetData;
    }

    public CardTableContainer<JobBudget, JobBudgetLine> openBudget(CardTableContainer<JobBudget, JobBudgetLine> budgetData, MaconomyPSORestContext mrc) {
        if(budgetData.card().hasAction("action:reopenbudget")) {
        	budgetData = mrc.jobBudget().postToAction("action:reopenbudget", budgetData.card());
        }
        return budgetData;
    }
    
}
