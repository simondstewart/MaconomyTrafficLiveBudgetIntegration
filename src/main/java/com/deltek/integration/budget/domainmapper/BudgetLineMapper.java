package com.deltek.integration.budget.domainmapper;

import java.math.BigDecimal;

import org.dozer.DozerConverter;

import com.deltek.integration.budget.IntegrationDetailsHolder;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.sohnar.trafficlite.transfer.HasUuid;
import com.sohnar.trafficlite.transfer.project.AbstractLineItemTO;
import com.sohnar.trafficlite.transfer.project.JobStageTO;

public abstract class BudgetLineMapper<A extends HasUuid> {

	private IntegrationDetailsHolder integrationDetailsHolder;

	public enum Context  {
		CREATE,
		UPDATE;
	}
	
	private final Class<A> prototypeA;
	
	public BudgetLineMapper(Class<A> prototypeA) {
		this.prototypeA = prototypeA;
	}

	public BudgetLineMapper(Class<A> prototypeA, IntegrationDetailsHolder integrationDetailsHolder) {
		this(prototypeA);
		this.integrationDetailsHolder = integrationDetailsHolder;
	}
	
	public abstract JobBudgetLine convertTo(A source, JobBudgetLine destination);
	
	public A convertFrom(JobBudgetLine source, A destination) {
		throw new RuntimeException("Not Implemented Yet");
	}

	public IntegrationDetailsHolder getIntegrationDetailsHolder() {
		return integrationDetailsHolder;
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
