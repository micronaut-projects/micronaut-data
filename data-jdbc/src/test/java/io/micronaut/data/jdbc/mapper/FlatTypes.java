/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.jdbc.mapper;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Version;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

/**
 * A flat entity with a property of every type the flat entity mapper reads with a typed getter.
 */
@MappedEntity("flat_types")
public class FlatTypes {

    @Id
    @GeneratedValue
    private Long id;
    @Version
    private Long version;
    private int primitiveInteger;
    private long primitiveLong;
    private boolean primitiveBoolean;
    private short primitiveShort;
    private double primitiveDouble;
    private float primitiveFloat;
    private byte primitiveByte;
    private String string;
    @Nullable
    private Integer wrapperInteger;
    @Nullable
    private Long wrapperLong;
    @Nullable
    private Boolean wrapperBoolean;
    @Nullable
    private Short wrapperShort;
    @Nullable
    private Double wrapperDouble;
    @Nullable
    private Float wrapperFloat;
    @Nullable
    private Byte wrapperByte;
    @Nullable
    private BigDecimal bigDecimal;
    @Nullable
    private BigInteger bigInteger;
    @Nullable
    private byte[] byteArray;
    @Nullable
    private Date date;
    @Nullable
    private LocalDateTime localDateTime;
    @Nullable
    private ZonedDateTime zonedDateTime;
    @Nullable
    private OffsetDateTime offsetDateTime;
    @Nullable
    private LocalDate localDate;
    @Nullable
    private LocalTime localTime;
    @Nullable
    private Instant instant;
    @Nullable
    private UUID uuid;
    @Nullable
    @MappedProperty("renamed_column")
    private String renamed;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public int getPrimitiveInteger() {
        return primitiveInteger;
    }

    public void setPrimitiveInteger(int primitiveInteger) {
        this.primitiveInteger = primitiveInteger;
    }

    public long getPrimitiveLong() {
        return primitiveLong;
    }

    public void setPrimitiveLong(long primitiveLong) {
        this.primitiveLong = primitiveLong;
    }

    public boolean isPrimitiveBoolean() {
        return primitiveBoolean;
    }

    public void setPrimitiveBoolean(boolean primitiveBoolean) {
        this.primitiveBoolean = primitiveBoolean;
    }

    public short getPrimitiveShort() {
        return primitiveShort;
    }

    public void setPrimitiveShort(short primitiveShort) {
        this.primitiveShort = primitiveShort;
    }

    public double getPrimitiveDouble() {
        return primitiveDouble;
    }

    public void setPrimitiveDouble(double primitiveDouble) {
        this.primitiveDouble = primitiveDouble;
    }

    public float getPrimitiveFloat() {
        return primitiveFloat;
    }

    public void setPrimitiveFloat(float primitiveFloat) {
        this.primitiveFloat = primitiveFloat;
    }

    public byte getPrimitiveByte() {
        return primitiveByte;
    }

    public void setPrimitiveByte(byte primitiveByte) {
        this.primitiveByte = primitiveByte;
    }

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }

    @Nullable
    public Integer getWrapperInteger() {
        return wrapperInteger;
    }

    public void setWrapperInteger(@Nullable Integer wrapperInteger) {
        this.wrapperInteger = wrapperInteger;
    }

    @Nullable
    public Long getWrapperLong() {
        return wrapperLong;
    }

    public void setWrapperLong(@Nullable Long wrapperLong) {
        this.wrapperLong = wrapperLong;
    }

    @Nullable
    public Boolean getWrapperBoolean() {
        return wrapperBoolean;
    }

    public void setWrapperBoolean(@Nullable Boolean wrapperBoolean) {
        this.wrapperBoolean = wrapperBoolean;
    }

    @Nullable
    public Short getWrapperShort() {
        return wrapperShort;
    }

    public void setWrapperShort(@Nullable Short wrapperShort) {
        this.wrapperShort = wrapperShort;
    }

    @Nullable
    public Double getWrapperDouble() {
        return wrapperDouble;
    }

    public void setWrapperDouble(@Nullable Double wrapperDouble) {
        this.wrapperDouble = wrapperDouble;
    }

    @Nullable
    public Float getWrapperFloat() {
        return wrapperFloat;
    }

    public void setWrapperFloat(@Nullable Float wrapperFloat) {
        this.wrapperFloat = wrapperFloat;
    }

    @Nullable
    public Byte getWrapperByte() {
        return wrapperByte;
    }

    public void setWrapperByte(@Nullable Byte wrapperByte) {
        this.wrapperByte = wrapperByte;
    }

    @Nullable
    public BigDecimal getBigDecimal() {
        return bigDecimal;
    }

    public void setBigDecimal(@Nullable BigDecimal bigDecimal) {
        this.bigDecimal = bigDecimal;
    }

    @Nullable
    public BigInteger getBigInteger() {
        return bigInteger;
    }

    public void setBigInteger(@Nullable BigInteger bigInteger) {
        this.bigInteger = bigInteger;
    }

    @Nullable
    public byte[] getByteArray() {
        return byteArray;
    }

    public void setByteArray(@Nullable byte[] byteArray) {
        this.byteArray = byteArray;
    }

    @Nullable
    public Date getDate() {
        return date;
    }

    public void setDate(@Nullable Date date) {
        this.date = date;
    }

    @Nullable
    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(@Nullable LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    @Nullable
    public ZonedDateTime getZonedDateTime() {
        return zonedDateTime;
    }

    public void setZonedDateTime(@Nullable ZonedDateTime zonedDateTime) {
        this.zonedDateTime = zonedDateTime;
    }

    @Nullable
    public OffsetDateTime getOffsetDateTime() {
        return offsetDateTime;
    }

    public void setOffsetDateTime(@Nullable OffsetDateTime offsetDateTime) {
        this.offsetDateTime = offsetDateTime;
    }

    @Nullable
    public LocalDate getLocalDate() {
        return localDate;
    }

    public void setLocalDate(@Nullable LocalDate localDate) {
        this.localDate = localDate;
    }

    @Nullable
    public LocalTime getLocalTime() {
        return localTime;
    }

    public void setLocalTime(@Nullable LocalTime localTime) {
        this.localTime = localTime;
    }

    @Nullable
    public Instant getInstant() {
        return instant;
    }

    public void setInstant(@Nullable Instant instant) {
        this.instant = instant;
    }

    @Nullable
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(@Nullable UUID uuid) {
        this.uuid = uuid;
    }

    @Nullable
    public String getRenamed() {
        return renamed;
    }

    public void setRenamed(@Nullable String renamed) {
        this.renamed = renamed;
    }
}
