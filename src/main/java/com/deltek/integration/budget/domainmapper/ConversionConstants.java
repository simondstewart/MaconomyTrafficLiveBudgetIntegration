package com.deltek.integration.budget.domainmapper;

import static com.deltek.integration.budget.domainmapper.ConversionConstants.DATE_FORMAT;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import com.deltek.integration.budget.DateUtils;

public class ConversionConstants {

	public static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    public static final String MACONOMY_STAGE_TYPE = "sum/text";
    public static final String MACONOMY_TIME_TYPE = "time";
    public static final String MACONOMY_OUTLAY_TYPE = "outlay";
    public static final String MACONOMY_AMOUNT_TYPE = "amount";
    public static final String MACONOMY_MILESTONE_TYPE = "milestone";
    public static final String SYNC_OK = "Sync ok";
    public static final String SYNC_ERROR = "ERROR ";

    public static String formatAsLocalTimeDate(Calendar utcDateTime, String timeZone) {
        return DATE_FORMAT.format(DateUtils.offsetUTCToClientTimeZone(utcDateTime, timeZone).getTime());
    }


}
