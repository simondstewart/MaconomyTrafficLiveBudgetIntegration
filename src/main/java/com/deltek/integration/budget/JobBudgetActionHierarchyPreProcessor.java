package com.deltek.integration.budget;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.deltek.integration.budget.JobBudgetMergeActionRequestBuilder.BudgetLineActionRequest;
import com.deltek.integration.maconomy.domain.CardTableContainer;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudget;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.sohnar.trafficlite.transfer.project.JobTO;

/**
 * Once all actions have been built yet before they have been executed, we need to 
 * process any hierarchy relationships. 
 * 
 * We need all the actions, a copy of the original job and a copy of the Budget to accurately populate the relationships.
 * This is to handle the cases where
 * 
 * Child (Task or Stage) is new, Stage is existing and unmodified (Parent in Budget)
 * Child is new, Stage existing and modified (Stage in UPDATE action list)
 * Child is new, Stage is new. (Stage in CREATE list)
 * 
 * @author simonstewart
 *
 */
public class JobBudgetActionHierarchyPreProcessor {

	private final List<BudgetLineActionRequest> lineActions;
	private final JobTO jobTO;
	private final IntegrationDetailsHolder integrationDetails;
	private final CardTableContainer<JobBudget, JobBudgetLine> budget;

	public JobBudgetActionHierarchyPreProcessor(JobTO jobTO, 
												CardTableContainer<JobBudget, JobBudgetLine> budgetData, 
												List<BudgetLineActionRequest> lineActions,
												IntegrationDetailsHolder integrationDetails) {
		this.jobTO = jobTO;
		this.budget = budgetData;
		this.lineActions = lineActions;
		this.integrationDetails = integrationDetails;
	}

	public List<BudgetLineActionRequest> process() {
		
		Set<String> uuids = jobTO.getJobTasks().stream().map(i->i.getUuid()).collect(Collectors.toSet());
		uuids.addAll(jobTO.getJobStages().stream().map(i->i.getUuid()).collect(Collectors.toSet()));
		
		//Start with all existing records with TL ids.
		Map<String, JobBudgetLine> tlUuidToJobLine = budget.tableRecords().stream()
				.map(record -> record.getData())
				.filter(i -> !i.lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty()).isEmpty())
				.filter(i -> uuids.contains(i.lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty())))
				.collect(Collectors.toMap(p -> p.lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty()), 
						  Function.identity()));

		//Add the pending line actions
		lineActions.stream()
				.map(i -> i.getJobBudgetLine().getData())
				.forEach(i -> tlUuidToJobLine.put(i.lookupTrafficUUID(integrationDetails.getMaconomyBudgetUUIDProperty()), 
						i));

		//The only fields that will require processing, are Tasks/Stages with a parent relationship defined. So for each of those
		//lookup the associated JobBudget line for the tl uuid, and add the relationship.
		jobTO.getJobTasks().stream()
			.filter(task -> task.getJobStageUUID() != null && !task.getJobStageUUID().isEmpty())
			.forEach(task -> {
				JobBudgetLine line = tlUuidToJobLine.get(task.getUuid());
				JobBudgetLine parent = tlUuidToJobLine.get(task.getJobStageUUID());
				line.setParentjobbudgetlineinstancekey(parent.getInstancekey());
			});
			
		jobTO.getJobStages().stream()
			.filter(stage -> stage.getParentStageUUID() != null && !stage.getParentStageUUID().isEmpty())
			.forEach(stage -> {
				JobBudgetLine line = tlUuidToJobLine.get(stage.getUuid());
				JobBudgetLine parent = tlUuidToJobLine.get(stage.getParentStageUUID());
				line.setParentjobbudgetlineinstancekey(parent.getInstancekey());
			});
		//safe to return.
		return lineActions;
	}
	
}
