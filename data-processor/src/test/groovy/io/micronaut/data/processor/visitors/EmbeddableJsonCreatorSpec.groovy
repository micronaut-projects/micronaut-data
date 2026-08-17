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
        'mappable @JsonCreator kept'        | 'test.PojoCountry'| CLASS_MAPPABLE_CREATOR
        'static factory only'               | 'test.PojoCountry'| CLASS_FACTORY_ONLY
        '@MappedEntity record constructor'  | 'test.Country'    | MAPPED_ENTITY_RECORD
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

    @Override
    public String toString() {
        return countryCode + (regionCode != null ? "-" + regionCode : "");
    }
}
'''

    private static final String CLASS_FACTORY_ONLY = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public class PojoCountry {
    private final String countryCode;
    private final String regionCode;

    private PojoCountry(String countryCode, String regionCode) {
        this.countryCode = countryCode;
        this.regionCode = regionCode;
    }

    public static PojoCountry of(String countryCode, String regionCode) {
        return new PojoCountry(countryCode, regionCode);
    }

    @JsonCreator
    public static PojoCountry create(String value) {
        return of(value.substring(0, 2), value.length() > 3 ? value.substring(3) : null);
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    @Override
    public String toString() {
        return countryCode + (regionCode != null ? "-" + regionCode : "");
    }
}
'''

    private static final String MAPPED_ENTITY_RECORD = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record Country(@Id String countryCode, String regionCode) {
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
}
