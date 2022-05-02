package fr.openent.supportpivot.helpers;

import fr.openent.supportpivot.constants.PivotConstants;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateHelper {
    public static final String TIME_ZONE_FORMAT = "z";
    public static final String DATE_FORMAT = "dd/MM/yyyy' 'HH:mm";
    public static final String DATE_FORMAT_PARSE = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private static final Logger log = LoggerFactory.getLogger(DateHelper.class);

    private DateHelper() {
    }

    /***
     * convert the string in sql format(2022-05-03T08:05:16.307) to date in the format yyyy-MM-dd'T'HH:mm:ss.SSS
     * @param stringDate String
     * @return date, example of date format : Tue May 03 07:52:17 GMT 2022
     */
    public static Date convertStringToDate(String stringDate) {
        Date date = new Date();
        try {
            date = new SimpleDateFormat(DATE_FORMAT_PARSE).parse(stringDate);
        } catch (ParseException e) {
            String message = String.format("[SupportPivot@%s::convertStringToDate] Supportpivot : invalid date format : %s",
                    DateHelper.class.getSimpleName(), e.getMessage());
            log.error(message);
        }
        return date;
    }

    /***
     * convert to date to string with the time zone in the format dd/MM/yyyy' 'HH:mm
     * @param date Date
     * @return date in string for jira field, example of date format : 22/04/2022 8:40
     */
    public static String convertDateFormat(Date date) {
        DateTime dateTime = new DateTime(date);
        return dateTime.toDateTime(DateTimeZone.forTimeZone(setTimeZone(PivotConstants.TIME_ZONE))).toString(DATE_FORMAT);
    }

    /***
     * set the timeZone with use the simpleDateFormat (z) to add the time zone to the date
     * @param timeZone String
     * @return TimeZone choice with ianaName, example of ianaName Eupore/Paris
     */
    public static TimeZone setTimeZone(String timeZone) {
        TimeZone tz = TimeZone.getDefault();
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(TIME_ZONE_FORMAT);
            sdf.parse(timeZone);
            String ianaName = sdf.getTimeZone().getID();
            tz = TimeZone.getTimeZone(ianaName);
        } catch (ParseException e) {
            String message = String.format("[SupportPivot@%s::setTimeZone] Supportpivot : invalid time zone : %s",
                    DateHelper.class.getSimpleName(), e.getMessage());
            log.error(message);
        }
        return tz;
    }
}
