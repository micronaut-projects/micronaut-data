package io.micronaut.data.cql;

import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.data.CqlDuration;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.metadata.token.Token;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.TupleType;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.codec.registry.CodecRegistry;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BoundStatementWrapper implements TupleValue {
   private BoundStatement boundStatement = null;
    public BoundStatementWrapper(BoundStatement boundStatement) {
        this.boundStatement = boundStatement;
    }

    @Nullable
    @Override
    public ByteBuffer getBytesUnsafe(int i) {
        return boundStatement.getBytesUnsafe(i);
    }

    @Override
    public boolean isNull(int i) {
        return boundStatement.isNull(i);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(int i, TypeCodec<ValueT> codec) {
        return boundStatement.get(i,codec);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(int i, GenericType<ValueT> targetType) {
        return boundStatement.get(i,targetType);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(int i, Class<ValueT> targetClass) {
        return boundStatement.get(i,targetClass);
    }

    @Nullable
    @Override
    public Object getObject(int i) {
        return boundStatement.getObject(i);
    }

    @Override
    public boolean getBoolean(int i) {
        return boundStatement.getBoolean(i);
    }

    @Override
    public byte getByte(int i) {
        return boundStatement.getByte(i);
    }

    @Override
    public double getDouble(int i) {
        return boundStatement.getDouble(i);
    }

    @Override
    public float getFloat(int i) {
        return boundStatement.getFloat(i);
    }

    @Override
    public int getInt(int i) {
        return boundStatement.getInt(i);
    }

    @Override
    public long getLong(int i) {
        return boundStatement.getLong(i);
    }

    @Override
    public short getShort(int i) {
        return boundStatement.getShort(i);
    }

    @Nullable
    @Override
    public Instant getInstant(int i) {
        return boundStatement.getInstant(i);
    }

    @Nullable
    @Override
    public LocalDate getLocalDate(int i) {
        return boundStatement.getLocalDate(i);
    }

    @Nullable
    @Override
    public LocalTime getLocalTime(int i) {
        return null;
    }

    @Nullable
    @Override
    public ByteBuffer getByteBuffer(int i) {
        return null;
    }

    @Nullable
    @Override
    public String getString(int i) {
        return null;
    }

    @Nullable
    @Override
    public BigInteger getBigInteger(int i) {
        return null;
    }

    @Nullable
    @Override
    public BigDecimal getBigDecimal(int i) {
        return null;
    }

    @Nullable
    @Override
    public UUID getUuid(int i) {
        return null;
    }

    @Nullable
    @Override
    public InetAddress getInetAddress(int i) {
        return null;
    }

    @Nullable
    @Override
    public CqlDuration getCqlDuration(int i) {
        return null;
    }

    @Nullable
    @Override
    public Token getToken(int i) {
        return null;
    }

    @Nullable
    @Override
    public <ElementT> List<ElementT> getList(int i, @NonNull Class<ElementT> elementsClass) {
        return null;
    }

    @Nullable
    @Override
    public <ElementT> Set<ElementT> getSet(int i, @NonNull Class<ElementT> elementsClass) {
        return null;
    }

    @Nullable
    @Override
    public <KeyT, ValueT> Map<KeyT, ValueT> getMap(int i, @NonNull Class<KeyT> keyClass, @NonNull Class<ValueT> valueClass) {
        return null;
    }

    @Nullable
    @Override
    public UdtValue getUdtValue(int i) {
        return null;
    }

    @Nullable
    @Override
    public TupleValue getTupleValue(int i) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setBytesUnsafe(int i, @Nullable ByteBuffer byteBuffer) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setToNull(int i) {
        return null;
    }

    @NonNull
    @Override
    public <ValueT> TupleValue set(int i, @Nullable ValueT v, @NonNull TypeCodec<ValueT> codec) {
        return null;
    }

    @NonNull
    @Override
    public <ValueT> TupleValue set(int i, @Nullable ValueT v, @NonNull GenericType<ValueT> targetType) {
        return null;
    }

    @NonNull
    @Override
    public <ValueT> TupleValue set(int i, @Nullable ValueT v, @NonNull Class<ValueT> targetClass) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setBoolean(int i, boolean v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setByte(int i, byte v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setDouble(int i, double v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setFloat(int i, float v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setInt(int i, int v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setLong(int i, long v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setShort(int i, short v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setInstant(int i, @Nullable Instant v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setLocalDate(int i, @Nullable LocalDate v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setLocalTime(int i, @Nullable LocalTime v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setByteBuffer(int i, @Nullable ByteBuffer v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setString(int i, @Nullable String v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setBigInteger(int i, @Nullable BigInteger v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setBigDecimal(int i, @Nullable BigDecimal v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setUuid(int i, @Nullable UUID v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setInetAddress(int i, @Nullable InetAddress v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setCqlDuration(int i, @Nullable CqlDuration v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setToken(int i, @NonNull Token v) {
        return null;
    }

    @NonNull
    @Override
    public <ElementT> TupleValue setList(int i, @Nullable List<ElementT> v, @NonNull Class<ElementT> elementsClass) {
        return null;
    }

    @NonNull
    @Override
    public <ElementT> TupleValue setSet(int i, @Nullable Set<ElementT> v, @NonNull Class<ElementT> elementsClass) {
        return null;
    }

    @NonNull
    @Override
    public <KeyT, ValueT> TupleValue setMap(int i, @Nullable Map<KeyT, ValueT> v, @NonNull Class<KeyT> keyClass, @NonNull Class<ValueT> valueClass) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setUdtValue(int i, @Nullable UdtValue v) {
        return null;
    }

    @NonNull
    @Override
    public TupleValue setTupleValue(int i, @Nullable TupleValue v) {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @NonNull
    @Override
    public DataType getType(int i) {
        return null;
    }

    @NonNull
    @Override
    public CodecRegistry codecRegistry() {
        return null;
    }

    @NonNull
    @Override
    public ProtocolVersion protocolVersion() {
        return null;
    }

    @NonNull
    @Override
    public TupleType getType() {
        return null;
    }

    @NonNull
    @Override
    public String getFormattedContents() {
        return null;
    }
}
