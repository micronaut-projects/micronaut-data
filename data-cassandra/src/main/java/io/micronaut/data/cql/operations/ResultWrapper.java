package io.micronaut.data.cql.operations;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.ProtocolVersion;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.data.CqlDuration;
import com.datastax.oss.driver.api.core.data.GettableById;
import com.datastax.oss.driver.api.core.data.GettableByIndex;
import com.datastax.oss.driver.api.core.data.GettableByName;
import com.datastax.oss.driver.api.core.data.TupleValue;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.detach.AttachmentPoint;
import com.datastax.oss.driver.api.core.detach.Detachable;
import com.datastax.oss.driver.api.core.metadata.token.Token;
import com.datastax.oss.driver.api.core.type.DataType;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

public final class ResultWrapper implements GettableByName, GettableByIndex {
    private final Iterator<Row> results;
    private Row current = null;
    private ResultSet resultSet;
    public ResultWrapper(ResultSet results) {
        this.resultSet = results;
        this.results = results.iterator();
        if (this.results.hasNext()) {
            current = this.results.next();
        }
    }

    public static ResultWrapper of(ResultSet resultSet) {
        return new ResultWrapper(resultSet);
    }

    public boolean hasNext(){
       return this.results.hasNext();
    }

    public boolean next() {
        if (hasNext()) {
            current = this.results.next();
            return true;
        }
        return false;
    }

    @Override
    public int firstIndexOf(@NonNull String s) {
        if(current == null) throw new NoSuchElementException();
        return current.firstIndexOf(s);
    }

    @NonNull
    @Override
    public DataType getType(@NonNull String s) {
        if(current == null) throw new NoSuchElementException();
        return current.getType(s);
    }

    @Nullable
    @Override
    public ByteBuffer getBytesUnsafe(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getBytesUnsafe(i);
    }

    @Override
    public boolean isNull(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.isNull(i);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(int i, TypeCodec<ValueT> codec) {
        if(current == null) throw new NoSuchElementException();
        return current.get(i,codec);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(int i, GenericType<ValueT> targetType) {
        if(current == null) throw new NoSuchElementException();
        return current.get(i,targetType);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(int i, Class<ValueT> targetClass) {
        if(current == null) throw new NoSuchElementException();
        return current.get(i,targetClass);
    }

    @Nullable
    @Override
    public Object getObject(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getObject(i);
    }

    @Override
    public boolean getBoolean(int i) {
        if(current == null) throw new NoSuchElementException();

        return current.getBoolean(i);
    }

    @Override
    public byte getByte(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getByte(i);
    }

    @Override
    public double getDouble(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getDouble(i);
    }

    @Override
    public float getFloat(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getFloat(i);
    }

    @Override
    public int getInt(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getInt(i);
    }

    @Override
    public long getLong(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getLong(i);
    }

    @Override
    public short getShort(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getShort(i);
    }

    @Nullable
    @Override
    public Instant getInstant(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getInstant(i);
    }

    @Nullable
    @Override
    public LocalDate getLocalDate(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getLocalDate(i);
    }

    @Nullable
    @Override
    public LocalTime getLocalTime(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getLocalTime(i);
    }

    @Nullable
    @Override
    public ByteBuffer getByteBuffer(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getByteBuffer(i);
    }

    @Nullable
    @Override
    public String getString(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getString(i);
    }

    @Nullable
    @Override
    public BigInteger getBigInteger(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getBigInteger(i);
    }

    @Nullable
    @Override
    public BigDecimal getBigDecimal(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getBigDecimal(i);
    }

    @Nullable
    @Override
    public UUID getUuid(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getUuid(i);
    }

    @Nullable
    @Override
    public InetAddress getInetAddress(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getInetAddress(i);
    }

    @Nullable
    @Override
    public CqlDuration getCqlDuration(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getCqlDuration(i);
    }

    @Nullable
    @Override
    public Token getToken(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getToken(i);
    }

    @Nullable
    @Override
    public <ElementT> List<ElementT> getList(int i, @NonNull Class<ElementT> elementsClass) {
        if(current == null) throw new NoSuchElementException();
        return current.getList(i,elementsClass);
    }

    @Nullable
    @Override
    public <ElementT> Set<ElementT> getSet(int i, @NonNull Class<ElementT> elementsClass) {
        if(current == null) throw new NoSuchElementException();
        return current.getSet(i,elementsClass);
    }

    @Nullable
    @Override
    public <KeyT, ValueT> Map<KeyT, ValueT> getMap(int i, @NonNull Class<KeyT> keyClass, @NonNull Class<ValueT> valueClass) {
        if(current == null) throw new NoSuchElementException();
        return current.getMap(i,keyClass,valueClass);
    }

    @Nullable
    @Override
    public UdtValue getUdtValue(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getUdtValue(i);
    }

    @Nullable
    @Override
    public TupleValue getTupleValue(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getTupleValue(i);
    }

    @Override
    public int size() {
        if(current == null) throw new NoSuchElementException();
        return current.size();
    }

    @NonNull
    @Override
    public DataType getType(int i) {
        if(current == null) throw new NoSuchElementException();
        return current.getType(i);
    }

    @NonNull
    @Override
    public CodecRegistry codecRegistry() {
        if(current == null) throw new NoSuchElementException();
        return current.codecRegistry();
    }

    @NonNull
    @Override
    public ProtocolVersion protocolVersion() {
        if(current == null) throw new NoSuchElementException();
        return current.protocolVersion();
    }

    @Nullable
    @Override
    public ByteBuffer getBytesUnsafe(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getBytesUnsafe(name);
    }

    @Override
    public boolean isNull(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.isNull(name);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(@NonNull String name, @NonNull TypeCodec<ValueT> codec) {
        if(current == null) throw new NoSuchElementException();
        return current.get(name,codec);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(@NonNull String name, @NonNull GenericType<ValueT> targetType) {
        if(current == null) throw new NoSuchElementException();
        return current.get(name,targetType);
    }

    @Nullable
    @Override
    public <ValueT> ValueT get(@NonNull String name, @NonNull Class<ValueT> targetClass) {
        if(current == null) throw new NoSuchElementException();
        return current.get(name,targetClass);
    }

    @Nullable
    @Override
    public Object getObject(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getObject(name);
    }

    @Override
    public boolean getBoolean(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getBoolean(name);
    }

    @Override
    public byte getByte(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getByte(name);
    }

    @Override
    public double getDouble(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getDouble(name);
    }

    @Override
    public float getFloat(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getFloat(name);
    }

    @Override
    public int getInt(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getInt(name);
    }

    @Override
    public long getLong(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getLong(name);
    }

    @Override
    public short getShort(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getShort(name);
    }

    @Nullable
    @Override
    public Instant getInstant(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getInstant(name);
    }

    @Nullable
    @Override
    public LocalDate getLocalDate(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getLocalDate(name);
    }

    @Nullable
    @Override
    public LocalTime getLocalTime(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getLocalTime(name);
    }

    @Nullable
    @Override
    public ByteBuffer getByteBuffer(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getByteBuffer(name);
    }

    @Nullable
    @Override
    public String getString(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getString(name);
    }

    @Nullable
    @Override
    public BigInteger getBigInteger(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getBigInteger(name);
    }

    @Nullable
    @Override
    public BigDecimal getBigDecimal(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getBigDecimal(name);
    }

    @Nullable
    @Override
    public UUID getUuid(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getUuid(name);
    }

    @Nullable
    @Override
    public InetAddress getInetAddress(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getInetAddress(name);
    }

    @Nullable
    @Override
    public CqlDuration getCqlDuration(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getCqlDuration(name);
    }

    @Nullable
    @Override
    public Token getToken(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getToken(name);
    }

    @Nullable
    @Override
    public <ElementT> List<ElementT> getList(@NonNull String name, @NonNull Class<ElementT> elementsClass) {
        if(current == null) throw new NoSuchElementException();
        return current.getList(name,elementsClass);
    }

    @Nullable
    @Override
    public <ElementT> Set<ElementT> getSet(@NonNull String name, @NonNull Class<ElementT> elementsClass) {
        if(current == null) throw new NoSuchElementException();
        return current.getSet(name,elementsClass);
    }

    @Nullable
    @Override
    public <KeyT, ValueT> Map<KeyT, ValueT> getMap(@NonNull String name, @NonNull Class<KeyT> keyClass, @NonNull Class<ValueT> valueClass) {
        if(current == null) throw new NoSuchElementException();
        return current.getMap(name,keyClass,valueClass);
    }

    @Nullable
    @Override
    public UdtValue getUdtValue(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getUdtValue(name);
    }

    @Nullable
    @Override
    public TupleValue getTupleValue(@NonNull String name) {
        if(current == null) throw new NoSuchElementException();
        return current.getTupleValue(name);
    }
}
