package io.micronaut.data.cql.operations;

import com.datastax.oss.driver.api.core.cql.BoundStatementBuilder;
import com.datastax.oss.driver.api.core.data.CqlDuration;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.runtime.mapper.QueryStatement;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

public class CqlQueryStatement implements QueryStatement<BoundStatementBuilder, Integer>, CqlQueryStatment<BoundStatementBuilder, Integer> {

    public CqlQueryStatement() {

    }

    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setValue(BoundStatementBuilder statement, Integer index, Object value) throws DataAccessException {
        if (value instanceof Boolean) {
            return this.setBoolean(statement, index, (Boolean) value);
        } else if (value instanceof Byte) {
            return this.setByte(statement, index, (Byte) value);
        } else if (value instanceof Double) {
            return this.setDouble(statement, index, (Double) value);
        } else if (value instanceof Float) {
            return this.setFloat(statement, index, (Float) value);
        } else if (value instanceof Integer) {
            return this.setInt(statement, index, (Integer) value);
        } else if (value instanceof Long) {
            return this.setLong(statement, index, (Long) value);
        } else if (value instanceof Short) {
            return this.setShort(statement, index, (Short) value);
        } else if (value instanceof byte[]) {
            return this.setBytes(statement, index, (byte[]) value);
        } else if (value instanceof String) {
            return this.setString(statement, index, (String) value);
        } else if (value instanceof Instant) {
            statement.set(index, (Instant) value, GenericType.INSTANT);
            return this;
        } else if (value instanceof ZonedDateTime) {
            statement.set(index, (ZonedDateTime) value, GenericType.ZONED_DATE_TIME);
            return this;
        } else if (value instanceof LocalDate) {
            statement.set(index, (LocalDate) value, GenericType.LOCAL_DATE);
            return this;
        } else if (value instanceof LocalTime) {
            statement.set(index, (LocalTime) value, GenericType.LOCAL_TIME);
            return this;
        } else if (value instanceof ByteBuffer) {
            statement.set(index, (ByteBuffer) value, GenericType.BYTE_BUFFER);
            return this;
        } else if (value instanceof BigInteger) {
            statement.set(index, (BigInteger) value, GenericType.BIG_INTEGER);
            return this;
        } else if (value instanceof BigDecimal) {
            statement.set(index, (BigDecimal) value, GenericType.BIG_DECIMAL);
            return this;
        } else if (value instanceof UUID) {
            statement.set(index, (UUID) value, GenericType.UUID);
            return this;
        } else if (value instanceof InetAddress) {
            statement.set(index, (InetAddress) value, GenericType.INET_ADDRESS);
            return this;
        } else if (value instanceof CqlDuration) {
            statement.set(index, (CqlDuration) value, GenericType.CQL_DURATION);
            return this;
        } else if (value instanceof Duration) {
            long nanos = ((Duration) value).get(ChronoUnit.NANOS);
            int months = (int) (ChronoUnit.MONTHS.getDuration().getNano() / nanos);
            nanos -= months * ChronoUnit.MONTHS.getDuration().getNano();
            int days = (int) (ChronoUnit.DAYS.getDuration().getNano() / nanos);
            nanos -= days * ChronoUnit.DAYS.getDuration().getNano();
            statement.set(index, CqlDuration.newInstance(months, days, nanos), GenericType.CQL_DURATION);
        } else if (value instanceof TupleValue) {
            statement.set(index, (TupleValue) value, GenericType.TUPLE_VALUE);
            return this;
        } else if (value instanceof UdtValue) {
            statement.set(index, (UdtValue) value, GenericType.UDT_VALUE);
            return this;
        }
        throw new DataAccessException("Unknown data type: " + value.getClass());
    }

    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setDynamic(@NonNull BoundStatementBuilder statement, @NonNull Integer index, @NonNull DataType dataType, Object value) {
        if(value == null){
            statement.setToNull(index);
            return this;
        } else {
            return CqlQueryStatment.super.setDynamic(statement, index, dataType, value);
        }
    }

    @Nullable
    @Override
    public <T> T convertRequired(@Nullable Object value, Class<T> type) {
        return null;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> SetUUID(BoundStatementBuilder statement, Integer name, UUID value) {
        statement.setUuid(name,value);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setLong(BoundStatementBuilder statement, Integer name, long value) {
        statement.setLong(name, value);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setChar(BoundStatementBuilder statement, Integer name, char value) {
        statement.setByte(name, (byte) value);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setDate(BoundStatementBuilder statement, Integer name, Date date) {
        statement.setLocalDate(name,LocalDate.ofInstant(date.toInstant(),ZoneId.systemDefault()));
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setTimestamp(BoundStatementBuilder statement, Integer name, Date date) {
        statement.set(name, ZonedDateTime.ofInstant(date.toInstant(),ZoneId.systemDefault()), ZonedDateTime.class);
        return this;
    }

    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setString(BoundStatementBuilder statement, Integer name, String string) {
        statement.setString(name,string);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setInt(BoundStatementBuilder statement, Integer name, int integer) {
        statement.setInt(name,integer);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setBoolean(BoundStatementBuilder statement, Integer name, boolean bool) {
        statement.setBoolean(name,bool);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setFloat(BoundStatementBuilder statement, Integer name, float f) {
        statement.setFloat(name,f);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setByte(BoundStatementBuilder statement, Integer name, byte b) {
        statement.setByte(name,b);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setShort(BoundStatementBuilder statement, Integer name, short s) {
        statement.setShort(name,s);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setDouble(BoundStatementBuilder statement, Integer name, double d) {
        statement.setDouble(name,name);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setBigDecimal(BoundStatementBuilder statement, Integer name, BigDecimal bd) {
        statement.setBigDecimal(name,bd);
        return this;
    }

    @NonNull
    @Override
    public QueryStatement<BoundStatementBuilder, Integer> setBytes(BoundStatementBuilder statement, Integer name, byte[] bytes) {
         statement.setByteBuffer(name, ByteBuffer.wrap(bytes));
         return this;
    }
}
