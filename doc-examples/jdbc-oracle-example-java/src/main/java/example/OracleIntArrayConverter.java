package example;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.jdbc.convert.JdbcConversionContext;
import io.micronaut.data.model.runtime.convert.TypeConverter;
import io.micronaut.data.runtime.convert.DataConversionService;
import jakarta.inject.Singleton;
import oracle.jdbc.OracleConnection;

import java.sql.Array;
import java.sql.SQLException;

@Singleton
public class OracleIntArrayConverter implements TypeConverter<int[], Array> {

    private final DataConversionService<?> conversionService;

    public OracleIntArrayConverter(DataConversionService<?> conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Array convertToPersistedValue(int[] entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        if (context instanceof JdbcConversionContext) {
            try {
                JdbcConversionContext jdbcConversionContext = (JdbcConversionContext) context;
                OracleConnection oracleConnection = jdbcConversionContext.getConnection().unwrap(OracleConnection.class);
                Array num_varray = oracleConnection.createOracleArray("NUM_VARRAY", entityValue);
                return num_varray;
            } catch (SQLException e) {
                throw new DataAccessException("Failed to create an array: " + e.getMessage(), e);
            }
        }
        throw new IllegalStateException("Unsupported context type: " + context.getClass().getName());
    }

    @Override
    public int[] convertToEntityValue(Array persistedValue, ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        try {
            Class<int[]> type = int[].class;
            Object array = persistedValue.getArray();
            return conversionService.convert(array, type, context)
                    .orElseThrow(() -> new DataAccessException("Cannot convert type [" + array.getClass() + "] to target type: " + type + ". Considering defining a TypeConverter bean to handle this case."));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create an array: " + e.getMessage(), e);
        }
    }

}
