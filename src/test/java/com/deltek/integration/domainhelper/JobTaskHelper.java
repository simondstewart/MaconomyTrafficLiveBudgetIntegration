package com.deltek.integration.domainhelper;

import java.math.BigDecimal;
import java.util.UUID;

import org.apache.commons.beanutils.BeanUtils;

import com.deltek.integration.domainhelper.JobTaskHelper.JobTaskHelperBuilder;
import com.sohnar.trafficlite.transfer.Identifier;
import com.sohnar.trafficlite.transfer.financial.MoneyTO;
import com.sohnar.trafficlite.transfer.financial.PrecisionMoneyTO;
import com.sohnar.trafficlite.transfer.project.JobTaskTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobTaskHelper {

	private Long id;
	private String uuid;
	private String description;
	private BigDecimal quantity;
	private MoneyTO cost;
	private PrecisionMoneyTO rate;
	private Identifier chargeBandId;
	private Integer lineItemOrder;
	private Integer hierarchyOrder;

	public JobTaskTO create() {
		try {
		JobTaskTO result = new JobTaskTO();
		BeanUtils.copyProperties(result, this);
		return result;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
	}
	
    public static class JobTaskHelperBuilder {
        public JobTaskHelperBuilder randomUUID() {
            this.uuid = UUID.randomUUID().toString(); 
            return this;
        }
    }	
}
