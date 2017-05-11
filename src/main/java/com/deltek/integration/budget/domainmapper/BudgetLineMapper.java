package com.deltek.integration.budget.domainmapper;

import java.math.BigDecimal;

import org.dozer.DozerConverter;

import com.deltek.integration.budget.IntegrationDetailsHolder;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.sohnar.trafficlite.transfer.HasUuid;
import com.sohnar.trafficlite.transfer.project.AbstractLineItemTO;

public abstract class BudgetLineMapper<A extends HasUuid> extends DozerConverter<A, JobBudgetLine> {

	private IntegrationDetailsHolder integrationDetailsHolder;

	public BudgetLineMapper(Class<A> prototypeA) {
		super(prototypeA, JobBudgetLine.class);
	}

	public BudgetLineMapper(Class<A> prototypeA, IntegrationDetailsHolder integrationDetailsHolder) {
		super(prototypeA, JobBudgetLine.class);
		this.integrationDetailsHolder = integrationDetailsHolder;
	}
	
	public IntegrationDetailsHolder getIntegrationDetailsHolder() {
		return integrationDetailsHolder;
	}

	@Override
	public A convertFrom(JobBudgetLine source, A destination) {
		throw new RuntimeException("Not Implemented Yet");
	}

	protected void mapAbstractLineItemPropertiesToMaconomyLine(AbstractLineItemTO trafficLine, JobBudgetLine maconomyLine) {
        maconomyLine.setText(trafficLine.getDescription());
        maconomyLine.setLinenumber(trafficLine.getLineItemOrder());
        maconomyLine.setNumberof(trafficLine.getQuantity().doubleValue());
        maconomyLine.setShowcostpricelowervar(trafficLine.getCost().getAmountString().multiply(BigDecimal.valueOf(100)).intValue());
        maconomyLine.setBillingpricecurrency(trafficLine.getRate().getAmountString().multiply(BigDecimal.valueOf(100)).intValue());
        maconomyLine.setAdditionalProperty(integrationDetailsHolder.getMaconomyBudgetUUIDProperty(), trafficLine.getUuid());
    }

}
