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
import io.micronaut.data.annotation.FlatEntity
import spock.lang.Unroll

/**
 * The processor marks an entity whose columns all map onto properties with a typed result set getter, so that the
 * SQL runtimes can read it by ordinal without resolving the mapping per column.
 */
class FlatEntitySpec extends AbstractTypeElementSpec {

    @Unroll
    void "#title is #description"() {
        expect:
        buildBeanIntrospection(className, source).hasAnnotation(FlatEntity) == flat

        where:
        title                              | className        | source              | flat
        'a class with setters'             | 'test.Flat'      | FLAT                | true
        'a record'                         | 'test.FlatRecord' | FLAT_RECORD        | true
        'an entity with enum properties'   | 'test.Enums'     | ENUMS               | true
        'an entity with a date and a UUID' | 'test.Temporal'  | TEMPORAL            | true
        'an entity with an association'    | 'test.Child'     | ASSOCIATION         | false
        'an entity with an embedded value' | 'test.WithEmbedded' | EMBEDDED         | false
        'an entity with a composite id'    | 'test.Composite' | COMPOSITE           | false
        'an entity with a JSON column'     | 'test.Json'      | JSON                | false
        'an entity with a converter'       | 'test.Converted' | CONVERTER           | false
        'an entity with a @TypeDef converter' | 'test.TypeDefConverted' | TYPEDEF_CONVERTER | false
        'an entity with an unsettable id'  | 'test.ReadOnlyId' | READ_ONLY_ID       | false
        'an entity with an unsettable property' | 'test.ReadOnlyProperty' | READ_ONLY_PROPERTY | true
        description = flat ? 'flat' : 'not flat'
    }

    static final String FLAT = '''
package test;

import io.micronaut.data.annotation.*;

@MappedEntity
class Flat {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private int count;
    private Double amount;
    private boolean enabled;
    private byte[] data;
    private java.math.BigDecimal price;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal price) { this.price = price; }
}
'''

    static final String FLAT_RECORD = '''
package test;

import io.micronaut.data.annotation.*;
import org.jspecify.annotations.Nullable;

@MappedEntity
record FlatRecord(@Id @GeneratedValue Long id, String name, @Nullable Integer quantity, int count) {
}
'''

    static final String ENUMS = '''
package test;

import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;

@MappedEntity
class Enums {
    enum Suit { HEARTS, SPADES }

    @Id
    @GeneratedValue
    private Long id;
    private Suit byName;
    @MappedProperty(type = DataType.INTEGER)
    private Suit byOrdinal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Suit getByName() { return byName; }
    public void setByName(Suit byName) { this.byName = byName; }
    public Suit getByOrdinal() { return byOrdinal; }
    public void setByOrdinal(Suit byOrdinal) { this.byOrdinal = byOrdinal; }
}
'''

    static final String TEMPORAL = '''
package test;

import io.micronaut.data.annotation.*;
import java.time.*;
import java.util.UUID;

@MappedEntity
class Temporal {
    @Id
    @AutoPopulated
    private UUID id;
    private LocalDateTime created;
    private LocalDate day;
    private Instant instant;
    private java.util.Date date;
    private char letter;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public LocalDateTime getCreated() { return created; }
    public void setCreated(LocalDateTime created) { this.created = created; }
    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public Instant getInstant() { return instant; }
    public void setInstant(Instant instant) { this.instant = instant; }
    public java.util.Date getDate() { return date; }
    public void setDate(java.util.Date date) { this.date = date; }
    public char getLetter() { return letter; }
    public void setLetter(char letter) { this.letter = letter; }
}
'''

    static final String ASSOCIATION = '''
package test;

import io.micronaut.data.annotation.*;

@MappedEntity
class Parent {
    @Id
    @GeneratedValue
    private Long id;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}

@MappedEntity
class Child {
    @Id
    @GeneratedValue
    private Long id;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Parent parent;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Parent getParent() { return parent; }
    public void setParent(Parent parent) { this.parent = parent; }
}
'''

    static final String EMBEDDED = '''
package test;

import io.micronaut.data.annotation.*;

@Embeddable
class Address {
    private String street;
    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
}

@MappedEntity
class WithEmbedded {
    @Id
    @GeneratedValue
    private Long id;
    @Relation(Relation.Kind.EMBEDDED)
    private Address address;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
}
'''

    static final String COMPOSITE = '''
package test;

import io.micronaut.data.annotation.*;

@MappedEntity
class Composite {
    @Id
    private Long first;
    @Id
    private Long second;
    private String name;

    public Long getFirst() { return first; }
    public void setFirst(Long first) { this.first = first; }
    public Long getSecond() { return second; }
    public void setSecond(Long second) { this.second = second; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
'''

    static final String JSON = '''
package test;

import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import java.util.Map;

@MappedEntity
class Json {
    @Id
    @GeneratedValue
    private Long id;
    @TypeDef(type = DataType.JSON)
    private Map<String, String> attributes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
}
'''

    static final String CONVERTER = '''
package test;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.runtime.convert.AttributeConverter;

class Money {
    final long cents;
    Money(long cents) { this.cents = cents; }
}

class MoneyConverter implements AttributeConverter<Money, Long> {
    @Override
    public Long convertToPersistedValue(Money money, ConversionContext context) { return money == null ? null : money.cents; }
    @Override
    public Money convertToEntityValue(Long value, ConversionContext context) { return value == null ? null : new Money(value); }
}

@MappedEntity
class Converted {
    @Id
    @GeneratedValue
    private Long id;
    @MappedProperty(converter = MoneyConverter.class)
    private Money price;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Money getPrice() { return price; }
    public void setPrice(Money price) { this.price = price; }
}
'''

    static final String TYPEDEF_CONVERTER = '''
package test;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.AttributeConverter;

class Code {
    final String value;
    Code(String value) { this.value = value; }
}

class CodeConverter implements AttributeConverter<Code, String> {
    @Override
    public String convertToPersistedValue(Code code, ConversionContext context) { return code == null ? null : code.value; }
    @Override
    public Code convertToEntityValue(String value, ConversionContext context) { return value == null ? null : new Code(value); }
}

@MappedEntity
class TypeDefConverted {
    @Id
    @GeneratedValue
    private Long id;
    @TypeDef(type = DataType.STRING, converter = CodeConverter.class)
    private Code code;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Code getCode() { return code; }
    public void setCode(Code code) { this.code = code; }
}
'''

    static final String READ_ONLY_ID = '''
package test;

import io.micronaut.data.annotation.*;

@MappedEntity
class ReadOnlyId {
    @Id
    @GeneratedValue
    private Long id;
    private String name;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
'''

    static final String READ_ONLY_PROPERTY = '''
package test;

import io.micronaut.data.annotation.*;

@MappedEntity
class ReadOnlyProperty {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private String computed;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getComputed() { return computed; }
}
'''
}
