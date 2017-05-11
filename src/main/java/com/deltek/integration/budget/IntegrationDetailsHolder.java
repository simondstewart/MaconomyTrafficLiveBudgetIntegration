package com.deltek.integration.budget;

import java.util.HashMap;
import java.util.Map;

import com.sohnar.trafficlite.transfer.Identifier;
import com.sohnar.trafficlite.transfer.financial.ChargeBandTO;


public class IntegrationDetailsHolder {

	private Map<Identifier, ChargeBandTO> trafficLiveChargeBands = new HashMap<>();
	
	private String macaonomyRestServiceURLBase;
	private String macaonomyUser;
	private String macaonomyPassword;
	private String maconomyBudgetType;
	private String maconomyBudgetUUIDProperty;
	private String tlCompanyTimezone;
	
	public IntegrationDetailsHolder(Map<Identifier, ChargeBandTO> trafficLiveChargeBands,
			String macaonomyRestServiceURLBase, String macaonomyUser, String macaonomyPassword,
			String maconomyBudgetType, String maconomyBudgetUUIDProperty, String tlCompanyTimezone) {
		super();
		this.trafficLiveChargeBands = trafficLiveChargeBands;
		this.macaonomyRestServiceURLBase = macaonomyRestServiceURLBase;
		this.macaonomyUser = macaonomyUser;
		this.macaonomyPassword = macaonomyPassword;
		this.maconomyBudgetType = maconomyBudgetType;
		this.maconomyBudgetUUIDProperty = maconomyBudgetUUIDProperty;
		this.tlCompanyTimezone = tlCompanyTimezone;
	}

	public Map<Identifier, ChargeBandTO> getTrafficLiveChargeBands() {
		return trafficLiveChargeBands;
	}

	public String getMacaonomyRestServiceURLBase() {
		return macaonomyRestServiceURLBase;
	}

	public String getMacaonomyUser() {
		return macaonomyUser;
	}

	public String getMacaonomyPassword() {
		return macaonomyPassword;
	}

	public String getMaconomyBudgetType() {
		return maconomyBudgetType;
	}

	public String getMaconomyBudgetUUIDProperty() {
		return maconomyBudgetUUIDProperty;
	}

	public String getTlCompanyTimezone() {
		return tlCompanyTimezone;
	}
}
