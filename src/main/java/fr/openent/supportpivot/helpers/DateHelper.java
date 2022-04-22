package fr.openent.supportpivot.helpers;

import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateHelper {
    public static final String DATE_FORMAT = "yyyy/MM/dd' 'HH:mm";
    public static final String DATE_FORMAT_PARSE = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    private static final Logger log = LoggerFactory.getLogger(DateHelper.class);

    private DateHelper() {
    }

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

    public static String convertDateFormat(Date date) {
        return new SimpleDateFormat(DATE_FORMAT).format(date);
    }

}
