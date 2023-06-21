package example;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.MonthDay;

@Converter
public class MonthDayDateAttributeConverter implements AttributeConverter<MonthDay, Timestamp> {
    @Override
    public Timestamp convertToDatabaseColumn(MonthDay monthDay) {
        if (monthDay == null) {
            return null;
        }
        return Timestamp.valueOf(monthDay.atYear(2000).atStartOfDay());
    }

    @Override
    public MonthDay convertToEntityAttribute(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime localDate = timestamp.toLocalDateTime();
        return MonthDay.of(
                localDate.getMonth(),
                localDate.getDayOfMonth()
        );
    }
}
