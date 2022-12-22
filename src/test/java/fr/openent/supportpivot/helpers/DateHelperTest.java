package fr.openent.supportpivot.helpers;

import fr.openent.supportpivot.constants.Field;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

@RunWith(VertxUnitRunner.class)
public class DateHelperTest {
    private Vertx vertx;
    public static final String DATE_FORMAT = "dd/MM/yyyy' 'HH:mm";
    public static final String DATE_FORMAT_PARSE = "yyyy-MM-dd'T'HH:mm:ss.SSS";

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
    }

    @Test
    public void convertStringToDate_Should_Return_Date_In_Correct_Format(TestContext ctx) throws ParseException {
        String dateStr = "2022-05-03T08:05:16.307";
        Date dateExpected = new SimpleDateFormat(DATE_FORMAT_PARSE).parse("2022-05-03T08:05:16.307");
        ctx.assertEquals(DateHelper.convertStringToDate(dateStr).toString(), dateExpected.toString());

        String wrongDateStr = "2022-05/03 08:05:16";
        Date wrongDateExpected = new Date();
        ctx.assertEquals(DateHelper.convertStringToDate(wrongDateStr).toString(), wrongDateExpected.toString());
    }

    @Test
    public void setTimeZone_Should_return_Correct_Value(TestContext ctx) {
        String expectedTimeZone = TimeZone.getDefault().getID();

        ctx.assertEquals(DateHelper.setTimeZone("CEST").getID(), "Europe/Paris");

        ctx.assertEquals(DateHelper.setTimeZone("EEST").getID(), "Europe/Bucharest");

        ctx.assertEquals(DateHelper.setTimeZone("EST").getID(), "America/New_York");

        ctx.assertEquals(DateHelper.setTimeZone("CST").getID(), "America/Chicago");

        ctx.assertEquals(DateHelper.setTimeZone("randomParam").getID(), expectedTimeZone);

    }

    @Test
    public void convertDateFormat_Should_Have_Correct_Format(TestContext ctx) {
        String dateStr = "2022-05-03T08:05:16.307";
        String dateExpected = "03/05/2022 10:05";
        Date date = DateHelper.convertStringToDate(dateStr);
        ctx.assertEquals(DateHelper.convertDateFormat(date), dateExpected);

        String wrongDateStr = "2022-05-03 08:05:16.";
        Date wrongDate = DateHelper.convertStringToDate(wrongDateStr);
        String dateTime = DateTime.now().toDateTime(DateTimeZone.forTimeZone(DateHelper.setTimeZone(Field.CEST))).toString(DATE_FORMAT);
        ctx.assertEquals(DateHelper.convertDateFormat(wrongDate), dateTime);
    }
}
