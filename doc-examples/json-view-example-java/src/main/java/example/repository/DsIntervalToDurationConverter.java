package example.repository;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import oracle.jdbc.driver.json.binary.OsonPrimitiveConversions;
import oracle.sql.INTERVALDS;

import java.time.Duration;


@Singleton
public class DsIntervalToDurationConverter implements AttributeConverter<Duration, INTERVALDS> {

    @Override
    public INTERVALDS convertToPersistedValue(Duration entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        byte[] bytes = OsonPrimitiveConversions.durationToIntervalDS(entityValue);
        INTERVALDS result = new INTERVALDS();
        result.setBytes(bytes);
        return result;
    }

    @Override
    public Duration convertToEntityValue(INTERVALDS persistedValue, ConversionContext context) {
        if (persistedValue == null || persistedValue.isNull()) {
            return null;
        }
        byte[] bytes = persistedValue.toBytes();
        Duration duration = OsonPrimitiveConversions.intervalDSToDuration(bytes);
        return duration;
    }
}
