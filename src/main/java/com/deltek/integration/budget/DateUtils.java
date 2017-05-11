package com.deltek.integration.budget;

import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.TimeZone;

import org.joda.time.MutableDateTime;

public class DateUtils {
	
	public static Calendar offsetUTCToClientTimeZone(Calendar timeToOffset, String timeZone) {
		return offsetUTCToClientTimeZone(timeToOffset, TimeZone.getTimeZone(timeZone));
	}

	public static Calendar offsetUTCToClientTimeZone(Calendar timeToOffset, TimeZone timeZone) {
		int offsetMilliseconds = timeZone.getOffset(timeToOffset.getTimeInMillis());
		int offsetMinutes = offsetMilliseconds / (1000 * 60);
		return offsetUTCToClientTimeZone(timeToOffset, -offsetMinutes);
	}

	public static Calendar offsetUTCToClientTimeZone(Calendar timeToOffset, Integer offsetMinutes) {
		MutableDateTime offsetTime = new MutableDateTime(timeToOffset);
		offsetTime.addMinutes(0 - offsetMinutes);
		return offsetTime.toGregorianCalendar();
	}
}
