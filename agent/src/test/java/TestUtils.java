import nl.bliss.util.TimeHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import java.time.ZonedDateTime;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;

public class TestUtils {

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    @Test
    public void testZonedDateTime(){
        String time1 = "next Monday or Tuesday";
        ZonedDateTime date1 = TimeHelper.getZonedDateTime(time1);

        String time2 = "two hours ago";
        ZonedDateTime date2 = TimeHelper.getZonedDateTime(time2);

        String time3 = "while singing a song";
        ZonedDateTime date3 = TimeHelper.getZonedDateTime(time3);

        String time4 = "for seventy years";
        ZonedDateTime date4 = TimeHelper.getZonedDateTime(time4);

        collector.checkThat(date1.format(TimeHelper.formatter),containsString("CET"));
        collector.checkThat(date2.format(TimeHelper.formatter),containsString("CET"));
        collector.checkThat(date3,equalTo(null));
        collector.checkThat(date4,equalTo(null));

    }
}
