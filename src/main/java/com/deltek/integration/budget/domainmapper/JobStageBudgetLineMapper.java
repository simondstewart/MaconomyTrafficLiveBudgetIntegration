package com.deltek.integration.budget.domainmapper;

import com.deltek.integration.budget.IntegrationDetailsHolder;
import com.deltek.integration.maconomy.psorestclient.domain.JobBudgetLine;
import com.sohnar.trafficlite.transfer.project.JobStageTO;

public class JobStageBudgetLineMapper extends BudgetLineMapper<JobStageTO> {

	public JobStageBudgetLineMapper(IntegrationDetailsHolder integrationDetailsHolder) {
		super(JobStageTO.class, integrationDetailsHolder);
	}

	@Override
	public JobBudgetLine convertTo(JobStageTO source, JobBudgetLine destination) {
		mapStageToMaconomyLine(source, destination, getIntegrationDetailsHolder().getMaconomyBudgetUUIDProperty());
		return destination;
	}

	private void mapStageToMaconomyLine(JobStageTO trafficStage, JobBudgetLine maconomyLine, String maconomyBudgetUUIDProperty) {
        maconomyLine.setText(trafficStage.getDescription());
        maconomyLine.setLinenumber(trafficStage.getHierarchyOrder());
        maconomyLine.setAdditionalProperty(maconomyBudgetUUIDProperty, trafficStage.getUuid());
    }

}
