package com.sohnar.trafficlite.integration.data;

import java.io.Serializable;

import com.sohnar.trafficlite.transfer.event.MessageRestrictionsTO;
import com.sohnar.trafficlite.transfer.event.SyncEventTOIF;

/**
 * Define the data that comprises a message sent to a TrafficCompany.  It is assumed the message is relevant for only 
 * the company identified in this message.
 * 
 * @author Admin
 *
 */
public class TrafficCompanyMessage implements Serializable {

	private static final long serialVersionUID = 1L;

	private Long tcId;
	
	private SyncEventTOIF syncEvent;

    private MessageRestrictionsTO restrictions;

    public TrafficCompanyMessage(Long tcId, SyncEventTOIF syncEvent, MessageRestrictionsTO restrictions) {
        super();
        this.tcId = tcId;
        this.syncEvent = syncEvent;
		this.restrictions = restrictions;
	}

	public Long getTcId() {
		return tcId;
	}

	public SyncEventTOIF getSyncEvent() {
		return syncEvent;
	}

    public MessageRestrictionsTO getRestrictions() {
        return restrictions;
    }

    public void setRestrictions(MessageRestrictionsTO restrictions) {
        this.restrictions = restrictions;
    }

    @Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("TrafficCompanyMessage [syncEvent=");
		builder.append(syncEvent);
		builder.append(", tcId=");
		builder.append(tcId);
		builder.append("]");
		return builder.toString();
	}
	
}
