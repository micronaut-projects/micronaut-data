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

class EmbeddableJsonCreatorSpec extends AbstractTypeElementSpec {

    @Unroll
    void "embeddable with @JsonCreator uses persisted properties as constructor args: #title"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection(className, source)
        RuntimePersistentEntity entity = new RuntimePersistentEntity(introspection)

        then:
        introspection.constructorArguments*.name == ['countryCode', 'regionCode']
        entity.constructorArguments*.name == ['countryCode', 'regionCode']
        introspection.instantiate('US', 'NY').toString() == 'US-NY'

        where:
        title                               | className         | source
        'record compact constructor'        | 'test.Country'    | RECORD_CONSTRUCTOR
        'record static factory'             | 'test.Country'    | RECORD_STATIC
        'class extra constructor'           | 'test.PojoCountry'| CLASS_CONSTRUCTOR
        'class static factory'              | 'test.PojoCountry'| CLASS_STATIC
    }

    void "RuntimePersistentEntity of JsonCreator embeddable does not throw MappingException"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Country', RECORD_CONSTRUCTOR)
        new RuntimePersistentEntity(introspection)

        then:
        notThrown(MappingException)
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
    private final String countryCode;
    private final String regionCode;

    public PojoCountry(String countryCode, String regionCode) {
        this.countryCode = countryCode;
        this.regionCode = regionCode;
    }

    @JsonCreator
    public PojoCountry(String value) {
        this(value.substring(0, 2), value.length() > 3 ? value.substring(3) : null);
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegionCode() {
        return regionCode;
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
    private final String countryCode;
    private final String regionCode;

    public PojoCountry(String countryCode, String regionCode) {
        this.countryCode = countryCode;
        this.regionCode = regionCode;
    }

    @JsonCreator
    public static PojoCountry create(String value) {
        return new PojoCountry(value.substring(0, 2), value.length() > 3 ? value.substring(3) : null);
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    @Override
    @JsonValue
    public String toString() {
        return countryCode + (regionCode != null ? "-" + regionCode : "");
    }
}
'''
}
