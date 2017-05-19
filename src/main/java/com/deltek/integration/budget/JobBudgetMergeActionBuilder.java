package com.deltek.integration.budget;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.deltek.integration.budget.JobBudgetMergeActionBuilder.BudgetLineAction;
import com.deltek.integration.budget.domainmapper.AbstractLineBudgetLineMapper;
import com.deltek.integration.budget.domainmapper.BudgetLineMapper;
import com.deltek.integration.budget.domainmapper.JobStageBudgetLineMapper;
import com.deltek.integration.budget.domainmapper.JobTaskBudgetLineMapper;
import com.deltek.integration.maconomy.domain.Record;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sohnar.trafficlite.transfer.HasUuid;
import com.sohnar.trafficlite.transfer.expenses.JobExpenseTO;
import com.sohnar.trafficlite.transfer.project.AbstractLineItemTO;
import com.sohnar.trafficlite.transfer.project.JobStageTO;
import com.sohnar.trafficlite.transfer.project.JobTaskTO;
import com.sohnar.trafficlite.transfer.project.JobThirdPartyCostTO;

/**
 * This class takes TrafficLIVE Job Data, Maconomy JobBudget data and some configuration details and builds
 * Actions of each type.
 * 
 * @author simonstewart
 *
 */
public class JobBudgetMergeActionBuilder {

	private final ObjectMapper objectMapper;
	private final Map<String, HasUuid> uuidJobLineItemMap;
	private final Map<String, Record<JobBudgetLine>> instanceKeyJobBudgetLineMap;
	private final Record<JobBudgetLine> templateLine;
	private final Map<Class<?>, BudgetLineMapper<? extends HasUuid>> mapperLookup;
	private final IntegrationDetailsHolder integrationDetails;

	public JobBudgetMergeActionBuilder(
			ObjectMapper objectMapper,
			Map<String, HasUuid> uuidJobLineItemMap,
			Map<String, Record<JobBudgetLine>> instanceKeyJobBudgetLineMap, 
			Record<JobBudgetLine> templateLine,
			IntegrationDetailsHolder integrationDetails) {
		this.objectMapper = objectMapper;
		this.uuidJobLineItemMap = uuidJobLineItemMap;
		this.instanceKeyJobBudgetLineMap = instanceKeyJobBudgetLineMap;
		this.templateLine = templateLine;
		this.mapperLookup = buildMapperLookup(integrationDetails);
		this.integrationDetails = integrationDetails;
	}

	public List<BudgetLineAction> deletes() {
    	List<BudgetLineAction> lineActions = new ArrayList<>();

    	//Iterate of the JobBudgetLines to derive records that need deletions and updates.
    	instanceKeyJobBudgetLineMap.entrySet().forEach(budgetEntry -> {
    		Record<JobBudgetLine> budgetLine = budgetEntry.getValue();
    		String budgetLineTLUUID = budgetLine.getData().lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty());
    		
    		//The Budget Line DOES Not exist in TrafficLIVE, so it shall be removed.
    		if(!uuidJobLineItemMap.containsKey(budgetLineTLUUID)) {
    			lineActions.add(BudgetLineAction.delete(budgetLine));
    		} 
    	});
    	lineActions.sort(Comparator.comparing(i -> i.getJobBudgetLine().getData().getLinenumber()));
		return lineActions;
	}
	
	public List<? extends BudgetLineAction> creates() {
		List<BudgetLineAction> lineActions = new ArrayList<>();

		//All lines from Traffic that do not exist in Maconomy, need to be created.
		List<String> tlUUIDsInBudget = instanceKeyJobBudgetLineMap.values().stream()
				.map(value -> value.getData().lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty()))
				.collect(Collectors.toList());
		
		uuidJobLineItemMap.values().forEach(c -> 
			{ 	
				//UUID does not exist in the JobBudgetLines.  This means it will be a create.
				if(!tlUUIDsInBudget.contains(c.getUuid())) {
					Record<JobBudgetLine> newLine = copyLine(templateLine, true);
					lookupMapper(c.getClass(), mapperLookup).convertTo(c, newLine.getData());
					lineActions.add(BudgetLineAction.create(newLine));
				}
					
			});
    	lineActions.sort(Comparator.comparing(i -> i.getJobBudgetLine().getData().getLinenumber()));
		return lineActions;
	}

	public List<? extends BudgetLineAction> updates() {
		List<BudgetLineAction> lineActions = new ArrayList<>();
		instanceKeyJobBudgetLineMap.entrySet().forEach(budgetEntry -> {
			Record<JobBudgetLine> budgetLine = budgetEntry.getValue();
			String budgetLineTLUUID = budgetLine.getData().lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty());

			// The Budget Line DOES Not exist in TrafficLIVE, so it shall be
			// removed.
			if (uuidJobLineItemMap.containsKey(budgetLineTLUUID)) {
				HasUuid line = uuidJobLineItemMap.get(budgetLineTLUUID);
				Record<JobBudgetLine> previous = copyLine(budgetEntry.getValue(), false);
				lookupMapper(line.getClass(), mapperLookup).convertTo(line, budgetEntry.getValue().getData());
				if(!previous.getData().equals(budgetEntry.getValue().getData())) {
					lineActions.add(BudgetLineAction.update(budgetLine));
				}
			}

		});
    	lineActions.sort(Comparator.comparing(i -> i.getJobBudgetLine().getData().getLinenumber()));
		return lineActions;
	}

    public enum Action {
    	CREATE,
    	UPDATE,
    	DELETE;
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
    
    public static class BudgetLineAction {
    	
    	private Action action;
    	private Record<JobBudgetLine> jobBudgetLine;

    	public BudgetLineAction(Action action, Record<JobBudgetLine> jobBudgetLine) {
			super();
			this.action = action;
			this.jobBudgetLine = jobBudgetLine;
		}
    	
		public static BudgetLineAction delete(Record<JobBudgetLine> value) {
			return new BudgetLineAction(Action.DELETE, value);
		}
		public static BudgetLineAction update(Record<JobBudgetLine> value) {
			return new BudgetLineAction(Action.UPDATE, value);
		}
		public static BudgetLineAction create(Record<JobBudgetLine> value) {
			return new BudgetLineAction(Action.CREATE, value);
		}

		public Action getAction() {
			return action;
		}
		public Record<JobBudgetLine> getJobBudgetLine() {
			return jobBudgetLine;
		}
		
    }

}
