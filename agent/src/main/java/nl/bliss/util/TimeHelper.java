package nl.bliss.util;

import com.joestelmach.natty.DateGroup;
import com.joestelmach.natty.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static java.time.temporal.ChronoUnit.*;

/**
 * This class helps with temporal expression construction
 * TODO: Make this language independent with language resource files
 */
public class TimeHelper {

    public static Parser parser = new Parser();
    public static Logger logger = LoggerFactory.getLogger(TimeHelper.class.getName());
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-z");

    /**
     * This class extends in English the names printed by the Time4J library, since the library only provides a single
     * pretty print
     * @param date, the date to generate the prettified name for
     * @return a list of possible prettified name
     */
    public static ArrayList<String> getAlternativePrettyTimeString(ZonedDateTime date){
        ArrayList<String> timeOptions = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now();
        if(YEARS.between(date,now) > 1){
            timeOptions.add("over the last years");
            timeOptions.add("during the last couple of years");
            timeOptions.add("some years ago");
            timeOptions.add("in " + date.getYear());
            timeOptions.add(YEARS.between(date,now) + " years ago");
        }
        if(date.getYear() == now.getYear()-1){
            timeOptions.add("last year");
            timeOptions.add("the previous year");
            timeOptions.add("in " + date.getYear());
        }
        if(MONTHS.between(date,now) > 1){
            timeOptions.add("some months ago");
            timeOptions.add("during the last couple of months");
            timeOptions.add("in " + date.getMonth().toString());
            timeOptions.add(MONTHS.between(date, now) + " months ago");
        }
        if(date.getMonth().equals(now.getMonth().minus(1))){
            timeOptions.add("last month");
            timeOptions.add("the previous month");
            timeOptions.add("in " + date.getMonth().toString());
        }
        if(WEEKS.between(date,now) > 1){
            timeOptions.add("some weeks ago");
            timeOptions.add("during the last couple of weeks");
            timeOptions.add(WEEKS.between(date, now) + " weeks ago");
        }
        ZonedDateTime startLastWeek = now.minusDays(7+now.getDayOfWeek().getValue()-1);
        ZonedDateTime endLastWeek = now.minusDays(now.getDayOfWeek().getValue());
        if(date.isAfter(startLastWeek) && date.isBefore(endLastWeek)){
            timeOptions.add("last week");
            timeOptions.add("the previous week");
            timeOptions.add("earlier");
        }
        if(DAYS.between(date,now) > 2){
            timeOptions.add("some days ago");
            timeOptions.add("a few days ago");
            timeOptions.add(DAYS.between(date,now) + " days ago");
            timeOptions.add("earlier");
        }
        if(now.toLocalDate().minusDays(2).equals(date.toLocalDate())) {
            DayOfWeek dayOfWeek = now.minusDays(2).getDayOfWeek();
            String day = dayOfWeek.toString().toLowerCase();
            timeOptions.add("last " + day);
            timeOptions.add("the previous " + day);
            timeOptions.add("the day before yesterday");
            timeOptions.add("recently");
            timeOptions.add("earlier");
        }
        if(now.toLocalDate().minusDays(1).equals(date.toLocalDate())){
            timeOptions.add("recently");
            timeOptions.add("earlier");
        }
        ZonedDateTime beginLastNight = now.minusDays(1).withHour(17);
        ZonedDateTime endLastNight = now.withHour(0);
        if(date.isAfter(beginLastNight) && date.isBefore(endLastNight)) {
            timeOptions.add("last night");
            timeOptions.add("recently");
            timeOptions.add("earlier");
        }
        if(date.toLocalDate().equals(now.toLocalDate()) && date.isBefore(now.minusHours(2))){
            timeOptions.add("recently");
            timeOptions.add("today");
            timeOptions.add("just today");
        }
        if(date.isAfter(now.plusMinutes(1)) && date.isBefore(now.plusHours(6))){
            timeOptions.add("today");
            timeOptions.add("soon");
            timeOptions.add("later");
        }
        if(date.isAfter(now.withHour(11)) && date.isBefore(now.withHour(17))){
            timeOptions.add("this afternoon");
            timeOptions.add("soon");
            timeOptions.add("later");
        }
        if(date.isAfter(now.withHour(17)) && date.isBefore(now.plusDays(1).withHour(0))){
            timeOptions.add("this night");
            timeOptions.add("tonight");
            timeOptions.add("later");
        }
        if(date.toLocalDate().equals(now.toLocalDate().plusDays(1))){
            timeOptions.add("next day");
        }
        if(date.toLocalDate().equals(now.toLocalDate().plusDays(2))){
            DayOfWeek dayOfWeek = now.plusDays(2).getDayOfWeek();
            String day = dayOfWeek.toString().toLowerCase();
            timeOptions.add("upcoming " + day);
            timeOptions.add("this " + day);
            timeOptions.add("day after tomorrow");
        }
        if(date.isAfter(now.plusDays(3).withHour(0)) && date.getDayOfWeek().getValue() > now.getDayOfWeek().getValue()){
            timeOptions.add("in a few days");
            timeOptions.add("in a couple of days");
            timeOptions.add("in the next days");
            timeOptions.add("this week");
            timeOptions.add("in " + DAYS.between(date,now) + " days");
        }
        ZonedDateTime startNextWeek = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        ZonedDateTime endNextWeek = now.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        if(date.isAfter(now.plusDays(3).withHour(0)) && date.isAfter(startNextWeek) && date.isBefore(endNextWeek)){
            timeOptions.add("next week");
            timeOptions.add("somewhere next week");
        }
        if(date.isAfter(endNextWeek) && date.isBefore(now.with(TemporalAdjusters.lastDayOfMonth()))){
            timeOptions.add("in the next few weeks");
            timeOptions.add("this month");
            timeOptions.add("in " + WEEKS.between(date,now) + " weeks");
        }
        if(date.isAfter(now.with(TemporalAdjusters.lastDayOfMonth())) && date.isBefore(now.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth()))){
            timeOptions.add("next month");
            timeOptions.add("in a month");
            timeOptions.add("in " + date.getMonth().toString());
        }
        if(date.isAfter(now.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())) && date.isBefore(now.with(TemporalAdjusters.firstDayOfNextYear()))){
            timeOptions.add("in a few months");
            timeOptions.add("in " + date.getMonth().toString());
            timeOptions.add("this year");
        }
        if(date.isAfter(now.with(TemporalAdjusters.lastDayOfYear())) && date.isBefore(now.plusYears(1).with(TemporalAdjusters.lastDayOfYear()))){
            timeOptions.add("in " + date.getYear());
            timeOptions.add("next year");
            timeOptions.add("upcoming year");
        }
        if(date.isAfter(now.plusYears(1).with(TemporalAdjusters.lastDayOfYear()))){
            timeOptions.add("in the next few years");
            timeOptions.add("in " + date.getYear());
        }
        return timeOptions;
    }

    /**
     * Method for extracting the exact time in English form a string expression.
     * @param expression, such as 'yesterday' or 'two hourse ago'.
     * @return null if no time could be established
     */
    public static ZonedDateTime getZonedDateTime(String expression){
        try{
            List<DateGroup> dateGroups = parser.parse(expression);
            if(!dateGroups.isEmpty() && !dateGroups.get(0).getDates().isEmpty()){
                Date date = dateGroups.get(0).getDates().get(0);
                return ZonedDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
            }
        }
        catch(Exception e){
            logger.warn("Unparsable time: {}", expression);
            e.printStackTrace();
        }
        return null;
    }
}
