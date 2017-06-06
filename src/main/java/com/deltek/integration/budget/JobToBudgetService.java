package com.deltek.integration.budget;

import static com.deltek.integration.budget.domainmapper.ConversionConstants.DATE_TIME_FORMAT;

import java.util.Calendar;
import java.util.List;
import java.util.Optional;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.deltek.integration.budget.JobBudgetMergeActionBuilder.BudgetLineAction;
import com.deltek.integration.maconomy.client.MaconomyRestClient;
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
	public JobTO mergeJobToMaconomyBudget(JobTO trafficJob, IntegrationDetailsHolder integrationSettings) {

		if(LOG.isInfoEnabled()) {
			LOG.info("Attempting to merge TrafficLIVE Job Number: " +trafficJob.getJobNumber() 
			+ " with Maconomy server: "+integrationSettings.getMacaonomyRestServiceURLBase());
		}
		
        MaconomyPSORestContext mrc = buildMaconomyContext(integrationSettings);
        String maconomyJobNumber = trafficJob.getExternalCode();
        
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		mrc.jobBudget().data(String.format("jobnumber=%s", maconomyJobNumber));

        budgetData = updateBudgetType(budgetData, integrationSettings.getMaconomyBudgetType(), mrc);

        if(budgetData.card().hasAction("action:reopenbudget")) {
        	budgetData = mrc.jobBudget().postToAction("action:reopenbudget", budgetData.card());
        }
        
        budgetData = buildAndExecuteMergeActions(budgetData, trafficJob, integrationSettings, mrc);
        budgetData.card().getData()
                .setRevisionremark1var(String.format("Synced at %s (UTC) from TrafficLIVE  by %s", 
                		DATE_TIME_FORMAT.format(Calendar.getInstance().getTime()), getCurrentUserName()));
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
		
		List<BudgetLineAction> lineActions = createMergeLineActions(budgetData, job, integrationDetails, mrc);
		
		//Populate any Hierarchy relationships that exist.  It would be nice if the ActionBuilder could do this
		//but the granularity of the Action prevents it.
		JobBudgetActionHierarchyPreProcessor hierarchyProcessor =
				new JobBudgetActionHierarchyPreProcessor(job, budgetData, lineActions, integrationDetails);
		
		lineActions = hierarchyProcessor.process();
		if(lineActions.isEmpty()) 
			return budgetData;
		
		budgetData = executeActions(mrc, lineActions);
		return budgetData;
	}

	public List<BudgetLineAction> createMergeLineActions(CardTableContainer<JobBudget, JobBudgetLine> budgetData,
			JobTO job, IntegrationDetailsHolder integrationDetails, MaconomyPSORestContext mrc) {

		Record<JobBudgetLine> templateLine = mrc.jobBudget().initTable(budgetData.getPanes().getTable());

		// Generate a collection of lines actions, based on the state to be merged.
		JobBudgetMergeActionBuilder actionBuilder = new JobBudgetMergeActionBuilder(job, budgetData, objectMapper,
				templateLine, integrationDetails);

		List<BudgetLineAction> lineActions = actionBuilder.deletes();
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
	 */
	 public CardTableContainer<JobBudget, JobBudgetLine> executeActions(MaconomyPSORestContext mrc, List<BudgetLineAction> lineActions) {
		CardTableContainer<JobBudget, JobBudgetLine> workingBudget = null;
		Optional<CardTableContainer<JobBudget, JobBudgetLine>> lastBudget = Optional.ofNullable(workingBudget);
		
		for(BudgetLineAction action : lineActions) {

			if(LOG.isDebugEnabled()) {
				LOG.debug("Executing Action: "+action);
			}
			
			switch(action.getAction()) {
				case DELETE:
					//A DELETE action requires up to date concurrency data, likely from the previous action response 
					lastBudget.ifPresent(i-> updateTableRecordMeta(action.getJobBudgetLine(), i));
					lastBudget =  Optional.of(mrc.jobBudget().deleteTableRecord(action.getJobBudgetLine()));
					break;
				case CREATE:
					//a CREATE action required concurrency control of the Card pane.
					lastBudget.ifPresent(i-> action.getJobBudgetLine().setMeta(i.card().getMeta()));
					lastBudget =  Optional.of(mrc.jobBudget().create(action.getJobBudgetLine()));
					//CREATE actions also replace the instancekey with something server generated.
					replaceInstanceKeys(lineActions, action, lastBudget.get().lastRecord());
					break;
				case UPDATE:
					lastBudget.ifPresent(i -> updateTableRecordMeta(action.getJobBudgetLine(), i));
					lastBudget = Optional.of(mrc.jobBudget().update(action.getJobBudgetLine()));
					break;
				default:
					break;
			}
		}
		return lastBudget.get();
	}

	private void replaceInstanceKeys(List<BudgetLineAction> lineActions, BudgetLineAction executedAction,
			Record<JobBudgetLine> recentlyCreatedRecord) {
		//Find all usages of the pre-creation instance key in the action list and replace it with
		//the server allocated instance key.
		lineActions.stream()
				.map(i -> i.getJobBudgetLine().getData())
				.filter(i ->
					{
						return 	i.getParentjobbudgetlineinstancekey() != null &&
								!i.getParentjobbudgetlineinstancekey().isEmpty() &&
								i.getParentjobbudgetlineinstancekey()
								.equals(executedAction.getJobBudgetLine().getData().getInstancekey());
					})
				.forEach(i -> 
					{
						i.setParentjobbudgetlineinstancekey(recentlyCreatedRecord.getData().getInstancekey());
					});
	}

 
    private void updateTableRecordMeta(Record<JobBudgetLine> jobBudgetLine,
			CardTableContainer<JobBudget, JobBudgetLine> container) {
    	//If we find a matching record, then update the meta of the line.
    	Optional<Record<JobBudgetLine>> budgetLine = 
    			container.tableRecords().stream().filter(i -> jobBudgetLine.getData().getInstancekey().equals(
    																i.getData().getInstancekey())
    					).findFirst();
    	
    	budgetLine.ifPresent(i -> {
    		if(LOG.isDebugEnabled()) {
    			try {
					LOG.debug("Overriding meta: "+this.objectMapper.writeValueAsString(jobBudgetLine.getMeta() + 
							"\nWith: "+this.objectMapper.writeValueAsString(i.getMeta())));
				} catch (Exception e) {
					throw new BudgetIntegrationException(e);
				}
    		}
    		jobBudgetLine.setMeta(i.getMeta());	
    		//We dont just need to override the meta, but the actions also - as the ordering may have changed entirely.
    		jobBudgetLine.setLinks(i.getLinks());
    	});
    }

    private MaconomyPSORestContext buildMaconomyContext(IntegrationDetailsHolder integrationSettings) {
    	MaconomyRestClient client = new MaconomyRestClient(integrationSettings.getMacaonomyUser(), 
    														integrationSettings.getMacaonomyPassword(),
    														integrationSettings.getMacaonomyRestServiceURLBase());
    	return new MaconomyPSORestContext(client);
    	
    }

	private String jsonLastUpdatedObject(String label){
        JsonObject lastUpdateObject = new JsonObject();
        lastUpdateObject.add("label", new JsonPrimitive(label));
        lastUpdateObject.add("date", new JsonPrimitive(Calendar.getInstance().getTimeInMillis()));
        Gson gsonParser = new Gson();
        return gsonParser.toJson(lastUpdateObject);
    }

    private CardTableContainer<JobBudget, JobBudgetLine> updateBudgetType(CardTableContainer<JobBudget, JobBudgetLine> budgetData, 
    														String budgetType, MaconomyPSORestContext mrc) {
        Record<JobBudget> budget = budgetData.card();

        if (!StringUtils.equals(budget.getData().getShowbudgettypevar(), budgetType)) {
            budget.getData().setShowbudgettypevar(budgetType);
            budgetData = mrc.jobBudget().update(budget);
        }

        return budgetData;
    }

    //TODO - Do we need the current user?  It will be the integration user in this scenario.
    private String getCurrentUserName() {
    	return "";
    }
    
}
