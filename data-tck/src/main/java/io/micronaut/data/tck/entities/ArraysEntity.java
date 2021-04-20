/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

import io.micronaut.core.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

@MappedEntity
public class ArraysEntity {
    @GeneratedValue
    @Id
    private Long someId;

    private String[] stringArray;
    @TypeDef(type = DataType.STRING_ARRAY)
    private Collection<String> stringArrayCollection;
    private Short[] shortArray;
    private short[] shortPrimitiveArray;
    @TypeDef(type = DataType.SHORT_ARRAY)
    private Collection<Short> shortArrayCollection;
    @Nullable
    private Integer[] integerArray;
    @Nullable
    private int[] integerPrimitiveArray;
    @Nullable
    @TypeDef(type = DataType.INTEGER_ARRAY)
    private Collection<Integer> integerArrayCollection;
    private Long[] longArray;
    private long[] longPrimitiveArray;
    @TypeDef(type = DataType.LONG_ARRAY)
    private Collection<Long> longArrayCollection;
    private Float[] floatArray;
    private float[] floatPrimitiveArray;
    @TypeDef(type = DataType.FLOAT_ARRAY)
    private Collection<Float> floatArrayCollection;
    private Double[] doubleArray;
    private double[] doublePrimitiveArray;
    @TypeDef(type = DataType.DOUBLE_ARRAY)
    private Collection<Double> doubleArrayCollection;
    private Character[] characterArray;
    private char[] characterPrimitiveArray;
    @TypeDef(type = DataType.CHARACTER_ARRAY)
    private Collection<Character> characterArrayCollection;
    private Boolean[] booleanArray;
    private boolean[] booleanPrimitiveArray;
    @TypeDef(type = DataType.BOOLEAN_ARRAY)
    private Collection<Boolean> booleanArrayCollection;

    public Long getSomeId() {
        return someId;
    }

    public void setSomeId(Long someId) {
        this.someId = someId;
    }

    public String[] getStringArray() {
        return stringArray;
    }

    public void setStringArray(String[] stringArray) {
        this.stringArray = stringArray;
    }

    public Collection<String> getStringArrayCollection() {
        return stringArrayCollection;
    }

    public void setStringArrayCollection(Collection<String> stringArrayCollection) {
        this.stringArrayCollection = stringArrayCollection;
    }

    public Short[] getShortArray() {
        return shortArray;
    }

    public void setShortArray(Short[] shortArray) {
        this.shortArray = shortArray;
    }

    public short[] getShortPrimitiveArray() {
        return shortPrimitiveArray;
    }

    public void setShortPrimitiveArray(short[] shortPrimitiveArray) {
        this.shortPrimitiveArray = shortPrimitiveArray;
    }

    public Collection<Short> getShortArrayCollection() {
        return shortArrayCollection;
    }

    public void setShortArrayCollection(Collection<Short> shortArrayCollection) {
        this.shortArrayCollection = shortArrayCollection;
    }

    @Nullable
    public Integer[] getIntegerArray() {
        return integerArray;
    }

    public void setIntegerArray(@Nullable Integer[] integerArray) {
        this.integerArray = integerArray;
    }

    @Nullable
    public int[] getIntegerPrimitiveArray() {
        return integerPrimitiveArray;
    }

    public void setIntegerPrimitiveArray(@Nullable int[] integerPrimitiveArray) {
        this.integerPrimitiveArray = integerPrimitiveArray;
    }

    @Nullable
    public Collection<Integer> getIntegerArrayCollection() {
        return integerArrayCollection;
    }

    public void setIntegerArrayCollection(@Nullable Collection<Integer> integerArrayCollection) {
        this.integerArrayCollection = integerArrayCollection;
    }

    public Long[] getLongArray() {
        return longArray;
    }

    public void setLongArray(Long[] longArray) {
        this.longArray = longArray;
    }

    public long[] getLongPrimitiveArray() {
        return longPrimitiveArray;
    }

    public void setLongPrimitiveArray(long[] longPrimitiveArray) {
        this.longPrimitiveArray = longPrimitiveArray;
    }

    public Collection<Long> getLongArrayCollection() {
        return longArrayCollection;
    }

    public void setLongArrayCollection(Collection<Long> longArrayCollection) {
        this.longArrayCollection = longArrayCollection;
    }

    public Float[] getFloatArray() {
        return floatArray;
    }

    public void setFloatArray(Float[] floatArray) {
        this.floatArray = floatArray;
    }

    public float[] getFloatPrimitiveArray() {
        return floatPrimitiveArray;
    }

    public void setFloatPrimitiveArray(float[] floatPrimitiveArray) {
        this.floatPrimitiveArray = floatPrimitiveArray;
    }

    public Collection<Float> getFloatArrayCollection() {
        return floatArrayCollection;
    }

    public void setFloatArrayCollection(Collection<Float> floatArrayCollection) {
        this.floatArrayCollection = floatArrayCollection;
    }

    public Double[] getDoubleArray() {
        return doubleArray;
    }

    public void setDoubleArray(Double[] doubleArray) {
        this.doubleArray = doubleArray;
    }

    public double[] getDoublePrimitiveArray() {
        return doublePrimitiveArray;
    }

    public void setDoublePrimitiveArray(double[] doublePrimitiveArray) {
        this.doublePrimitiveArray = doublePrimitiveArray;
    }

    public Collection<Double> getDoubleArrayCollection() {
        return doubleArrayCollection;
    }

    public void setDoubleArrayCollection(Collection<Double> doubleArrayCollection) {
        this.doubleArrayCollection = doubleArrayCollection;
    }

    public Character[] getCharacterArray() {
        return characterArray;
    }

    public void setCharacterArray(Character[] characterArray) {
        this.characterArray = characterArray;
    }

    public char[] getCharacterPrimitiveArray() {
        return characterPrimitiveArray;
    }

    public void setCharacterPrimitiveArray(char[] characterPrimitiveArray) {
        this.characterPrimitiveArray = characterPrimitiveArray;
    }

    public Collection<Character> getCharacterArrayCollection() {
        return characterArrayCollection;
    }

    public void setCharacterArrayCollection(Collection<Character> characterArrayCollection) {
        this.characterArrayCollection = characterArrayCollection;
    }

    public Boolean[] getBooleanArray() {
        return booleanArray;
    }

    public void setBooleanArray(Boolean[] booleanArray) {
        this.booleanArray = booleanArray;
    }

    public boolean[] getBooleanPrimitiveArray() {
        return booleanPrimitiveArray;
    }

    public void setBooleanPrimitiveArray(boolean[] booleanPrimitiveArray) {
        this.booleanPrimitiveArray = booleanPrimitiveArray;
    }

    public Collection<Boolean> getBooleanArrayCollection() {
        return booleanArrayCollection;
    }

    public void setBooleanArrayCollection(Collection<Boolean> booleanArrayCollection) {
        this.booleanArrayCollection = booleanArrayCollection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArraysEntity)) return false;
        ArraysEntity that = (ArraysEntity) o;
        return Objects.equals(someId, that.someId) &&
                Arrays.equals(stringArray, that.stringArray) &&
                Objects.equals(stringArrayCollection, that.stringArrayCollection) &&
                Arrays.equals(shortArray, that.shortArray) &&
                Arrays.equals(shortPrimitiveArray, that.shortPrimitiveArray) &&
                Objects.equals(shortArrayCollection, that.shortArrayCollection) &&
                Arrays.equals(integerArray, that.integerArray) &&
                Arrays.equals(integerPrimitiveArray, that.integerPrimitiveArray) &&
                Objects.equals(integerArrayCollection, that.integerArrayCollection) &&
                Arrays.equals(longArray, that.longArray) &&
                Arrays.equals(longPrimitiveArray, that.longPrimitiveArray) &&
                Objects.equals(longArrayCollection, that.longArrayCollection) &&
                Arrays.equals(floatArray, that.floatArray) &&
                Arrays.equals(floatPrimitiveArray, that.floatPrimitiveArray) &&
                Objects.equals(floatArrayCollection, that.floatArrayCollection) &&
                Arrays.equals(doubleArray, that.doubleArray) &&
                Arrays.equals(doublePrimitiveArray, that.doublePrimitiveArray) &&
                Objects.equals(doubleArrayCollection, that.doubleArrayCollection) &&
                Arrays.equals(characterArray, that.characterArray) &&
                Arrays.equals(characterPrimitiveArray, that.characterPrimitiveArray) &&
                Objects.equals(characterArrayCollection, that.characterArrayCollection) &&
                Arrays.equals(booleanArray, that.booleanArray) &&
                Arrays.equals(booleanPrimitiveArray, that.booleanPrimitiveArray) &&
                Objects.equals(booleanArrayCollection, that.booleanArrayCollection);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(someId, stringArrayCollection, shortArrayCollection, integerArrayCollection, longArrayCollection, floatArrayCollection, doubleArrayCollection, characterArrayCollection, booleanArrayCollection);
        result = 31 * result + Arrays.hashCode(stringArray);
        result = 31 * result + Arrays.hashCode(shortArray);
        result = 31 * result + Arrays.hashCode(shortPrimitiveArray);
        result = 31 * result + Arrays.hashCode(integerArray);
        result = 31 * result + Arrays.hashCode(integerPrimitiveArray);
        result = 31 * result + Arrays.hashCode(longArray);
        result = 31 * result + Arrays.hashCode(longPrimitiveArray);
        result = 31 * result + Arrays.hashCode(floatArray);
        result = 31 * result + Arrays.hashCode(floatPrimitiveArray);
        result = 31 * result + Arrays.hashCode(doubleArray);
        result = 31 * result + Arrays.hashCode(doublePrimitiveArray);
        result = 31 * result + Arrays.hashCode(characterArray);
        result = 31 * result + Arrays.hashCode(characterPrimitiveArray);
        result = 31 * result + Arrays.hashCode(booleanArray);
        result = 31 * result + Arrays.hashCode(booleanPrimitiveArray);
        return result;
    }
}
