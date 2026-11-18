package io.micronaut.data.graph.processor

import io.micronaut.data.document.serde.CustomConverterDeserializer
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.config.annotation.SerdeConfig

/**
 * Serde is on the processor path for graph entities only: the entities of other modules keep their JSON.
 */
class GraphSerdeMappingSpec extends AbstractGraphDataSpec {

    void "an entity of another module is not made serdeable and keeps its JSON names"() {
        when:
        def introspection = buildBeanIntrospection('test.Book', '''
package test;

import io.micronaut.data.annotation.*;

@MappedEntity
class Book {
    @Id
    private Long id;
    @MappedProperty("book_title")
    private String title;
    @Transient
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
''')

        then:
        !introspection.hasStereotype(Serdeable.Serializable)
        !introspection.hasStereotype(Serdeable.Deserializable)
        !introspection.getRequiredProperty('title', String).hasAnnotation(SerdeConfig)
        !introspection.getRequiredProperty('note', String).hasAnnotation(SerdeConfig)
    }

    void "an embeddable of another module is not made serdeable"() {
        when:
        def introspection = buildBeanIntrospection('test.Address', '''
package test;

import io.micronaut.data.annotation.*;

@Embeddable
class Address {
    private String street;

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
}
''')

        then:
        !introspection.hasStereotype(Serdeable.Serializable)
        !introspection.hasStereotype(Serdeable.Deserializable)
    }

    void "#kind entities are serdeable, keep their JSON names and read a converted property through its converter"() {
        when:
        def introspection = buildBeanIntrospection('test.Entry', """
package test;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.annotation.*;
import io.micronaut.data.graph.annotation.*;
import io.micronaut.data.model.runtime.convert.AttributeConverter;

@$kind
class Entry {
    @Id
    private Long id;
    @MappedProperty("entry_name")
    private String name;
    @MappedProperty(converter = RankConverter.class)
    private Rank rank;
    ${kind == 'RelationshipProperties' ? '@TargetNode private Target target;\n    public Target getTarget() { return target; }\n    public void setTarget(Target target) { this.target = target; }' : ''}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Rank getRank() { return rank; }
    public void setRank(Rank rank) { this.rank = rank; }
}

@Node
class Target {
    @Id
    private Long id;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}

class Rank {
}

class RankConverter implements AttributeConverter<Rank, Integer> {
    @Override
    public Integer convertToPersistedValue(Rank value, ConversionContext context) { return 1; }
    @Override
    public Rank convertToEntityValue(Integer value, ConversionContext context) { return new Rank(); }
}
""")
        def rank = introspection.getRequiredProperty('rank', Object)

        then:
        introspection.hasStereotype(Serdeable)
        !introspection.getRequiredProperty('name', String).stringValue(SerdeConfig, SerdeConfig.PROPERTY).isPresent()
        rank.classValue(SerdeConfig, SerdeConfig.DESERIALIZER_CLASS).get() == CustomConverterDeserializer
        rank.classValue(SerdeConfig, SerdeConfig.DESERIALIZE_AS).get() == Integer

        where:
        kind << ['Node', 'RelationshipProperties']
    }
}
