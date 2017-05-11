package com.deltek.integration.budget.domainmapper;

import org.apache.commons.lang3.StringUtils;

import com.deltek.integration.budget.BudgetIntegrationException;
import com.deltek.integration.budget.IntegrationDetailsHolder;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.sohnar.trafficlite.transfer.financial.ChargeBandTO;
import com.sohnar.trafficlite.transfer.project.AbstractLineItemTO;

public class AbstractLineBudgetLineMapper extends BudgetLineMapper<AbstractLineItemTO>{

	public AbstractLineBudgetLineMapper(IntegrationDetailsHolder integrationDetailsHolder) {
		super(AbstractLineItemTO.class, integrationDetailsHolder);
	}

	@Override
	public JobBudgetLine convertTo(AbstractLineItemTO source, JobBudgetLine destination) {
		mapAbstractLineItemPropertiesToMaconomyLine(source, destination);
        ChargeBandTO chargeBand = getIntegrationDetailsHolder().getTrafficLiveChargeBands().get(source.getChargeBandId());
        if (StringUtils.isBlank(chargeBand.getSecondaryExternalCode()))
            throw new BudgetIntegrationException(String.format("Chargeband '%s' has empty secondaryExternalCode. It must be set to a corresponding Maconomy task's name.", chargeBand.getName()));
        destination.setTaskname(chargeBand.getSecondaryExternalCode());
		return destination;
	}

}
