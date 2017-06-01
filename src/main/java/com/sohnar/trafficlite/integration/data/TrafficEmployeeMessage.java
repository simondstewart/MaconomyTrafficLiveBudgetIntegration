package com.sohnar.trafficlite.integration.data;

import java.io.Serializable;

import com.sohnar.trafficlite.datamodel.enums.event.TrafficEmployeeEventType;
import com.sohnar.trafficlite.transfer.BaseTO;
import com.sohnar.trafficlite.transfer.Identifier;
import com.sohnar.trafficlite.transfer.event.MessageRestrictionsTO;
import com.sohnar.trafficlite.transfer.event.TrafficEmployeeEventTO;


public class TrafficEmployeeMessage<TO extends BaseTO> implements Serializable {

	private static final long serialVersionUID = 1L;
	private Long trafficEmployeeId;
	private String description;
	final private TrafficEmployeeEventTO<TO> trafficEmployeeEvent;
    private MessageRestrictionsTO restrictions;
	private Throwable exception;
    
	public TrafficEmployeeMessage(TrafficEmployeeEventType eventType, Long trafficEmployeeId, String description) {
		super();
		this.trafficEmployeeId = trafficEmployeeId;
		this.description = description;
		trafficEmployeeEvent = new TrafficEmployeeEventTO();
		trafficEmployeeEvent.setTrafficEmployeeEventType(eventType);
		trafficEmployeeEvent.setTrafficEmployeeId(new Identifier(trafficEmployeeId));
		trafficEmployeeEvent.setDescription(description);
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

	public TrafficEmployeeEventTO<TO> getTrafficEmployeeEvent() {
		return trafficEmployeeEvent;
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
