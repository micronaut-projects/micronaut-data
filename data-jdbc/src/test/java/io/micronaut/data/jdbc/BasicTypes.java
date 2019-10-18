/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.jdbc;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

@Entity
@Table(name = "basic_types")
public class BasicTypes {
    @Id
    @GeneratedValue
    private Long myId;

    private int primitiveInteger = 1;
    private long primitiveLong = 2;
    private boolean primitiveBoolean = true;
    private char primitiveChar = 'c';
    private short primitiveShort = 3;
    private double primitiveDouble = 1.1;
    private float primitiveFloat = 1.2f;
    private byte primitiveByte = 4;
    private String string = "test";
    private CharSequence charSequence = "test2";
    private Integer wrapperInteger = 1;
    private Long wrapperLong = 2L;
    private Boolean wrapperBoolean = true;
    private Character wrapperChar = 'c';
    private Short wrapperShort = 3;
    private Double wrapperDouble = 1.1d;
    private Float wrapperFloat = 1.2f;
    private Byte wrapperByte = 4;
    private URL url = new URL("https://test.com");
    private URI uri = URI.create("https://test.com");
    private byte[] byteArray = new byte[] { 1, 2, 3};
    private Date date = new Date();
    private LocalDateTime localDateTime = LocalDateTime.now();
    private Instant instant = Instant.now();
    private UUID uuid = UUID.randomUUID();
    @Column(columnDefinition = "DECIMAL(24) NOT NULL")
    private BigDecimal bigDecimal = new BigDecimal(Long.MAX_VALUE + "000");
    private TimeZone timeZone = TimeZone.getTimeZone("GMT");
    private Charset charset = StandardCharsets.UTF_8;

    public BasicTypes() throws MalformedURLException {
    }

    public Long getMyId() {
        return myId;
    }

    public void setMyId(Long myId) {
        this.myId = myId;
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

    public char getPrimitiveChar() {
        return primitiveChar;
    }

    public void setPrimitiveChar(char primitiveChar) {
        this.primitiveChar = primitiveChar;
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

    public CharSequence getCharSequence() {
        return charSequence;
    }

    public void setCharSequence(CharSequence charSequence) {
        this.charSequence = charSequence;
    }

    public Integer getWrapperInteger() {
        return wrapperInteger;
    }

    public void setWrapperInteger(Integer wrapperInteger) {
        this.wrapperInteger = wrapperInteger;
    }

    public Long getWrapperLong() {
        return wrapperLong;
    }

    public void setWrapperLong(Long wrapperLong) {
        this.wrapperLong = wrapperLong;
    }

    public Boolean getWrapperBoolean() {
        return wrapperBoolean;
    }

    public void setWrapperBoolean(Boolean wrapperBoolean) {
        this.wrapperBoolean = wrapperBoolean;
    }

    public Character getWrapperChar() {
        return wrapperChar;
    }

    public void setWrapperChar(Character wrapperChar) {
        this.wrapperChar = wrapperChar;
    }

    public Short getWrapperShort() {
        return wrapperShort;
    }

    public void setWrapperShort(Short wrapperShort) {
        this.wrapperShort = wrapperShort;
    }

    public Double getWrapperDouble() {
        return wrapperDouble;
    }

    public void setWrapperDouble(Double wrapperDouble) {
        this.wrapperDouble = wrapperDouble;
    }

    public Float getWrapperFloat() {
        return wrapperFloat;
    }

    public void setWrapperFloat(Float wrapperFloat) {
        this.wrapperFloat = wrapperFloat;
    }

    public Byte getWrapperByte() {
        return wrapperByte;
    }

    public void setWrapperByte(Byte wrapperByte) {
        this.wrapperByte = wrapperByte;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public byte[] getByteArray() {
        return byteArray;
    }

    public void setByteArray(byte[] byteArray) {
        this.byteArray = byteArray;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    public Instant getInstant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public BigDecimal getBigDecimal() {
        return bigDecimal;
    }

    public void setBigDecimal(BigDecimal bigDecimal) {
        this.bigDecimal = bigDecimal;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
