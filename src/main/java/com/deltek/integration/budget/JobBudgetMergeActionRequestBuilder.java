package com.deltek.integration.budget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.deltek.integration.maconomy.domain.CardTableContainer;
import com.deltek.integration.maconomy.domain.Record;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudget;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sohnar.trafficlite.transfer.HasUuid;
import com.sohnar.trafficlite.transfer.expenses.JobExpenseTO;
import com.sohnar.trafficlite.transfer.project.AbstractLineItemTO;
import com.sohnar.trafficlite.transfer.project.JobStageTO;
import com.sohnar.trafficlite.transfer.project.JobTO;
import com.sohnar.trafficlite.transfer.project.JobTaskTO;
import com.sohnar.trafficlite.transfer.project.JobThirdPartyCostTO;

/**
 * This class takes TrafficLIVE Job Data, Maconomy JobBudget data and some configuration details and builds
 * Actions of each type.
 * 
 * @author simonstewart
 *
 */
public class JobBudgetMergeActionRequestBuilder {

	private final IntegrationDetailsHolder integrationDetails;
	private final ObjectMapper objectMapper;
	private final Map<String, HasUuid> uuidJobLineItemMap;
	private final Map<String, Record<JobBudgetLine>> instanceKeyJobBudgetLineMap;
	private final Record<JobBudgetLine> templateLine;
	private final Map<String, JobStageTO> uuidStageMap;
	private final Map<String, JobTaskTO> uuidTaskMap;
	
	public JobBudgetMergeActionRequestBuilder(JobTO job, CardTableContainer<JobBudget, JobBudgetLine> budgetData, ObjectMapper objectMapper,
			Record<JobBudgetLine> templateLine, IntegrationDetailsHolder integrationDetals) {
		this.objectMapper = objectMapper;
		this.integrationDetails = integrationDetals;
		this.templateLine = templateLine;
		
		//The job is the master in this scenario.  Create a uuid key map.
		uuidStageMap = job.getJobStages().stream().collect(Collectors.toMap(JobStageTO::getUuid, Function.identity()));
		uuidTaskMap = job.getJobTasks().stream().collect(Collectors.toMap(JobTaskTO::getUuid, Function.identity()));
		Map<String, JobExpenseTO> uuidExpenseMap = job.getJobExpenses().stream().collect(Collectors.toMap(JobExpenseTO::getUuid, Function.identity()));
		Map<String, JobThirdPartyCostTO> uuidThirdPartyMap = job.getJobThirdPartyCosts().stream().collect(
																	Collectors.toMap(JobThirdPartyCostTO::getUuid, Function.identity()));
		
		//TODO find a clever way to merge this map with Java 8
//		Map<String, HasUuid> uuidJobLineItemMap = new HashMap<>();
		uuidJobLineItemMap = new HashMap<>();
		uuidStageMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidTaskMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidExpenseMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidThirdPartyMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		
		//A collection of existing line items mapped to uuid.
		instanceKeyJobBudgetLineMap = budgetData.tableRecords().stream().collect(
																	Collectors.toMap(
																			c -> c.getData().getInstancekey(), 
																			c -> c));

	}
	
	public List<BudgetLineActionRequest> deletes() {
    	List<BudgetLineActionRequest> lineActions = new ArrayList<>();

    	//Iterate of the JobBudgetLines to derive records that need deletions and updates.
    	instanceKeyJobBudgetLineMap.entrySet().forEach(budgetEntry -> {
    		Record<JobBudgetLine> budgetLine = budgetEntry.getValue();
    		String budgetLineTLUUID = budgetLine.getData().lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty());
    		
    		//The Budget Line DOES Not exist in TrafficLIVE, so it shall be removed.
    		if(!uuidJobLineItemMap.containsKey(budgetLineTLUUID)) {
    			lineActions.add(BudgetLineActionRequest.delete(budgetLine));
    		} 
    	});
    	lineActions.sort(Comparator.naturalOrder());
		return lineActions;
	}
	
	public List<? extends BudgetLineActionRequest> creates() {
		List<BudgetLineActionRequest> lineActions = new ArrayList<>();

		//All lines from Traffic that do not exist in Maconomy, need to be created.
		List<String> tlUUIDsInBudget = instanceKeyJobBudgetLineMap.values().stream()
				.map(value -> value.getData().lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty()))
				.collect(Collectors.toList());
		
		uuidJobLineItemMap.values().forEach(c -> 
			{ 	
				//UUID does not exist in the JobBudgetLines.  This means it will be a create.
				if(!tlUUIDsInBudget.contains(c.getUuid())) {
					Record<JobBudgetLine> newLine = copyLine(templateLine, true);
					//do not perform full mapping, but we do need the tl uuid for later processing of creates.
					newLine.getData().applyTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty(), c.getUuid());

					//TODO - Gargh!  Need the line number for new lines so the sort works.
					//					lookupMapper(c.getClass(), mapperLookup).convertTo(c, newLine.getData());
					lineActions.add(BudgetLineActionRequest.create(c, newLine));
				}
					
			});
		
    	lineActions.sort(Comparator.naturalOrder());
		return lineActions;
	}
	
	public List<? extends BudgetLineActionRequest> updates() {
		List<BudgetLineActionRequest> lineActions = new ArrayList<>();
		instanceKeyJobBudgetLineMap.entrySet().forEach(budgetEntry -> {
			Record<JobBudgetLine> budgetLine = budgetEntry.getValue();
			String budgetLineTLUUID = budgetLine.getData().lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty());

			// The Budget Line DOES Not exist in TrafficLIVE, so it shall be
			// removed.
			if (uuidJobLineItemMap.containsKey(budgetLineTLUUID)) {
				HasUuid line = uuidJobLineItemMap.get(budgetLineTLUUID);
//				Record<JobBudgetLine> previous = copyLine(budgetEntry.getValue(), false);
//				lookupMapper(line.getClass(), mapperLookup).convertTo(line, budgetEntry.getValue().getData());
//				//For an update, we will preserve the original line number field.  As consolidating the 
//				//ordering from TrafficLIVE is a mess.
//				budgetEntry.getValue().getData().setLinenumber(previous.getData().getLinenumber());
//				
//				if(!previous.getData().equals(budgetEntry.getValue().getData())) {
//					lineActions.add(BudgetLineActionRequest.update(line, budgetLine));
//				}
				//The Update Request contains the state of the line prior to any proceeding updates.  This will be out of date 
				//once proceeding actions have been processed.  See class ActionRequestProcessor
				lineActions.add(BudgetLineActionRequest.update(line, budgetLine));
				
			}

		});
    	lineActions.sort(Comparator.naturalOrder());
		return lineActions;
	}

    public enum Action {
    	CREATE,
    	UPDATE,
    	DELETE;
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
    
    public static class BudgetLineActionRequest implements Comparable<BudgetLineActionRequest> {
    	
    	private Action action;
    	private Record<JobBudgetLine> jobBudgetLine;
    	private Optional<Record<JobBudgetLine>> parentJobBudgetLine;
    	private Optional<HasUuid> tlLine;
    	
    	public BudgetLineActionRequest(Action action, Record<JobBudgetLine> jobBudgetLine) {
			super();
			this.action = action;
			this.jobBudgetLine = jobBudgetLine;
			this.parentJobBudgetLine = Optional.empty();
			this.tlLine = Optional.empty();
		}
    	
    	public BudgetLineActionRequest(Action action, Record<JobBudgetLine> jobBudgetLine,
				Optional<HasUuid> tlLine) {
			this(action, jobBudgetLine);
			this.tlLine = tlLine;
		}

		public static BudgetLineActionRequest delete(Record<JobBudgetLine> value) {
			return new BudgetLineActionRequest(Action.DELETE, value);
		}
		public static BudgetLineActionRequest update(Record<JobBudgetLine> value) {
			return new BudgetLineActionRequest(Action.UPDATE, value);
		}
		public static BudgetLineActionRequest create(Record<JobBudgetLine> value) {
			return new BudgetLineActionRequest(Action.CREATE, value);
		}
		public static BudgetLineActionRequest update(HasUuid tlLine, Record<JobBudgetLine> value) {
			return new BudgetLineActionRequest(Action.UPDATE, value, Optional.of(tlLine));
		}
		public static BudgetLineActionRequest create(HasUuid tlLine, Record<JobBudgetLine> value) {
			return new BudgetLineActionRequest(Action.CREATE, value, Optional.of(tlLine));
		}

		public Action getAction() {
			return action;
		}
		public Record<JobBudgetLine> getJobBudgetLine() {
			return jobBudgetLine;
		}

		public Optional<Record<JobBudgetLine>> getParentJobBudgetLine() {
			return parentJobBudgetLine;
		}

		public String tlUUID(String uuidPropName) {
			return jobBudgetLine.getData().lookupTrafficUUID(uuidPropName);
		}
		
		public String macUUID() {
			return jobBudgetLine.getData().getInstancekey();
		}
		
		public Optional<HasUuid> getTlLine() {
			return tlLine;
		}

		@Override
		public String toString() {
			return "BudgetLineActionRequest [action=" + action + ", jobBudgetLine=" + jobBudgetLine + ", tlLine="
					+ (tlLine.isPresent() ? tlLineToString(tlLine.get()) : "EMPTY" )  + "]";
		}

		public String errorString() {
			StringBuilder errorString = new StringBuilder("Action: "+action);
			errorString.append("\n Maconomy JobBudgetLine: text: "+jobBudgetLine.getData().getText());
			tlLine.ifPresent(tlLine -> errorString.append("\n TrafficLIVE Line: "+tlLineToString(tlLine)));
			return errorString.toString();
		}
		
		private String tlLineToString(HasUuid tlLine) {
			if(tlLine == null)
				return "";
			
			StringBuilder sb = new StringBuilder(tlLine.getClass().getSimpleName());
			if(tlLine instanceof JobStageTO) {
				sb.append(", description: ").append(((JobStageTO)tlLine).getDescription());
			} else if (tlLine instanceof AbstractLineItemTO) {
				sb.append(", description: ").append(((AbstractLineItemTO)tlLine).getDescription());
			} else {
				sb.append(", uuid: ").append(tlLine.getUuid());
			}
			sb.append(", lineOrder: ").append(lineOrder());
			return sb.toString();
		}

		@Override
		public int compareTo(BudgetLineActionRequest o) {
			//DELETES at the start.
			if(Action.DELETE.equals(action)) 
					return -1;
			
			return Integer.compare(lineOrder(), o.lineOrder());
		}
		
		public Integer lineOrder() {
			if(!getTlLine().isPresent())
				return 0;
			HasUuid tlLine = getTlLine().get();
			if(JobTaskTO.class.equals(tlLine.getClass())) {
				return ((JobTaskTO)tlLine).getHierarchyOrder();
			} else if (JobStageTO.class.equals(tlLine.getClass())) {
				return ((JobStageTO)tlLine).getHierarchyOrder();
			} else if (AbstractLineItemTO.class.isAssignableFrom(tlLine.getClass())) {
				return ((AbstractLineItemTO)tlLine).getLineItemOrder();
			} else {
				throw new RuntimeException("Invalid Line Type encountered: "+tlLine.getClass());
			}
			
 		}
		
		public Optional<String> tlParentUUID() {
			if(getTlLine().isPresent()) {
				String parentUUID = "";
				HasUuid tlLine = getTlLine().get();
				if(tlLine instanceof JobStageTO) {
					parentUUID = ((JobStageTO)tlLine).getParentStageUUID();
					
				} else if (tlLine instanceof JobTaskTO) {
					parentUUID = ((JobTaskTO)tlLine).getJobStageUUID();
				}

				if(parentUUID != null && !parentUUID.trim().isEmpty())
					return Optional.of(parentUUID);
			}

			return Optional.empty();
	}
		
    }

}
