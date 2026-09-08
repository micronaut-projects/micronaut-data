package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;

import java.math.BigDecimal;

/**
 * One property per numeric width the filter builder narrows a bound literal onto, in both the
 * boxed and the primitive form, plus the two shapes that are not narrowed at all: a
 * {@link BigDecimal} the narrowing switch does not name, and a field reached by its persisted
 * name rather than its property name.
 */
@MappedEntity
public class NumericWidthsEntity {

    @Id
    private String id;

    private Integer boxedInt;
    private int primitiveInt;
    private Long boxedLong;
    private long primitiveLong;
    private Double boxedDouble;
    private double primitiveDouble;
    private Float boxedFloat;
    private float primitiveFloat;
    private Short boxedShort;
    private short primitiveShort;
    private Byte boxedByte;
    private byte primitiveByte;
    private BigDecimal amount;
    private String label;

    @MappedProperty("mapped_count")
    private Integer mappedCount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getBoxedInt() {
        return boxedInt;
    }

    public void setBoxedInt(Integer boxedInt) {
        this.boxedInt = boxedInt;
    }

    public int getPrimitiveInt() {
        return primitiveInt;
    }

    public void setPrimitiveInt(int primitiveInt) {
        this.primitiveInt = primitiveInt;
    }

    public Long getBoxedLong() {
        return boxedLong;
    }

    public void setBoxedLong(Long boxedLong) {
        this.boxedLong = boxedLong;
    }

    public long getPrimitiveLong() {
        return primitiveLong;
    }

    public void setPrimitiveLong(long primitiveLong) {
        this.primitiveLong = primitiveLong;
    }

    public Double getBoxedDouble() {
        return boxedDouble;
    }

    public void setBoxedDouble(Double boxedDouble) {
        this.boxedDouble = boxedDouble;
    }

    public double getPrimitiveDouble() {
        return primitiveDouble;
    }

    public void setPrimitiveDouble(double primitiveDouble) {
        this.primitiveDouble = primitiveDouble;
    }

    public Float getBoxedFloat() {
        return boxedFloat;
    }

    public void setBoxedFloat(Float boxedFloat) {
        this.boxedFloat = boxedFloat;
    }

    public float getPrimitiveFloat() {
        return primitiveFloat;
    }

    public void setPrimitiveFloat(float primitiveFloat) {
        this.primitiveFloat = primitiveFloat;
    }

    public Short getBoxedShort() {
        return boxedShort;
    }

    public void setBoxedShort(Short boxedShort) {
        this.boxedShort = boxedShort;
    }

    public short getPrimitiveShort() {
        return primitiveShort;
    }

    public void setPrimitiveShort(short primitiveShort) {
        this.primitiveShort = primitiveShort;
    }

    public Byte getBoxedByte() {
        return boxedByte;
    }

    public void setBoxedByte(Byte boxedByte) {
        this.boxedByte = boxedByte;
    }

    public byte getPrimitiveByte() {
        return primitiveByte;
    }

    public void setPrimitiveByte(byte primitiveByte) {
        this.primitiveByte = primitiveByte;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getMappedCount() {
        return mappedCount;
    }

    public void setMappedCount(Integer mappedCount) {
        this.mappedCount = mappedCount;
    }
}
