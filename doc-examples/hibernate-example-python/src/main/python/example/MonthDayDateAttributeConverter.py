from datetime import date

from jakarta.persistence import AttributeConverter, Converter
from java.sql import Date
from java.time import MonthDay


@Converter
class MonthDayDateAttributeConverter(AttributeConverter[MonthDay, Date]):

    def convertToDatabaseColumn(self, month_day: MonthDay | None) -> Date | None:
        if month_day is None:
            return None
        return Date.valueOf(month_day.atYear(2000))

    def convertToEntityAttribute(self, value: Date | None) -> MonthDay | None:
        if value is None:
            return None
        local_date = value.toLocalDate()
        return MonthDay.of(local_date.getMonth(), local_date.getDayOfMonth())
