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
package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.data.exceptions.MappingException
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import spock.lang.Unroll

/**
 * Issue #3752. {@code @JsonCreator} is mapped to {@code @Creator} by micronaut-core, which makes it the single
 * creator the introspection exposes. Micronaut Data must not fight over that creator - Jackson needs it to build
 * the value object from a single JSON string - it just must not assume it can be used to map the persisted
 * properties.
 */
class EmbeddableJsonCreatorSpec extends AbstractTypeElementSpec {

    @Unroll
    void "data ignores a creator it cannot map and uses setters instead: #title"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection(className, source)
        RuntimePersistentEntity entity = new RuntimePersistentEntity(introspection)

        then: "the creator Jackson claimed is left untouched"
        introspection.constructorArguments*.name == ['value']

        and: "but data doesn't use it, so it can populate the persisted properties itself"
        entity.constructorArguments.length == 0
        entity.persistentPropertyNames as Set == ['countryCode', 'regionCode'] as Set

        where:
        title                          | className          | source
        'class with extra constructor' | 'test.PojoCountry' | CLASS_CONSTRUCTOR
        'class with static factory'    | 'test.PojoCountry' | CLASS_STATIC
    }

    @Unroll
    void "a data mappable creator is still used: #title"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection(className, source)
        RuntimePersistentEntity entity = new RuntimePersistentEntity(introspection)

        then:
        entity.constructorArguments*.name == expected

        where:
        title                                 | className          | source                 | expected
        'mappable @JsonCreator'               | 'test.PojoCountry' | CLASS_MAPPABLE_CREATOR | ['countryCode', 'regionCode']
        'no jackson, several constructors'    | 'test.Thing'       | SEVERAL_CONSTRUCTORS   | ['name']
    }

    @Unroll
    void "an immutable type whose creator is claimed by jackson reports the conflict: #title"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection(className, source)
        new RuntimePersistentEntity(introspection)

        then:
        MappingException e = thrown()
        e.message.contains('is instantiated by the creator [value]')
        e.message.contains('cannot be set after construction')

        where:
        title                            | className       | source
        'record with extra constructor'  | 'test.Country'  | RECORD_CONSTRUCTOR
        'record with static factory'     | 'test.Country'  | RECORD_STATIC
    }

    void "a static @JsonCreator does not promote a wider constructor"() {
        given: "the shape that regressed on UuidEntity: the wider constructor takes a non-null argument"
        BeanIntrospection introspection = buildBeanIntrospection('test.Thing', STATIC_CREATOR_AND_SEVERAL_CONSTRUCTORS)

        when:
        RuntimePersistentEntity entity = new RuntimePersistentEntity(introspection)

        then: "no constructor is re-selected, so nothing forces a null into a non-null argument"
        introspection.constructorArguments*.name == ['value']
        entity.constructorArguments.length == 0
    }

    void "a mutable type with setters but no no-arg constructor is rejected when the json creator is unmappable"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Country', CLASS_SETTERS_WITHOUT_NOARG)
        new RuntimePersistentEntity(introspection)

        then:
        MappingException e = thrown()
        e.message.contains('is instantiated by the creator [value]')
        e.message.contains('no no-argument constructor exists')
    }

    private static final String RECORD_CONSTRUCTOR = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public record Country(String countryCode, String regionCode) {
    @JsonCreator
    public Country(String value) {
        this(value.substring(0, 2), value.length() > 3 ? value.substring(3) : null);
    }

    @Override
    @JsonValue
    public String toString() {
        return countryCode + (regionCode != null ? "-" + regionCode : "");
    }
}
'''

    private static final String RECORD_STATIC = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public record Country(String countryCode, String regionCode) {
    @JsonCreator
    public static Country create(String value) {
        return new Country(value.substring(0, 2), value.length() > 3 ? value.substring(3) : null);
    }

    @Override
    @JsonValue
    public String toString() {
        return countryCode + (regionCode != null ? "-" + regionCode : "");
    }
}
'''

    private static final String CLASS_CONSTRUCTOR = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public class PojoCountry {
    private String countryCode;
    private String regionCode;

    public PojoCountry() {
    }

    @JsonCreator
    public PojoCountry(String value) {
        this.countryCode = value.substring(0, 2);
        this.regionCode = value.length() > 3 ? value.substring(3) : null;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    @Override
    @JsonValue
    public String toString() {
        return countryCode + (regionCode != null ? "-" + regionCode : "");
    }
}
'''

    private static final String CLASS_STATIC = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public class PojoCountry {
    private String countryCode;
    private String regionCode;

    public PojoCountry() {
    }

    @JsonCreator
    public static PojoCountry create(String value) {
        PojoCountry country = new PojoCountry();
        country.setCountryCode(value.substring(0, 2));
        country.setRegionCode(value.length() > 3 ? value.substring(3) : null);
        return country;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    @Override
    @JsonValue
    public String toString() {
        return countryCode + (regionCode != null ? "-" + regionCode : "");
    }
}
'''

    private static final String CLASS_MAPPABLE_CREATOR = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public class PojoCountry {
    private final String countryCode;
    private final String regionCode;

    @JsonCreator
    public PojoCountry(String countryCode, String regionCode) {
        this.countryCode = countryCode;
        this.regionCode = regionCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegionCode() {
        return regionCode;
    }
}
'''

    private static final String SEVERAL_CONSTRUCTORS = '''
package test;

import io.micronaut.data.annotation.Embeddable;
import java.util.UUID;

@Embeddable
public class Thing {
    private final String name;
    private final UUID nullableValue;

    public Thing(String name) {
        this(name, null);
    }

    public Thing(String name, UUID nullableValue) {
        this.name = name;
        this.nullableValue = nullableValue;
    }

    public String getName() {
        return name;
    }

    public UUID getNullableValue() {
        return nullableValue;
    }
}
'''

    private static final String CLASS_SETTERS_WITHOUT_NOARG = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public class Country {
    private String countryCode;
    private String regionCode;

    public Country(String countryCode, String regionCode) {
        this.countryCode = countryCode;
        this.regionCode = regionCode;
    }

    @JsonCreator
    public static Country fromJson(String value) {
        return new Country(value.substring(0, 2),
            value.length() > 3 ? value.substring(3) : null);
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String value) {
        countryCode = value;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String value) {
        regionCode = value;
    }
}
'''

    private static final String STATIC_CREATOR_AND_SEVERAL_CONSTRUCTORS = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Embeddable;
import java.util.UUID;

@Embeddable
public class Thing {
    private String name;
    private UUID nullableValue;

    public Thing() {
    }

    public Thing(String name) {
        this.name = name;
    }

    public Thing(String name, UUID nullableValue) {
        this.name = name;
        this.nullableValue = nullableValue;
    }

    @JsonCreator
    public static Thing parse(String value) {
        return new Thing(value);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getNullableValue() {
        return nullableValue;
    }

    public void setNullableValue(UUID nullableValue) {
        this.nullableValue = nullableValue;
    }
}
'''
}
