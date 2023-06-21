package example;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.time.MonthDay;
import java.sql.Date;

@Converter
public class MonthDayDateAttributeConverter implements AttributeConverter<MonthDay, Date> {
    @Override
    public Date convertToDatabaseColumn(MonthDay monthDay) {
        if (monthDay == null) {
            return null;
        }
        return Date.valueOf(monthDay.atYear(2000));
    }

    @Override
    public MonthDay convertToEntityAttribute(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date.toLocalDate();
        return MonthDay.of(
                localDate.getMonth(),
                localDate.getDayOfMonth()
        );
    }
}
