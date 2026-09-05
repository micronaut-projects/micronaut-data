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
import io.micronaut.data.annotation.InstantiateWithDefaultConstructor
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
    void "a type whose @JsonCreator cannot be mapped is instantiated with its default constructor: #title"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection(className, source)
        RuntimePersistentEntity entity = new RuntimePersistentEntity(introspection)

        then: "the creator Jackson claimed is left untouched"
        introspection.constructorArguments*.name == ['value']

        and: "the processor recorded the fallback and data doesn't use the creator"
        introspection.hasAnnotation(InstantiateWithDefaultConstructor)
        entity.constructorArguments.length == 0
        entity.persistentPropertyNames as Set == propertyNames as Set

        where:
        title                                | className                | source                                | propertyNames
        'class with extra constructor'       | 'test.PojoCountry'       | CLASS_CONSTRUCTOR                     | ['countryCode', 'regionCode']
        'class with static factory'          | 'test.PojoCountry'       | CLASS_STATIC                          | ['countryCode', 'regionCode']
        'static factory, several constructors' | 'test.Thing'           | STATIC_CREATOR_AND_SEVERAL_CONSTRUCTORS | ['name', 'nullableValue']
        'mapped entity'                      | 'test.PojoCountryEntity' | ENTITY_CONSTRUCTOR                    | ['id', 'countryCode', 'regionCode']
    }

    @Unroll
    void "a data mappable creator is still used: #title"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection(className, source)
        RuntimePersistentEntity entity = new RuntimePersistentEntity(introspection)

        then:
        !introspection.hasAnnotation(InstantiateWithDefaultConstructor)
        entity.constructorArguments*.name == expected

        where:
        title                              | className              | source                 | expected
        'mappable @JsonCreator'            | 'test.PojoCountry'     | CLASS_MAPPABLE_CREATOR | ['countryCode', 'regionCode']
        'no jackson, several constructors' | 'test.Thing'           | SEVERAL_CONSTRUCTORS   | ['name']
        'arguments are the id and version' | 'test.VersionedThing'  | ID_AND_VERSION_CREATOR | ['id', 'version', 'name']
    }

    @Unroll
    void "a type whose creator is claimed by jackson and cannot use a default constructor fails to compile: #title"() {
        when:
        buildBeanIntrospection(className, source)

        then:
        def e = thrown(RuntimeException)
        e.message.contains("@JsonCreator $creator is the bean introspection creator of [$className] but its argument(s) [value] are not persistent properties")
        e.message.contains(reason)
        e.message.contains('remove @JsonCreator and use a custom Serde deserializer')

        where:
        title                                        | className          | source                      | creator                        | reason
        'record with extra constructor'              | 'test.Country'     | RECORD_CONSTRUCTOR          | 'Country(String value)'        | 'there is no accessible no-argument constructor and the properties [countryCode, regionCode] cannot be set after construction'
        'record with static factory'                 | 'test.Country'     | RECORD_STATIC               | 'Country.create(String value)' | 'there is no accessible no-argument constructor and the properties [countryCode, regionCode] cannot be set after construction'
        'class with final fields and two constructors' | 'test.PojoCountry' | CLASS_FINAL_FIELDS        | 'PojoCountry(String value)'    | 'there is no accessible no-argument constructor'
        'mutable class without no-arg constructor'   | 'test.Country'     | CLASS_SETTERS_WITHOUT_NOARG | 'Country.fromJson(String value)' | 'there is no accessible no-argument constructor'
        'default constructor but read-only property' | 'test.Country'     | CLASS_NOARG_READ_ONLY       | 'Country(String value)'        | 'the properties [regionCode] cannot be set after construction'
        'default constructor but read-only id'       | 'test.ReadOnlyIdEntity' | ENTITY_READ_ONLY_ID    | 'ReadOnlyIdEntity(String value)' | 'the properties [id] cannot be set after construction'
    }

    // Issue #3752, first variant
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

    // Issue #3752, second variant
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

    // Issue #3752, third variant
    private static final String CLASS_FINAL_FIELDS = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public class PojoCountry {
    public final String countryCode;
    public final String regionCode;

    public PojoCountry(String countryCode, String regionCode) {
        this.countryCode = countryCode;
        this.regionCode = regionCode;
    }

    @JsonCreator
    public PojoCountry(String value) {
        this(value.substring(0, 2), value.length() > 3 ? value.substring(3) : null);
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

    private static final String ENTITY_CONSTRUCTOR = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class PojoCountryEntity {
    @Id
    @GeneratedValue
    private Long id;
    private String countryCode;
    private String regionCode;

    public PojoCountryEntity() {
    }

    @JsonCreator
    public PojoCountryEntity(String value) {
        this.countryCode = value.substring(0, 2);
        this.regionCode = value.length() > 3 ? value.substring(3) : null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    // The identity and the version are persistent properties too, so a creator built from them is mappable
    private static final String ID_AND_VERSION_CREATOR = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;

@MappedEntity
public class VersionedThing {
    @Id
    private final Long id;
    @Version
    private final Long version;
    private final String name;

    @JsonCreator
    public VersionedThing(Long id, Long version, String name) {
        this.id = id;
        this.version = version;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }
}
'''

    // The read-only scan has to see the identity as well, not just the plain properties
    private static final String ENTITY_READ_ONLY_ID = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class ReadOnlyIdEntity {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    public ReadOnlyIdEntity() {
    }

    @JsonCreator
    public ReadOnlyIdEntity(String value) {
        this.name = value;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    private static final String CLASS_NOARG_READ_ONLY = '''
package test;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public class Country {
    private String countryCode;
    private String regionCode;

    public Country() {
    }

    @JsonCreator
    public Country(String value) {
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
