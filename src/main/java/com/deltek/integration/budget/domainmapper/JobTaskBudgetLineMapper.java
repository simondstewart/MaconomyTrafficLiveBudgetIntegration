package com.deltek.integration.budget.domainmapper;

import static com.deltek.integration.budget.domainmapper.ConversionConstants.formatAsLocalTimeDate;

import org.apache.commons.lang3.StringUtils;

import com.deltek.integration.budget.BudgetIntegrationException;
import com.deltek.integration.budget.IntegrationDetailsHolder;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.sohnar.trafficlite.datamodel.enums.project.JobTaskCategoryType;
import com.sohnar.trafficlite.transfer.financial.ChargeBandTO;
import com.sohnar.trafficlite.transfer.project.JobTaskTO;

public class JobTaskBudgetLineMapper extends BudgetLineMapper<JobTaskTO> {

	public JobTaskBudgetLineMapper(IntegrationDetailsHolder integrationDetailsHolder) {
		super(JobTaskTO.class, integrationDetailsHolder);
	}

	@Override
	public JobBudgetLine convertTo(JobTaskTO source, JobBudgetLine destination) {
		mapTaskToMaconomyLine(source, destination, getIntegrationDetailsHolder());
		return destination;
	}

	@Override
	public JobTaskTO convertFrom(JobBudgetLine source, JobTaskTO destination) {
		throw new RuntimeException("Not Implemented Yet");
	}
	
    private void mapTaskToMaconomyLine(JobTaskTO trafficTask, JobBudgetLine maconomyLine, IntegrationDetailsHolder integrationDetails) {
        mapAbstractLineItemPropertiesToMaconomyLine(trafficTask, maconomyLine);
        maconomyLine.setLinenumber(trafficTask.getHierarchyOrder());
        
        //Only populate the taskname and employeecategorynumber if we have a non milestone, as this information
        //is retrieved from the chargeband relation.
        if(JobTaskCategoryType.MILESTONE.equals(trafficTask.getJobTaskCategory())) {
        	maconomyLine.setLinetype(ConversionConstants.MACONOMY_MILESTONE_TYPE);
        } else {
            ChargeBandTO chargeBand = integrationDetails.getTrafficLiveChargeBands().get(trafficTask.getChargeBandId());
            if (chargeBand == null) 
            	throw new BudgetIntegrationException("ChargeBand does not exist for id: "+trafficTask.getChargeBandId());
            
            if(StringUtils.isBlank(chargeBand.getExternalCode()))
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

}
