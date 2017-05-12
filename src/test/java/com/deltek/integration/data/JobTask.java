package com.deltek.integration.data;

import java.math.BigDecimal;

import com.sohnar.trafficlite.transfer.financial.MoneyTO;
import com.sohnar.trafficlite.transfer.financial.PrecisionMoneyTO;

import lombok.Builder;
import lombok.Data;


public @Data class JobTask {
	private String uuid;
	private Id chargeBandId;
	private BigDecimal quantity;
	private MoneyTO cost;
	private PrecisionMoneyTO rate;
}