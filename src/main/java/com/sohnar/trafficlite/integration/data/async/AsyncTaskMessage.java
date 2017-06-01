package com.sohnar.trafficlite.integration.data.async;

import java.io.Serializable;

import com.sohnar.trafficlite.integration.data.TrafficEmployeeMessage;
import com.sohnar.trafficlite.transfer.BaseTO;

public class AsyncTaskMessage<DATA extends Serializable, TO extends BaseTO> implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String type;
	//A background process originates from an Employee Message
	private final TrafficEmployeeMessage<TO> employeeMessage;
	private final DATA data;
	
	public AsyncTaskMessage(String type, TrafficEmployeeMessage<TO> employeeMessage, DATA data) {
		super();
		this.type = type;
		this.employeeMessage = employeeMessage;
		this.data = data;
	}

	public String getType() {
		return type;
	}

	public TrafficEmployeeMessage<TO> getEmployeeMessage() {
		return employeeMessage;
	}

	public DATA getData() {
		return data;
	}
	
}
