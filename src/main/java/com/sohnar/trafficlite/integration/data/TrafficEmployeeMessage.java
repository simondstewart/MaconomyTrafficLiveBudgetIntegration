package com.sohnar.trafficlite.integration.data;

import java.io.Serializable;

import com.sohnar.trafficlite.transfer.event.MessageRestrictionsTO;
import com.sohnar.trafficlite.transfer.event.TrafficEmployeeEventTO;


public class TrafficEmployeeMessage implements Serializable {

	private static final long serialVersionUID = 1L;
	private Long trafficEmployeeId;
	private String description;
	private TrafficEmployeeEventTO<?> trafficEmployeeEvent;
    private MessageRestrictionsTO restrictions;
	private Throwable exception;
    
	public TrafficEmployeeMessage(Long trafficEmployeeId, String description) {
		super();
		this.trafficEmployeeId = trafficEmployeeId;
		this.description = description;
	}

	public TrafficEmployeeMessage(Long trafficEmployeeId, String description, MessageRestrictionsTO restrictions) {
		super();
		this.trafficEmployeeId = trafficEmployeeId;
		this.description = description;
        this.restrictions = restrictions;
	}

	public TrafficEmployeeMessage(Long trafficEmployeeId, String description, Throwable exception) {
		super();
		this.trafficEmployeeId = trafficEmployeeId;
		this.description = description;
		this.exception = exception;
	}

	public Long getTrafficEmployeeId() {
		return trafficEmployeeId;
	}
	public void setTrafficEmployeeId(Long userId) {
		this.trafficEmployeeId = userId;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String message) {
		this.description = message;
	}

	public TrafficEmployeeEventTO<?> getTrafficEmployeeEvent() {
		return trafficEmployeeEvent;
	}

	public void setTrafficEmployeeEvent(
			TrafficEmployeeEventTO<?> trafficEmployeeEvent) {
		this.trafficEmployeeEvent = trafficEmployeeEvent;
	}

    public MessageRestrictionsTO getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(MessageRestrictionsTO restrictions) {
        this.restrictions = restrictions;
    }

	public Throwable getException() {
		return exception;
	}

	public void setException(Throwable exception) {
		this.exception = exception;
	}
    
}
