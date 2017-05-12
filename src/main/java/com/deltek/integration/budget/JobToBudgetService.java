package com.deltek.integration.budget;

import static com.deltek.integration.budget.domainmapper.ConversionConstants.DATE_TIME_FORMAT;
import static com.deltek.integration.budget.domainmapper.ConversionConstants.MACONOMY_AMOUNT_TYPE;
import static com.deltek.integration.budget.domainmapper.ConversionConstants.MACONOMY_MILESTONE_TYPE;
import static com.deltek.integration.budget.domainmapper.ConversionConstants.MACONOMY_OUTLAY_TYPE;
import static com.deltek.integration.budget.domainmapper.ConversionConstants.MACONOMY_STAGE_TYPE;
import static com.deltek.integration.budget.domainmapper.ConversionConstants.MACONOMY_TIME_TYPE;
import static com.deltek.integration.budget.domainmapper.ConversionConstants.SYNC_ERROR;
import static com.deltek.integration.budget.domainmapper.ConversionConstants.SYNC_OK;
import static com.deltek.integration.budget.domainmapper.ConversionConstants.formatAsLocalTimeDate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.glassfish.jersey.uri.UriComponent;
import org.glassfish.jersey.uri.UriComponent.Type;

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
import com.sohnar.trafficlite.business.utils.ValidationResult;
import com.sohnar.trafficlite.datamodel.enums.financial.ChargeBandType;
import com.sohnar.trafficlite.datamodel.enums.financial.CurrencyType;
import com.sohnar.trafficlite.datamodel.enums.project.JobTaskCategoryType;
import com.sohnar.trafficlite.transfer.BaseTO;
import com.sohnar.trafficlite.transfer.HasUuid;
import com.sohnar.trafficlite.transfer.Identifier;
import com.sohnar.trafficlite.transfer.expenses.JobExpenseTO;
import com.sohnar.trafficlite.transfer.financial.ChargeBandTO;
import com.sohnar.trafficlite.transfer.financial.MoneyTO;
import com.sohnar.trafficlite.transfer.financial.PrecisionMoneyTO;
import com.sohnar.trafficlite.transfer.project.AbstractLineItemTO;
import com.sohnar.trafficlite.transfer.project.JobStageTO;
import com.sohnar.trafficlite.transfer.project.JobTO;
import com.sohnar.trafficlite.transfer.project.JobTaskTO;
import com.sohnar.trafficlite.transfer.project.JobThirdPartyCostTO;

//@Service
public class JobToBudgetService {

	private static final Log LOG = LogFactory.getLog(JobToBudgetService.class);
    private static final Logger juliLogger = Logger.getLogger(JobToBudgetService.class.getName());
	private static final String DEFAULT_UUID_PROPERTY = "remark10";
	
    @Resource(name="objectMapper")
    private ObjectMapper objectMapper;
    
    /**
     * Push the TrafficLIVE Job to a Maconomy Budget.
     */
    public JobTO synchronizeMaconomyBudgetWithJob(JobTO trafficJob, IntegrationDetailsHolder integrationSettings) {

    	//Retrieve required base integration information from TrafficLIVE.
        MaconomyPSORestContext mrc = buildMaconomyContext(integrationSettings);
        
        String maconomyJobNumber = trafficJob.getExternalCode();
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		mrc.jobBudget().data(String.format("jobnumber=%s", maconomyJobNumber));

        budgetData = updateBudgetType(budgetData, integrationSettings.getMaconomyBudgetType(), mrc);
        budgetData = reopenBudget(budgetData, mrc);
        budgetData = deleteBudgetItems(budgetData, mrc);
        
//        List<Record<JobBudgetLine>> initialisedLinesToCreate = initiateLines(trafficJob, budgetData, integrationSettings, mrc);
//        budgetData = createLines(initialisedLinesToCreate, budgetData, integrationSettings, mrc);

        budgetData.card().getData().setRevisionremark1var(
        		String.format("Synced at %s (UTC) from TrafficLIVE  by %s", 
        				DATE_TIME_FORMAT.format(Calendar.getInstance().getTime()), getCurrentUserName()));
        budgetData = updateBudget(budgetData, mrc);
        budgetData = submitBudget(budgetData, mrc);
        budgetData = approveBudget(budgetData, mrc);
        trafficJob.setExternalData(jsonLastUpdatedObject("Last budget sent:"));
        return trafficJob;
    }

    private MaconomyPSORestContext buildMaconomyContext(IntegrationDetailsHolder integrationSettings) {
    	MaconomyRestClient client = new MaconomyRestClient(integrationSettings.getMacaonomyUser(), 
    														integrationSettings.getMacaonomyPassword(),
    														integrationSettings.getMacaonomyRestServiceURLBase());
    	return new MaconomyPSORestContext(client);
    	
    }

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

        MaconomyPSORestContext mrc = buildMaconomyContext(integrationSettings);
        String maconomyJobNumber = trafficJob.getExternalCode();
        
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		mrc.jobBudget().data(String.format("jobnumber=%s", maconomyJobNumber));

        budgetData = updateBudgetType(budgetData, integrationSettings.getMaconomyBudgetType(), mrc);
    	budgetData = mrc.jobBudget().postToAction("action:reopenbudget", budgetData.card());
        //Open the existing budget of this type, create non existent trafficlive lines.
        //Update existing budget lines to the new values from TrafficLIVE.
        //Delete budget lines that no longer exist.
        List<BudgetLineAction> budgetLineActions = createMergeLineActions(budgetData, trafficJob, integrationSettings, mrc);
        budgetData = executeActions(mrc, budgetLineActions);
        
        budgetData.card().getData()
                .setRevisionremark1var(String.format("Synced at %s (UTC) from TrafficLIVE  by %s", 
                		DATE_TIME_FORMAT.format(Calendar.getInstance().getTime()), getCurrentUserName()));
        budgetData = updateBudget(budgetData, mrc);
        budgetData = submitBudget(budgetData, mrc);
        budgetData = approveBudget(budgetData, mrc);
        trafficJob.setExternalData(jsonLastUpdatedObject("Last budget sent:"));
        return trafficJob;
    }
    
	public List<BudgetLineAction> createMergeLineActions(CardTableContainer<JobBudget, JobBudgetLine> budgetData,
																			JobTO job,
																			IntegrationDetailsHolder integrationDetails, 
																			MaconomyPSORestContext mrc) {
		//The job is the master in this scenario.  Create a uuid key map.
		Map<String, JobStageTO> uuidStageMap = job.getJobStages().stream().collect(Collectors.toMap(JobStageTO::getUuid, Function.identity()));
		Map<String, JobTaskTO> uuidTaskMap = job.getJobTasks().stream().collect(Collectors.toMap(JobTaskTO::getUuid, Function.identity()));
		Map<String, JobExpenseTO> uuidExpenseMap = job.getJobExpenses().stream().collect(Collectors.toMap(JobExpenseTO::getUuid, Function.identity()));
		Map<String, JobThirdPartyCostTO> uuidThirdPartyMap = job.getJobThirdPartyCosts().stream().collect(
																	Collectors.toMap(JobThirdPartyCostTO::getUuid, Function.identity()));
		//TODO find a clever way to merge this map with Java 8
		Map<String, HasUuid> uuidJobLineItemMap = new HashMap<>();
		uuidStageMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidTaskMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidExpenseMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidThirdPartyMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		
		//A collection of existing line items mapped to uuid.
		Map<String, Record<JobBudgetLine>> instanceKeyJobBudgetLineMap = budgetData.tableRecords().stream().collect(
																	Collectors.toMap(
																			c -> c.getData().getInstancekey(), 
																			c -> c));
		
		Record<JobBudgetLine> templateLine = mrc.jobBudget().initTable(budgetData.getPanes().getTable());
		
		//Generate a collection of lines actions, based on the state to be merged.
		JobBudgetMergeActionBuilder actionBuilder = new JobBudgetMergeActionBuilder(objectMapper,
												uuidJobLineItemMap, instanceKeyJobBudgetLineMap, templateLine, 
												integrationDetails);
		
		List<BudgetLineAction> lineActions = actionBuilder.deletes();
		lineActions.addAll(actionBuilder.creates());
		lineActions.addAll(actionBuilder.updates());
		
		return lineActions;
	}
	
	public JobTO mergeJobToMaconomyBudget2(JobTO trafficJob, IntegrationDetailsHolder integrationSettings) {

        MaconomyPSORestContext mrc = buildMaconomyContext(integrationSettings);
        String maconomyJobNumber = trafficJob.getExternalCode();
        
        CardTableContainer<JobBudget, JobBudgetLine> budgetData = 
        		mrc.jobBudget().data(String.format("jobnumber=%s", maconomyJobNumber));

        budgetData = updateBudgetType(budgetData, integrationSettings.getMaconomyBudgetType(), mrc);
    	budgetData = mrc.jobBudget().postToAction("action:reopenbudget", budgetData.card());
        //Open the existing budget of this type, create non existent trafficlive lines.
        //Update existing budget lines to the new values from TrafficLIVE.
        //Delete budget lines that no longer exist.
        budgetData = mergeJobWithBudget(budgetData, trafficJob, integrationSettings, mrc);
//        budgetData = deleteNonTrafficBudgetLines(budgetData, integrationSettings, mrc);
//        budgetData = createBudgetItems(trafficJob, budgetData, integrationSettings, mrc);
        budgetData.card().getData()
                .setRevisionremark1var(String.format("Synced at %s (UTC) from TrafficLIVE  by %s", 
                		DATE_TIME_FORMAT.format(Calendar.getInstance().getTime()), getCurrentUserName()));
        budgetData = updateBudget(budgetData, mrc);
        budgetData = submitBudget(budgetData, mrc);
        budgetData = approveBudget(budgetData, mrc);
        trafficJob.setExternalData(jsonLastUpdatedObject("Last budget sent:"));
        return trafficJob;
    }
    
	public CardTableContainer<JobBudget, JobBudgetLine> mergeJobWithBudget(CardTableContainer<JobBudget, JobBudgetLine> budgetData,
																			JobTO job,
																			IntegrationDetailsHolder integrationDetails, 
																			MaconomyPSORestContext mrc) {
		//The job is the master in this scenario.  Create a uuid key map.
		Map<String, JobStageTO> uuidStageMap = job.getJobStages().stream().collect(Collectors.toMap(JobStageTO::getUuid, Function.identity()));
		Map<String, JobTaskTO> uuidTaskMap = job.getJobTasks().stream().collect(Collectors.toMap(JobTaskTO::getUuid, Function.identity()));
		Map<String, JobExpenseTO> uuidExpenseMap = job.getJobExpenses().stream().collect(Collectors.toMap(JobExpenseTO::getUuid, Function.identity()));
		Map<String, JobThirdPartyCostTO> uuidThirdPartyMap = job.getJobThirdPartyCosts().stream().collect(
																	Collectors.toMap(JobThirdPartyCostTO::getUuid, Function.identity()));
		//TODO find a clever way to merge this map with Java 8
		Map<String, HasUuid> uuidJobLineItemMap = new HashMap<>();
		uuidStageMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidTaskMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidExpenseMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		uuidThirdPartyMap.forEach((k,v) -> uuidJobLineItemMap.put(k, v));
		
		//A collection of existing line items mapped to uuid.
		Map<String, Record<JobBudgetLine>> instanceKeyJobBudgetLineMap = budgetData.tableRecords().stream().collect(
																	Collectors.toMap(
																			c -> c.getData().getInstancekey(), 
																			c -> c));
		
		Record<JobBudgetLine> templateLine = mrc.jobBudget().initTable(budgetData.getPanes().getTable());
		
		//Generate a collection of lines actions, based on the state to be merged.
		JobBudgetMergeActionBuilder actionBuilder = new JobBudgetMergeActionBuilder(objectMapper,
												uuidJobLineItemMap, instanceKeyJobBudgetLineMap, templateLine, 
												integrationDetails);
		
		List<BudgetLineAction> lineActions = actionBuilder.deletes();
		lineActions.addAll(actionBuilder.creates());
		lineActions.addAll(actionBuilder.updates());
		
		executeActions(mrc, lineActions);
		return budgetData;
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
		for(BudgetLineAction action : lineActions) {
			if(workingBudget != null) {
				//TODO merge the workingBudget concurrency control with the current line.
				
			}
			switch(action.getAction()) {
				case DELETE:
					workingBudget = mrc.jobBudget().deleteTableRecord(action.getJobBudgetLine());
					break;
				case CREATE:
					workingBudget = mrc.jobBudget().create(action.getJobBudgetLine());
					break;
				case UPDATE:
					workingBudget = mrc.jobBudget().update(action.getJobBudgetLine());
					break;
				default:
					break;
			}
		}
		return workingBudget;
	}

    private String jsonLastUpdatedObject(String label){
        JsonObject lastUpdateObject = new JsonObject();
        lastUpdateObject.add("label", new JsonPrimitive(label));
        lastUpdateObject.add("date", new JsonPrimitive(Calendar.getInstance().getTimeInMillis()));
        Gson gsonParser = new Gson();
        return gsonParser.toJson(lastUpdateObject);
    }

    private Map<String, Integer> mapIstanceKeyByLineNumber(List<Record<JobBudgetLine>> budgetLines) {
        Map<String, Integer> result = new HashMap<>();
        for (int index = 0; index < budgetLines.size(); index++) {
            result.put(budgetLines.get(index).getData().getInstancekey(), index + 1);
        }
        return result;
    }

    private void updateAndCreateLineItems(CardTableContainer<JobBudget, JobBudgetLine> budgetData, JobTO trafficJob) {

        Map<String, Integer> lineItemNumberByIstanceKey = mapIstanceKeyByLineNumber(budgetData.getPanes().getTable().getRecords());
        Set<JobBudgetLine> maconomyStages = new HashSet<>();
        Set<JobBudgetLine> maconomyTimes = new HashSet<>();
        Set<JobBudgetLine> maconomyOutlays = new HashSet<>();

        for (Record<JobBudgetLine> budgetLine : budgetData.getPanes().getTable().getRecords()) {
            switch (budgetLine.getData().getLinetype()) {
                case MACONOMY_STAGE_TYPE:
                    maconomyStages.add(budgetLine.getData());
                    break;
                case MACONOMY_TIME_TYPE:
                    maconomyTimes.add(budgetLine.getData());
                    break;
                case MACONOMY_MILESTONE_TYPE:
                	maconomyTimes.add(budgetLine.getData());
                	break;
                case MACONOMY_OUTLAY_TYPE:
                case MACONOMY_AMOUNT_TYPE:
                    maconomyOutlays.add(budgetLine.getData());
                    break;
            }
        }
        updateAndCreateStageTasks(maconomyStages, maconomyTimes, trafficJob, lineItemNumberByIstanceKey);
        updateAndCreateThirdPartyExpense(maconomyOutlays, trafficJob, lineItemNumberByIstanceKey);
    }

    private <T extends BaseTO> List<ValidationResult<T>> itemsCannotBeDeletedByClass(List<ValidationResult<T>> jobItemsValidation, 
    																			Class itemClass){
        List<ValidationResult<T>> result = new ArrayList<>();
        jobItemsValidation.stream().filter(singleItemValidation -> !singleItemValidation.getCanBeDeleted() && (singleItemValidation.getOrigin().getClass() == itemClass)).forEach(singleItemValidation -> {
            result.add(singleItemValidation);
        });
        return result;
    }

    private void updateAndCreateThirdPartyExpense(Set<JobBudgetLine> maconomyOutlays, JobTO trafficJob, 
    												Map<String, Integer> lineItemNumberByIstanceKey) {
        Set<JobThirdPartyCostTO> thirdPartyCosts = new HashSet<>();
        Set<JobExpenseTO> jobExpenses = new HashSet<>();
        Map<String, JobThirdPartyCostTO> jobThirdPartyCostByUuid = mapItemWithUuid(trafficJob.getJobThirdPartyCosts());
        Map<String, JobExpenseTO> jobExpensesByUuid = mapItemWithUuid(trafficJob.getJobExpenses());
        List<ValidationResult<JobThirdPartyCostTO>> purchaseCannotBeDeleted = new ArrayList<>();

//        		Replace with rest Job validation call.
//        		itemsCannotBeDeletedByClass(jobService.validateJobByBusinessRules(trafficJob), JobThirdPartyCostTO.class);
        List<ValidationResult<JobExpenseTO>> expensesCannotBeDeleted = new ArrayList<>();
//        itemsCannotBeDeletedByClass(jobService.validateJobByBusinessRules(trafficJob), JobExpenseTO.class);
        for (JobBudgetLine maconomyLine : maconomyOutlays) {
            ChargeBandTO chargeBand = getChargeBandFromMaconomyLine(maconomyLine);
            switch (chargeBand.getChargeBandType()) {
                case THIRD_PARTY:
                    JobThirdPartyCostTO thirdPartyCost = updateOrCreateThirdPartyCostFromMaconomyLine(maconomyLine, jobThirdPartyCostByUuid, lineItemNumberByIstanceKey);
                    thirdPartyCost.setChargeBandId(new Identifier(chargeBand.getId()));
                    thirdPartyCost.setExternalData(SYNC_OK);
                    thirdPartyCosts.add(thirdPartyCost);
                    break;
                case EXPENSES:
                    JobExpenseTO expense = updateOrCreateExpenseFromMaconomyLine(maconomyLine, jobExpensesByUuid, lineItemNumberByIstanceKey);
                    expense.setChargeBandId(new Identifier(chargeBand.getId()));
                    expense.setExternalData(SYNC_OK);
                    jobExpenses.add(expense);
                    break;
            }
        }
        thirdPartyCosts = keepRemovedItemCannotBeDeleted(jobThirdPartyCostByUuid, purchaseCannotBeDeleted, thirdPartyCosts);
        jobExpenses = keepRemovedItemCannotBeDeleted(jobExpensesByUuid, expensesCannotBeDeleted, jobExpenses);
        trafficJob.setJobThirdPartyCosts(thirdPartyCosts);
        trafficJob.setJobExpenses(jobExpenses);
    }

    private <T extends AbstractLineItemTO>Set<T> keepRemovedItemCannotBeDeleted(Map<String, T> lineItemsByUuid, List<ValidationResult<T>> itemsCannotBeDeleted, Set<T> newLineItems) {
        Set<String> newUuidSet = new HashSet<>();
        for (T lineItem : newLineItems){
            newUuidSet.add(lineItem.getUuid());
        }
        Map<String, String> uuidToKeepWithResult = new HashMap<>();
        for (ValidationResult<T> lineItemValidation : itemsCannotBeDeleted){
            if( !lineItemValidation.getCanBeDeleted()){
                uuidToKeepWithResult.put(lineItemValidation.getOrigin().getUuid(), lineItemValidation.getResult());
            }
        }
        lineItemsByUuid.keySet().removeAll(newUuidSet);
        for (T oldLineItem : lineItemsByUuid.values()){
            if(uuidToKeepWithResult.containsKey(oldLineItem.getUuid())){
                oldLineItem.setExternalData(SYNC_ERROR + uuidToKeepWithResult.get(oldLineItem.getUuid()));
                newLineItems.add(oldLineItem);
            }
        }
        return newLineItems;
    }

    private JobThirdPartyCostTO updateOrCreateThirdPartyCostFromMaconomyLine(JobBudgetLine maconomyLine, Map<String, JobThirdPartyCostTO> jobThirdParyyCostByUuid,
                                                                             Map<String, Integer> lineItemNumberByIstanceKey
                                                                             ) {
        String maconomyUuidproperty = DEFAULT_UUID_PROPERTY;
		String thirdPartyUuid = maconomyLine.getAdditionalProperties().get(maconomyUuidproperty ).toString();

        if (jobThirdParyyCostByUuid.containsKey(thirdPartyUuid)) {
            JobThirdPartyCostTO existingThirdPartyCost = jobThirdParyyCostByUuid.get(thirdPartyUuid);
            mapMaconomyLinePropertiesToAbstractLineItem(maconomyLine, existingThirdPartyCost, lineItemNumberByIstanceKey);
            return existingThirdPartyCost;
        } else {
            JobThirdPartyCostTO jobThirdPartyCost = new JobThirdPartyCostTO();
            mapMaconomLineToNewLineItem(maconomyLine, jobThirdPartyCost, maconomyUuidproperty, lineItemNumberByIstanceKey);
            return jobThirdPartyCost;
        }
    }

    private JobExpenseTO updateOrCreateExpenseFromMaconomyLine(JobBudgetLine maconomyLine, Map<String, JobExpenseTO> jobExpensesByUuid,
                                                               Map<String, Integer> lineItemNumberByIstanceKey) {
        String maconomyUuidproperty = DEFAULT_UUID_PROPERTY;
        String expenseUuid = maconomyLine.getAdditionalProperties().get(maconomyUuidproperty).toString();

        if (jobExpensesByUuid.containsKey(expenseUuid)) {
            JobExpenseTO expense = jobExpensesByUuid.get(expenseUuid);
            mapMaconomyLinePropertiesToAbstractLineItem(maconomyLine, expense, lineItemNumberByIstanceKey);
            return expense;
        } else {
            JobExpenseTO jobExpense = new JobExpenseTO();
            mapMaconomLineToNewLineItem(maconomyLine, jobExpense, maconomyUuidproperty, lineItemNumberByIstanceKey);
            return jobExpense;
        }
    }

    private <T extends AbstractLineItemTO> void mapMaconomLineToNewLineItem(JobBudgetLine maconomyLine, T lineItem, String maconomyUuidproperty,
                                                                            Map<String, Integer> lineItemNumberByIstanceKey) {
        mapMaconomyLinePropertiesToAbstractLineItem(maconomyLine, lineItem, lineItemNumberByIstanceKey);
        lineItem.setUuid(UUID.randomUUID().toString());
        maconomyLine.setAdditionalProperty(maconomyUuidproperty, lineItem.getUuid());
    }

    private ChargeBandTO getChargeBandFromMaconomyLine(JobBudgetLine maconomyLine) {
    	//TODO - retrieve charge bands.
        Set<ChargeBandTO> expensesAndThirdPartyChargebands = filterThirdPartyAndExpensesChargebands(new ArrayList<>());

        List<ChargeBandTO> result = findThirdPartyAndExpenseChargebandForMaconomyLine(maconomyLine, expensesAndThirdPartyChargebands);
        if (result.size() == 0) {
            throw new BudgetIntegrationException(String.format("Cannot find any chargeband with '%s' as secondary external code for that ThirdParty or Expense. " +
                    "It must be set to the corresponding Maconomy task's name.", maconomyLine.getTaskname()));
        } else if (result.size() > 1) {
            throw new BudgetIntegrationException(String.format("Found '%d' chargebands with '%s' as secondary externalCode for that ThirdParty or Expense when " +
                    "only one should be present.", result.size(), maconomyLine.getTaskname()));
        } else {
            return result.get(0);
        }
    }

    private List<ChargeBandTO> findThirdPartyAndExpenseChargebandForMaconomyLine(JobBudgetLine maconomyLine, Set<ChargeBandTO> expensesAndThirdPartyChargebands) {
        return expensesAndThirdPartyChargebands.stream().filter(cb -> cb.getSecondaryExternalCode().equals(maconomyLine.getTaskname())).collect(Collectors.toList());
    }

    private Set<ChargeBandTO> filterThirdPartyAndExpensesChargebands(Collection<ChargeBandTO> allChargeBands) {
        return allChargeBands.stream().filter(cb -> isAThirdPartyOrExpenseMaconomyChargeband(cb)).collect(Collectors.toSet());
    }

    private boolean isAThirdPartyOrExpenseMaconomyChargeband(ChargeBandTO chargeBand){
        return !ChargeBandType.TIME_FEE.equals(chargeBand.getChargeBandType()) && chargeBand.getSecondaryExternalCode() != null;
    }

    private void updateAndCreateStageTasks(Set<JobBudgetLine> maconomyStages, Set<JobBudgetLine> maconomyTimes, JobTO trafficJob,
                                           Map<String, Integer> lineItemNumberByIstanceKey) {
    	
    	String maconomyBudgetUUIDProperty = "";
        Map<String, JobStageTO> trafficStagesByIstanceKey = new HashMap<>();
        updateCreateStages(maconomyStages, trafficJob, trafficStagesByIstanceKey, lineItemNumberByIstanceKey, maconomyBudgetUUIDProperty);
        updateCreateTasks(maconomyTimes, trafficJob, trafficStagesByIstanceKey, lineItemNumberByIstanceKey, maconomyBudgetUUIDProperty);
    }

    private void updateCreateTasks(Set<JobBudgetLine> maconomyTimes, JobTO job, Map<String, JobStageTO> trafficStagesByIstanceKey,
                                   Map<String, Integer> lineItemNumberByIstanceKey,
                                   String maconomyUuidproperty) {

        Set<JobTaskTO> result = new HashSet<>();
        Set<JobBudgetLine> oldMaconomyTime = filterOldMaconomyLines(maconomyTimes, maconomyUuidproperty);
        Set<JobBudgetLine> newMaconomyTime = filterNewMaconomyLines(maconomyTimes, maconomyUuidproperty);
        List<ValidationResult<JobTaskTO>> taskCannotBeDeleted = new ArrayList<>();
//        Replace with the Rest Job validation request.
//        		itemsCannotBeDeletedByClass(jobService.validateJobByBusinessRules(job), JobTaskTO.class);

        Map<String, JobTaskTO> jobTaskByUuid = mapItemWithUuid(job.getJobTasks());

        for (JobBudgetLine maconomyTime : oldMaconomyTime) {
            String taskUuid = maconomyTime.getAdditionalProperties().get(maconomyUuidproperty).toString();
            if (jobTaskByUuid.containsKey(taskUuid)) {
                JobTaskTO existingJobTask = jobTaskByUuid.get(taskUuid);
                mapMaconomyLineToJobTask(maconomyTime, existingJobTask, trafficStagesByIstanceKey, maconomyUuidproperty, lineItemNumberByIstanceKey);
                result.add(existingJobTask);
            } else {
                newMaconomyTime.add(maconomyTime);
            }
        }
        for (JobBudgetLine maconomyTime : newMaconomyTime) {
            JobTaskTO jobTask = new JobTaskTO();
            jobTask.setUuid(UUID.randomUUID().toString());
            mapMaconomyLineToJobTask(maconomyTime, jobTask, trafficStagesByIstanceKey, maconomyUuidproperty, lineItemNumberByIstanceKey);
            result.add(jobTask);
        }
        result = keepRemovedItemCannotBeDeleted(jobTaskByUuid, taskCannotBeDeleted, result);
        job.setJobTasks(result);
    }

    private <T extends AbstractLineItemTO> Map<String, T> mapItemWithUuid(Set<T> lineItems) {
        Map<String, T> result = new HashMap<>();
        for (T item : lineItems) {
            result.put(item.getUuid(), item);
        }
        return result;
    }

    private Set<JobBudgetLine> filterNewMaconomyLines(Set<JobBudgetLine> maconomyTimes, String maconomyUuidproperty) {
        return maconomyTimes.stream().filter(line -> StringUtils.isBlank(line.getAdditionalProperties().get(maconomyUuidproperty).toString())).collect(Collectors.toSet());
    }

    private Set<JobBudgetLine> filterOldMaconomyLines(Set<JobBudgetLine> maconomyTimes, String maconomyUuidproperty) {
        return maconomyTimes.stream().filter(line -> StringUtils.isNoneBlank(line.getAdditionalProperties().get(maconomyUuidproperty).toString())).collect(Collectors.toSet());
    }

    private void mapMaconomyLineToJobTask(JobBudgetLine maconomyTime, JobTaskTO jobTask, Map<String, JobStageTO> trafficStagesByIstanceKey, 
			  String maconomyUuidproperty,
            Map<String, Integer> lineItemNumberByIstanceKey
            ) {
    	throw new UnsupportedOperationException("Implement the below method");
    }    
    
    private void mapMaconomyLineToJobTask(JobBudgetLine maconomyTime, JobTaskTO jobTask, Map<String, JobStageTO> trafficStagesByIstanceKey, 
    									  String maconomyUuidproperty,
                                          Map<String, Integer> lineItemNumberByIstanceKey,
                                          Collection<ChargeBandTO> allChargeBands) {
        mapMaconomyLinePropertiesToAbstractLineItem(maconomyTime, jobTask, lineItemNumberByIstanceKey);
        if (StringUtils.isNoneBlank(maconomyTime.getParentjobbudgetlineinstancekey())) {
            String parentUuid = trafficStagesByIstanceKey.get(maconomyTime.getParentjobbudgetlineinstancekey()).getUuid();
            jobTask.setJobStageUUID(parentUuid);
        } else {
            jobTask.setJobStageUUID("");
        }
        if(jobTask.isUnpersisted()){
            BigDecimal studioAllocatedMinutes = jobTask.getQuantity().multiply(new BigDecimal(60));
            jobTask.setStudioAllocationMinutes(studioAllocatedMinutes.intValue());
        }
        jobTask.setExternalData(SYNC_OK);
        updateUuuidOnMaconomyLine(maconomyTime, jobTask.getUuid(), maconomyUuidproperty);
        jobTask.setHierarchyOrder(lineItemNumberByIstanceKey.get(maconomyTime.getInstancekey()));
        chargebandTaskFromMaconomyLine(maconomyTime, jobTask, allChargeBands);
    }

    private void updateUuuidOnMaconomyLine(JobBudgetLine maconomyLine, String uuid, String maconomyUuidproperty) {
        maconomyLine.setAdditionalProperty(maconomyUuidproperty, uuid);
    }

    private void chargebandTaskFromMaconomyLine(JobBudgetLine maconomyTime, JobTaskTO jobTask, Collection<ChargeBandTO> allChargeBands) {
    	
    	//If we encounter an incoming Milestone, lets accept the values and create a corresponding TrafficLIVE Line Type of Milestone.
    	if (MACONOMY_MILESTONE_TYPE.equals(maconomyTime.getLinetype())) {
    		jobTask.setJobTaskCategory(JobTaskCategoryType.MILESTONE);
    		jobTask.setChargeBandId(null);
    		return;
    	} 
    	//now we know the incoming line is a Task (i.e. not a MACONOMY_MILESTONE, lets check to see if the 
    	//existing TL Task was milestone. If it is, then we should ensure that the Task Type is reset to 
    	//the default FEE, which is how all other new lines from Maconomy Arrive.
    	if (JobTaskCategoryType.MILESTONE.equals(jobTask.getJobTaskCategory())) {
        	jobTask.setJobTaskCategory(JobTaskCategoryType.FEE);
    	}
        Set<ChargeBandTO> timeChargebands = filterTimeChargeband(allChargeBands);
        List<ChargeBandTO> result = findTaskChargebandByMaconomyLine(maconomyTime, timeChargebands);
        if (result.size() == 0) {
            throw new BudgetIntegrationException(String.format("Cannot find any chargeband with '%s' as externalCode and '%s' as secondary external code. " +
                    "They must be set to the corresponding Maconomy task's name and employee category number.", maconomyTime.getTaskname(), maconomyTime.getEmployeecategorynumber()));
        } else if (result.size() > 1) {
            throw new BudgetIntegrationException(String.format("Found '%d' chargebands with '%s' as externalCode and '%s' " +
                    "as secondary external code when only one can exist. ", result.size(), maconomyTime.getTaskname(), maconomyTime.getEmployeecategorynumber()));
        } else {
            jobTask.setChargeBandId(new Identifier(result.get(0).getId()));
        }
    }

    private List<ChargeBandTO> findTaskChargebandByMaconomyLine(JobBudgetLine maconomyTime, Set<ChargeBandTO> timeChargebands) {
        return timeChargebands.stream().filter(cb -> maconomyTime.getTaskname().equals(cb.getExternalCode()) &&
                maconomyTime.getEmployeecategorynumber().equals(cb.getSecondaryExternalCode())).collect(Collectors.toList());
    }

    private Set<ChargeBandTO> filterTimeChargeband(Collection<ChargeBandTO> allChargeBands) {
        return allChargeBands.stream().filter(cb -> isATimeMaconomyChargeband(cb)).collect(Collectors.toSet());
    }

    private boolean isATimeMaconomyChargeband(ChargeBandTO chargeBand){
        return ChargeBandType.TIME_FEE.equals(chargeBand.getChargeBandType()) && chargeBand.getExternalCode() != null && chargeBand.getSecondaryExternalCode() != null;
    }

    private void updateCreateStages(Set<JobBudgetLine> maconomyStages, JobTO job, Map<String, JobStageTO> trafficStagesByIstanceKey,
                                    Map<String, Integer> lineItemNumberByIstanceKey,
                                    String maconomyBudgetUUIDProperty) {

        Map<String, JobStageTO> existingTrafficStagesByUiid = jobStagesByUiid(job.getJobStages());
        Set<JobBudgetLine> oldMaconomyStages = filterOldMaconomyLines(maconomyStages, maconomyBudgetUUIDProperty);
        Set<JobBudgetLine> newMaconomyStages = filterNewMaconomyLines(maconomyStages, maconomyBudgetUUIDProperty);

        for (JobBudgetLine oldMaconomyStage : oldMaconomyStages) {
            String stageUUid = oldMaconomyStage.getAdditionalProperties().get(maconomyBudgetUUIDProperty).toString();

            if (existingTrafficStagesByUiid.containsKey(stageUUid)) {
                JobStageTO existingJobStage = existingTrafficStagesByUiid.get(stageUUid);
                existingJobStage.setHierarchyOrder(lineItemNumberByIstanceKey.get(oldMaconomyStage.getInstancekey()));
                mapMaconomyLineToStage(oldMaconomyStage, existingJobStage);
                trafficStagesByIstanceKey.put(oldMaconomyStage.getInstancekey(), existingJobStage);
            } else {
                newMaconomyStages.add(oldMaconomyStage);
            }
        }

        for (JobBudgetLine maconomyStage : newMaconomyStages) {
            JobStageTO jobStage = createJobStageFromMaconomyLine(maconomyStage, maconomyBudgetUUIDProperty);
            jobStage.setHierarchyOrder(lineItemNumberByIstanceKey.get(maconomyStage.getInstancekey()));
            trafficStagesByIstanceKey.put(maconomyStage.getInstancekey(), jobStage);
        }

        updateParentUuidOnStages(maconomyStages, trafficStagesByIstanceKey);
        updateStageItemOrder(trafficStagesByIstanceKey, lineItemNumberByIstanceKey);

        job.setJobStages(new HashSet(trafficStagesByIstanceKey.values()));
    }

    private void updateStageItemOrder(Map<String, JobStageTO> trafficStagesByIstanceKey, Map<String, Integer> lineItemNumberByIstanceKey) {
        for (String istanceKey : trafficStagesByIstanceKey.keySet()) {
            JobStageTO stage = trafficStagesByIstanceKey.get(istanceKey);
            stage.setHierarchyOrder(lineItemNumberByIstanceKey.get(istanceKey));
        }
    }

    private void updateParentUuidOnStages(Set<JobBudgetLine> maconomyStages, Map<String, JobStageTO> trafficStagesByIstanceKey) {
        for (JobBudgetLine maconomyStage : maconomyStages) {
            JobStageTO stageToUpdate = trafficStagesByIstanceKey.get(maconomyStage.getInstancekey());
            if (StringUtils.isNoneBlank(maconomyStage.getParentjobbudgetlineinstancekey())) {
                String parentStageUuid = trafficStagesByIstanceKey.get(maconomyStage.getParentjobbudgetlineinstancekey()).getUuid();
                stageToUpdate.setParentStageUUID(parentStageUuid);
                trafficStagesByIstanceKey.put(maconomyStage.getInstancekey(), stageToUpdate);
            } else {
                stageToUpdate.setParentStageUUID("");
            }
        }
    }

    private Map<String, JobStageTO> jobStagesByUiid(Set<JobStageTO> jobStages) {
        Map<String, JobStageTO> result = new HashMap<>();
        for (JobStageTO stage : jobStages) {
            result.put(stage.getUuid(), stage);
        }
        return result;
    }

    private JobStageTO createJobStageFromMaconomyLine(JobBudgetLine maconomyStageLine, String maconomyUuidproperty) {
        JobStageTO jobStage = new JobStageTO();
        mapMaconomyLineToStage(maconomyStageLine, jobStage);
        jobStage.setUuid(UUID.randomUUID().toString());
        updateUuuidOnMaconomyLine(maconomyStageLine, jobStage.getUuid(), maconomyUuidproperty);
        return jobStage;
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

    private CardTableContainer<JobBudget, JobBudgetLine> updateBudget(CardTableContainer<JobBudget, JobBudgetLine> budgetData, MaconomyPSORestContext mrc) {
    	return mrc.jobBudget().update(budgetData.card());
    }

    private CardTableContainer<JobBudget, JobBudgetLine> submitBudget(CardTableContainer<JobBudget, JobBudgetLine> budgetData, MaconomyPSORestContext mrc) {
    	return mrc.jobBudget().postToAction("action:submitbudget", budgetData.card());
    }

    private CardTableContainer<JobBudget, JobBudgetLine> approveBudget(CardTableContainer<JobBudget, JobBudgetLine> budgetData, MaconomyPSORestContext mrc) {
    	return mrc.jobBudget().postToAction("action:approvebudget", budgetData.card());
    }

    private String getCurrentUserName() {
    	return "";
//        Long userId = userContextLoader.loadUserContext().getId();
//        TrafficCompanyUserEntity trafficCompanyUserEntity = trafficCompanyUserDAO.getByUserId(userId);
//        return trafficCompanyUserEntity.getTrafficEmployee().getEmployeeDetails().getPersonalDetails().getFullName();
    }

    private CardTableContainer<JobBudget, JobBudgetLine> reopenBudget(CardTableContainer<JobBudget, JobBudgetLine> budgetData, MaconomyPSORestContext restClientContext) {
    	budgetData = restClientContext.jobBudget().postToAction("action:reopenbudget",
        		budgetData.card());
    	return budgetData;
    }

    private CardTableContainer<JobBudget, JobBudgetLine> getMaconomyBudgetForType(String jobNumber, String budgetType, MaconomyPSORestContext mrc) {
    	return mrc.jobBudget().data(String.format("data;jobnumber=%s?card.showbudgettypevar=%s", 
    									UriComponent.encode(jobNumber, Type.QUERY_PARAM_SPACE_ENCODED),
    									UriComponent.encode(budgetType, Type.QUERY_PARAM_SPACE_ENCODED)));
    }

//    private CardTableContainer<JobBudget, JobBudgetLine> getMaconomyBudget(WebTarget target, String jobNumber) {
//        String encodedJobNumber = urlSafeEncodedString(jobNumber);
//        WebTarget getTarget = target.path("jobbudgets").path(String.format("data;jobnumber=%s", encodedJobNumber));
//        return executeRequest(getTarget);
//    }

//    private CardTableContainer<JobBudget, JobBudgetLine> executeRequest(WebTarget getTarget){
//        Invocation.Builder getInvocationBuilder = getTarget.request(MediaType.APPLICATION_JSON);
//        Response getResponse = getInvocationBuilder.get();
//
//        if(LOG.isDebugEnabled()) {
//        	LOG.debug("GET executeRequest() Response Info: "+getResponse);
//        }
//
//        checkThrowApplicationExceptionFromResponse(getResponse);
//        return getResponse.readEntity(new GenericType<CardTableContainer<JobBudget, JobBudgetLine>>() {
//        });
//    }

//    private String urlSafeEncodedString(String jobNumber){
//        try{
//            return URLEncoder.encode(jobNumber, "UTF-8").replaceAll("\\+", "%20");
//        }catch (UnsupportedEncodingException ex){
//            throw new BudgetIntegrationException(ex.getMessage());
//        }
//    }
//

    private CardTableContainer<JobBudget, JobBudgetLine> createLines(
    								List<Record<JobBudgetLine>> initialisedLinesToCreate, 
    								CardTableContainer<JobBudget, JobBudgetLine> budgetData, 
    								IntegrationDetailsHolder integrationSettings, 
    								MaconomyPSORestContext mrc
    								) {
        Map<String, String> instanceKeySubstitution = new HashMap<>();

        String concurrencyControl =  null;
        for (Record<JobBudgetLine> line : initialisedLinesToCreate) {
            if (StringUtils.isNoneBlank(line.getData().getParentjobbudgetlineinstancekey())) {
                line.getData().setParentjobbudgetlineinstancekey(instanceKeySubstitution.get(line.getData().getParentjobbudgetlineinstancekey()));
            }
            if(concurrencyControl == null)
            	concurrencyControl = line.getMeta().getConcurrencyControl();
            
            //ensure the current record has the latest concurrency control.
            line.getMeta().setConcurrencyControl(concurrencyControl);
            budgetData = mrc.jobBudget().addTableRecord(line);
            //Retrieve the newly created line, store the old key to the new key (for parent hierarchy accuracy).
            List<Record<JobBudgetLine>> budgetLines = budgetData.getPanes().getTable().getRecords();
            Record<JobBudgetLine> createdLine = budgetLines.get(budgetLines.size() - 1);
            instanceKeySubstitution.put(line.getData().getInstancekey(), createdLine.getData().getInstancekey());
            
            //As a new line has been added, we have a new concurrencycontrol which needs to be used for any subsequent calls.
            concurrencyControl = createdLine.getMeta().getConcurrencyControl();
        }

        return budgetData;
    }

    private List<Record<JobBudgetLine>> initiateLines(JobTO jobTO, CardTableContainer<JobBudget, JobBudgetLine> budgetData, 
															IntegrationDetailsHolder integrationSettings, MaconomyPSORestContext mrc) {
        List<Record<JobBudgetLine>> result = new ArrayList<>();
        Record<JobBudgetLine> templateJobBudgetLine = initialiseMaconomyLine(budgetData, mrc);
        result.addAll(initiateTasksAndStages(jobTO.getJobTasks(), jobTO.getJobStages(), templateJobBudgetLine, integrationSettings));
        result.addAll(initiateThirdPartyCosts(jobTO.getJobThirdPartyCosts(), templateJobBudgetLine, integrationSettings));
        result.addAll(initiateExpenses(jobTO.getJobExpenses(), templateJobBudgetLine, integrationSettings));
        return result;
    }

    private List<Record<JobBudgetLine>> initiateExpenses(Set<JobExpenseTO> jobExpenses, 
			Record<JobBudgetLine> templateJobBudgetLine,
			IntegrationDetailsHolder integrationSettings) {
        return initiateLineItems(jobExpenses, templateJobBudgetLine, integrationSettings);
    }

    private List<Record<JobBudgetLine>> initiateThirdPartyCosts(Set<JobThirdPartyCostTO> jobThirdPartyCosts, 
			Record<JobBudgetLine> templateJobBudgetLine,
			IntegrationDetailsHolder integrationSettings) {
        return initiateLineItems(jobThirdPartyCosts, templateJobBudgetLine, integrationSettings);
    }

    private <T extends AbstractLineItemTO> List<Record<JobBudgetLine>> initiateLineItems(Set<T> lineItemTOs, 
			Record<JobBudgetLine> templateJobBudgetLine,
			IntegrationDetailsHolder integrationSettings) {
    	
        List<Record<JobBudgetLine>> result = new ArrayList<>();
        for (T lineItemTO : lineItemTOs) {
//          Record<JobBudgetLine> maconomyLine = initialiseMaconomyLine(client, addUrl, concurrencyControl);
        	Record<JobBudgetLine> maconomyLine = initialiseMaconomyLineFromTemplateLine(templateJobBudgetLine);
            mapThirdPartyCostOrExpensesToMaconomyLine(lineItemTO, maconomyLine.getData(), integrationSettings);
            result.add(maconomyLine);
        }
        return sortInitialisedLines(result);
    }

    private List<Record<JobBudgetLine>> initiateTasksAndStages(Collection<JobTaskTO> jobTasks, Collection<JobStageTO> jobStages,
    																	Record<JobBudgetLine> templateJobBudgetLine,
    																		IntegrationDetailsHolder integrationDetails) {
        List<Record<JobBudgetLine>> result = new ArrayList<>();
        Map<String, Record<JobBudgetLine>> maconomyStageByTLUuid = new HashMap<>();
        
        for (JobStageTO trafficStage : jobStages) {
            Record<JobBudgetLine> maconomyStage = initialiseMaconomyLineFromTemplateLine(templateJobBudgetLine);
            maconomyStageByTLUuid.put(trafficStage.getUuid(), maconomyStage);
            result.add(maconomyStage);
            mapStageToMaconomyLine(trafficStage, maconomyStage.getData(), integrationDetails.getMaconomyBudgetUUIDProperty());
        }

        for (JobStageTO trafficStage : jobStages) {
            if (StringUtils.isNotBlank(trafficStage.getParentStageUUID())) {
                Record<JobBudgetLine> maconomyLine = maconomyStageByTLUuid.get(trafficStage.getUuid());
                Record<JobBudgetLine> maconomyParentStage = maconomyStageByTLUuid.get(trafficStage.getParentStageUUID());
                maconomyLine.getData().setParentjobbudgetlineinstancekey(maconomyParentStage.getData().getInstancekey());
            }
        }

        for (JobTaskTO trafficTask : jobTasks) {
        	Record<JobBudgetLine> maconomyTask = initialiseMaconomyLineFromTemplateLine(templateJobBudgetLine);

            mapTaskToMaconomyLine(trafficTask, maconomyTask.getData(), integrationDetails);

            if (trafficTask.getJobStageUUID() != null) {
                Record<JobBudgetLine> parentMaconomyStage = maconomyStageByTLUuid.get(trafficTask.getJobStageUUID());
                maconomyTask.getData().setParentjobbudgetlineinstancekey(parentMaconomyStage.getData().getInstancekey());
            }
            result.add(maconomyTask);
        }

        return sortInitialisedLines(result);
    }

	private Record<JobBudgetLine> initialiseMaconomyLineFromTemplateLine(Record<JobBudgetLine> templateLine) {
    	//return a value copy
    	try {
    		//TODO runtime startup error, there is no objectMapper instance in this context (it belongs to the API servlet context)
    		//Work out another way to copy the object.
        	String templateString = objectMapper.writeValueAsString(templateLine);
        	Record<JobBudgetLine> copy = objectMapper.readValue(templateString, templateLine.getClass());
        	copy.getData().setInstancekey("JobBudgetLine"+UUID.randomUUID().toString());
        	//TODO Do we need to reset the instance key here? Testing implies we do not.
        	return copy;
    	} catch (Exception e) {
    		throw new BudgetIntegrationException("Error initialising templateline");
    	}
    }
	
	private void mapStageToMaconomyLine(JobStageTO trafficStage, JobBudgetLine maconomyLine, String maconomyBudgetUUIDProperty) {
        maconomyLine.setText(trafficStage.getDescription());
        maconomyLine.setLinenumber(trafficStage.getHierarchyOrder());
        maconomyLine.setAdditionalProperty(maconomyBudgetUUIDProperty, trafficStage.getUuid());
    }

    private void mapTaskToMaconomyLine(JobTaskTO trafficTask, JobBudgetLine maconomyLine, IntegrationDetailsHolder integrationDetails) {
        mapAbstractLineItemPropertiesToMaconomyLine(trafficTask, maconomyLine, integrationDetails.getMaconomyBudgetUUIDProperty());
        maconomyLine.setLinenumber(trafficTask.getHierarchyOrder());
        
        //Only populate the taskname and employeecategorynumber if we have a non milestone, as this information
        //is retrieved from the chargeband relation.
        if(JobTaskCategoryType.MILESTONE.equals(trafficTask.getJobTaskCategory())) {
        	maconomyLine.setLinetype(MACONOMY_MILESTONE_TYPE);
        } else {
            ChargeBandTO chargeBand = integrationDetails.getTrafficLiveChargeBands().get(trafficTask.getChargeBandId());
            if (chargeBand == null || StringUtils.isBlank(chargeBand.getExternalCode()))
                throw new BudgetIntegrationException(String.format("Chargeband '%s' has empty externalCode. It must be set to a corresponding Maconomy task's name.", chargeBand.getName()));
            maconomyLine.setTaskname(chargeBand.getExternalCode());
            maconomyLine.setEmployeecategorynumber(chargeBand.getSecondaryExternalCode());
        }

        String timeZone = integrationDetails.getTlCompanyTimezone();
        if (trafficTask.getTaskStartDate() != null)
            maconomyLine.setThedate(formatAsLocalTimeDate(trafficTask.getTaskStartDate(), timeZone));
        if (trafficTask.getTaskDeadline() != null)
            maconomyLine.setClosingdate(formatAsLocalTimeDate(trafficTask.getTaskDeadline(), timeZone));
    }

    private void mapThirdPartyCostOrExpensesToMaconomyLine(AbstractLineItemTO trafficLine, JobBudgetLine maconomyLine, IntegrationDetailsHolder integrationSettings) {
        mapAbstractLineItemPropertiesToMaconomyLine(trafficLine, maconomyLine, integrationSettings.getMaconomyBudgetUUIDProperty());
        ChargeBandTO chargeBand = integrationSettings.getTrafficLiveChargeBands().get(trafficLine.getChargeBandId());
        if (StringUtils.isBlank(chargeBand.getSecondaryExternalCode()))
            throw new BudgetIntegrationException(String.format("Chargeband '%s' has empty secondaryExternalCode. It must be set to a corresponding Maconomy task's name.", chargeBand.getName()));
        maconomyLine.setTaskname(chargeBand.getSecondaryExternalCode());
    }


    private void mapAbstractLineItemPropertiesToMaconomyLine(AbstractLineItemTO trafficLine, JobBudgetLine maconomyLine, String maconomyBudgetUUIDProperty) {
        maconomyLine.setText(trafficLine.getDescription());
        maconomyLine.setLinenumber(trafficLine.getLineItemOrder());
        maconomyLine.setNumberof(trafficLine.getQuantity().doubleValue());
        maconomyLine.setShowcostpricelowervar(trafficLine.getCost().getAmountString().multiply(BigDecimal.valueOf(100)).intValue());
        maconomyLine.setBillingpricecurrency(trafficLine.getRate().getAmountString().multiply(BigDecimal.valueOf(100)).intValue());
        maconomyLine.setAdditionalProperty(maconomyBudgetUUIDProperty, trafficLine.getUuid());
    }

    private void mapMaconomyLineToStage(JobBudgetLine maconomyLine, JobStageTO stage) {
        stage.setDescription(maconomyLine.getText());
        stage.setHierarchyOrder(maconomyLine.getLinenumber());
        stage.setBillLineItemOrder(maconomyLine.getLinenumber());
    }

    private void mapMaconomyLinePropertiesToAbstractLineItem(JobBudgetLine maconomyLine, AbstractLineItemTO lineItem,
                                                             Map<String, Integer> lineItemNumberByIstanceKey) {
        CurrencyType currency = CurrencyType.GBP;
        Identifier taxTypeOneId = new Identifier(1l);
        
        lineItem.setDescription(maconomyLine.getText());
        lineItem.setLineItemOrder(lineItemNumberByIstanceKey.get(maconomyLine.getInstancekey()));
        lineItem.setBillLineItemOrder(lineItemNumberByIstanceKey.get(maconomyLine.getInstancekey()));
        lineItem.setQuantity(new BigDecimal(maconomyLine.getNumberof()));
        lineItem.setCost(new MoneyTO(BigDecimal.valueOf(maconomyLine.getShowcostpricelowervar()).divide(BigDecimal.valueOf(100)), currency));
        lineItem.setRate(new PrecisionMoneyTO(BigDecimal.valueOf(maconomyLine.getBillingpricecurrency()).divide(BigDecimal.valueOf(100)), currency));
        lineItem.setMultiplier(new BigDecimal(maconomyLine.getMarkuppercentage()));
        lineItem.setTaxTypeId(taxTypeOneId);
        lineItem.setTotal(new MoneyTO(lineItem.getQuantity().multiply(lineItem.getRate().getAmountString()), currency));
        lineItem.setBillableNet(new MoneyTO(lineItem.getQuantity().multiply(lineItem.getRate().getAmountString()), currency));
    }

    private List<Record<JobBudgetLine>> sortInitialisedLines(Collection<Record<JobBudgetLine>> initiatedLines) {
        return orderedListFromCollection(initiatedLines, new Comparator<Record<JobBudgetLine>>() {
            @Override
            public int compare(Record<JobBudgetLine> o1, Record<JobBudgetLine> o2) {
                return o1.getData().getLinenumber().compareTo(o2.getData().getLinenumber());
            }
        });
    }

    private <T> List<T> orderedListFromCollection(Collection<T> initiatedLines, Comparator<? super T> comparator) {
        List<T> result = new ArrayList<>(initiatedLines);
        result.sort(comparator);
        return result;
    }

    private Record<JobBudgetLine> initialiseMaconomyLine(CardTableContainer<JobBudget, JobBudgetLine> budgetData, MaconomyPSORestContext mrc) {
    	return mrc.jobBudget().initTable(budgetData.getPanes().getTable());
    }

    private CardTableContainer<JobBudget, JobBudgetLine> addMaconomyLine(Record<JobBudgetLine> record,  MaconomyPSORestContext mrc) {
    	return mrc.jobBudget().addTableRecord(record);
    }

    private CardTableContainer<JobBudget, JobBudgetLine> updateMaconomyLine(Record<JobBudgetLine> record,  MaconomyPSORestContext mrc) {
    	return mrc.jobBudget().update(record);
    }

    private CardTableContainer<JobBudget, JobBudgetLine> deleteBudgetItems(CardTableContainer<JobBudget, JobBudgetLine> budgetData, MaconomyPSORestContext mrc) {
        Record<JobBudget> budget = budgetData.card();
        return mrc.jobBudget().postToAction("action:removebudget", budget);
    }

//    private void deleteFromMaconomy(Client client, String url, String concurrencyControl) {
//        WebTarget target = client.target(url);
//
//        Invocation.Builder invocationBuilder = target.request(MediaType.APPLICATION_JSON);
//        if (StringUtils.isNotBlank(concurrencyControl)) {
//            invocationBuilder = invocationBuilder.header("Maconomy-Concurrency-Control", concurrencyControl);
//        }
//
//        Response response = invocationBuilder.delete();
//        
//        if(LOG.isDebugEnabled()) {
//        	LOG.debug("deleteFromMaconomy() Response Info: "+response);
//        }
//        
//        checkThrowApplicationExceptionFromResponse(response);
//        
//    }
//    
//    private <T> T postToMaconomy(Client client, Record data, GenericType<T> responseType, String url) {
//        return (postToMaconomy(client, data, responseType, url, data.getMeta().getConcurrencyControl()));
//    }
    
    
}
