package example.repository;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import oracle.jdbc.driver.json.binary.OsonPrimitiveConversions;
import oracle.sql.INTERVALYM;

import java.time.Period;

@Singleton
public class YmIntervalToPeriodConverter implements AttributeConverter<Period, INTERVALYM> {

    @Override
    public INTERVALYM convertToPersistedValue(Period entityValue, ConversionContext context) {
        return null;
    }

    @Override
    public Period convertToEntityValue(INTERVALYM persistedValue, ConversionContext context) {
        if (persistedValue == null || persistedValue.isNull()) {
            return null;
        }
        byte[] bytes = persistedValue.toBytes();
        Period period = OsonPrimitiveConversions.intervalYMToPeriod(bytes);
        return period;
    }
}
