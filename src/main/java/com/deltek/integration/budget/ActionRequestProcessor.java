package com.deltek.integration.budget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.deltek.integration.budget.JobBudgetMergeActionRequestBuilder.BudgetLineActionRequest;
import com.deltek.integration.budget.domainmapper.AbstractLineBudgetLineMapper;
import com.deltek.integration.budget.domainmapper.BudgetLineMapper;
import com.deltek.integration.budget.domainmapper.JobStageBudgetLineMapper;
import com.deltek.integration.budget.domainmapper.JobTaskBudgetLineMapper;
import com.deltek.integration.maconomy.client.MaconomyRestClientException;
import com.deltek.integration.maconomy.domain.CardTableContainer;
import com.deltek.integration.maconomy.domain.Record;
import com.deltek.integration.maconomy.psorestclient.MaconomyPSORestContext;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudget;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sohnar.trafficlite.transfer.HasUuid;
import com.sohnar.trafficlite.transfer.expenses.JobExpenseTO;
import com.sohnar.trafficlite.transfer.project.JobStageTO;
import com.sohnar.trafficlite.transfer.project.JobTaskTO;
import com.sohnar.trafficlite.transfer.project.JobThirdPartyCostTO;

public class ActionRequestProcessor {

	private static final Log LOG = LogFactory.getLog(ActionRequestProcessor.class);
	
	private final IntegrationDetailsHolder integrationDetails;
	private final ObjectMapper objectMapper;
	private final Map<Class<?>, BudgetLineMapper<? extends HasUuid>> mapperLookup;
	
	public ActionRequestProcessor(IntegrationDetailsHolder integrationDetails, ObjectMapper objectMapper) {
		super();
		this.integrationDetails = integrationDetails;
		this.objectMapper = objectMapper;
		this.mapperLookup = buildMapperLookup(integrationDetails);

	}
	
	public CardTableContainer<JobBudget, JobBudgetLine> executeAction(MaconomyPSORestContext mrc, 
			IntegrationDetailsHolder integrationDetails, 
			BudgetLineActionRequest action,
			final CardTableContainer<JobBudget, JobBudgetLine> workingBudget, 
			List<BudgetLineActionRequest> lineActions) {

		if(LOG.isDebugEnabled()) {
			LOG.debug("Executing Action: "+action);
		}
		//This got a bit messy once we started mapping the data inside this method (which is necessary to ensure
		//the previous response data is used in the data mapping of future actions).  Now the action list can contain 
		//redundant updates (because the value data hasnt changed), so it needs to support no update.  
		//TODO It would be better to separate the concerns of removing redundant updates and processing of valid actions.
		CardTableContainer<JobBudget, JobBudgetLine> updatedBudgetContainer = null;
		try {
				switch(action.getAction()) {
				case DELETE:
					//A DELETE action requires up to date concurrency data, likely from the previous action response 
					updateTableRecordWithPreviousResponse(action.getJobBudgetLine(), workingBudget);
					updatedBudgetContainer =  mrc.jobBudget().deleteTableRecord(action.getJobBudgetLine());
					break;
				case CREATE:
					//a CREATE action required concurrency control of the Card pane.
					action.getJobBudgetLine().setMeta(workingBudget.card().getMeta());
					if(action.getTlLine().isPresent()) {
						//Map the tl data to the budget line.
						HasUuid c = action.getTlLine().get();
						lookupMapper(c.getClass(), mapperLookup).convertTo(c, action.getJobBudgetLine().getData());
						mapParentUUID(integrationDetails, action, workingBudget);
					}
					updatedBudgetContainer =  mrc.jobBudget().create(action.getJobBudgetLine());
//					//CREATE actions also replace the instancekey with something server generated.
//					replaceInstanceKeys(lineActions, action, budgetToReturn.lastRecord());
					break;
				case UPDATE:
					updateTableRecordWithPreviousResponse(action.getJobBudgetLine(), workingBudget);
					if(action.getTlLine().isPresent()) {
						HasUuid line = action.getTlLine().get();
						Record<JobBudgetLine> budgetEntry = action.getJobBudgetLine();
						//Map value based on the current state.
						Record<JobBudgetLine> previous = copyLine(budgetEntry, false);
						//map data
						lookupMapper(line.getClass(), mapperLookup).convertTo(line, budgetEntry.getData());
						mapParentUUID(integrationDetails, action, workingBudget);
						
						//For an update, we will preserve the original line number field.  As consolidating the 
						//ordering from TrafficLIVE is a mess.
						budgetEntry.getData().setLinenumber(previous.getData().getLinenumber());
						
						//A change in heirarchy is not detected, as the replacement of instance keys occurs
						//on successful response of the CREATE (when a new stage is created and an existing task is moved to it).
						if(!previous.getData().equals(budgetEntry.getData())) {
							updatedBudgetContainer = mrc.jobBudget().update(action.getJobBudgetLine());
						}						
					} else {
						//If we have no tl line to map, just process the action assuming the data is correct.
						updatedBudgetContainer = mrc.jobBudget().update(action.getJobBudgetLine());
					}
					break;
				default:
					break;
				}
		} catch (MaconomyRestClientException mre) {
			throw new BudgetIntegrationException("Error Processing Line Action:\n"+action.errorString() +
												 "\n"+mre.getError().getErrorMessage(), 
												 mre);
		}
		if(updatedBudgetContainer != null) 
			return updatedBudgetContainer;
		else 
			return workingBudget;
	}

	//We need to retrieve the UUID of the tlLine item from the previously executed request.  This is because the line may
	//have been created by the previous request and we would have no knowledge of its instance key.
	private void mapParentUUID(IntegrationDetailsHolder integrationDetails, BudgetLineActionRequest action, CardTableContainer<JobBudget, JobBudgetLine> workingBudget) {
		action.tlParentUUID().ifPresent(parentUUID -> {
			Optional<JobBudgetLine> parent = workingBudget.tableRecords().stream()
					.map(i->i.getData())
					.filter(i -> i.lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty()).equals(parentUUID)).findAny();
			//If we find a match from the previous container, then map the data.
			parent.ifPresent(i -> action.getJobBudgetLine().getData().setParentjobbudgetlineinstancekey(i.getInstancekey()));
		});
	}

	public void replaceInstanceKeys(List<BudgetLineActionRequest> lineActions, final BudgetLineActionRequest executedAction,
			Record<JobBudgetLine> recentlyCreatedRecord) {
		//Find all usages of the pre-creation instance key in the action list and replace it with
		//the server allocated instance key.
		final String originalKey = executedAction.getJobBudgetLine().getData().getInstancekey();
		final String replacementKey = recentlyCreatedRecord.getData().getInstancekey();
		
		lineActions.stream()
				.map(i -> i.getJobBudgetLine().getData())
				.filter(i ->
					{
						return 	i.getParentjobbudgetlineinstancekey() != null &&
								!i.getParentjobbudgetlineinstancekey().isEmpty() &&
								i.getParentjobbudgetlineinstancekey().equals(originalKey);
					})
				.forEach(i -> 
					{
						i.setParentjobbudgetlineinstancekey(replacementKey);
					});
	}

 
    private void updateTableRecordWithPreviousResponse(Record<JobBudgetLine> jobBudgetLine,
			CardTableContainer<JobBudget, JobBudgetLine> previousResponse) {
    	
    	//If we find a matching record, then take the record - as its data values may have been 
    	//updated by a previous service call.
    	Optional<Record<JobBudgetLine>> budgetLine = 
    			previousResponse.tableRecords().stream().filter(i -> jobBudgetLine.getData().getInstancekey().equals(
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
    		jobBudgetLine.setData(i.getData());
    	});
    }
    
	private BudgetLineMapper<HasUuid> lookupMapper(Class<?> lineClass,
			Map<Class<?>, BudgetLineMapper<? extends HasUuid>> mapperLookup) {
		BudgetLineMapper<HasUuid> mapper = (BudgetLineMapper<HasUuid>) mapperLookup.get(lineClass);
		return mapper;

	}

	private Map<Class<?>, BudgetLineMapper<? extends HasUuid>> buildMapperLookup(IntegrationDetailsHolder integrationDetailsHolder) {
		Map<Class<?>, BudgetLineMapper<? extends HasUuid>> mapperLookup = new HashMap<>();
		mapperLookup.put(JobStageTO.class, new JobStageBudgetLineMapper(integrationDetailsHolder));
		mapperLookup.put(JobTaskTO.class, new JobTaskBudgetLineMapper(integrationDetailsHolder));
		mapperLookup.put(JobExpenseTO.class, new AbstractLineBudgetLineMapper(integrationDetailsHolder));
		mapperLookup.put(JobThirdPartyCostTO.class, new AbstractLineBudgetLineMapper(integrationDetailsHolder));
		return mapperLookup;
		
	}

	private Record<JobBudgetLine> copyLine(Record<JobBudgetLine> templateLine, Boolean newUUID) {
    	//return a value copy
    	try {
    		//TODO runtime startup error, there is no objectMapper instance in this context (it belongs to the API servlet context)
    		//Work out another way to copy the object.
        	String templateString = objectMapper.writeValueAsString(templateLine);
        	Record<JobBudgetLine> copy = objectMapper.readValue(templateString, templateLine.getClass());
        	if(newUUID)
        		copy.getData().setInstancekey("JobBudgetLine"+UUID.randomUUID().toString());
        	//TODO Do we need to reset the instance key here? Testing implies we do not.
        	return copy;
    	} catch (Exception e) {
    		throw new BudgetIntegrationException("Error initialising templateline", e);
    	}
    }


	
}
