/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package io.micronaut.data.processor.jpa.metamodel


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.naming.NameUtils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import static io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor.*

class JpaMetamodelProcessorVisitorSpec extends AbstractTypeElementSpec {


    void "test metaModel Generation"() {
        given:
        var classLoader = buildClassLoader("test.Train",
                """
                   package test;
                import io.micronaut.core.annotation.Nullable;
                import jakarta.persistence.*;
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.LocalTime;
                import java.util.*;
                @Entity
                public record Train (
                    @Id
                    long id,
                    String name,
                    String model,
                    Integer capacity,
                    Double speed,
                    Boolean electric,
                    LocalDateTime departureTime,
                    Instant createdAt,
                    LocalDate departureDate,
                    LocalTime departureTimeOnly,
                    MicronautRecord micronautRecord,
                    @Transient
                    String transientField,
                    List<String> seats,
                    Set<Integer> set,
                    Collection<Double> collection,
                    Map<String, String> map,
                    Set rawSet,
                    List rawList,
                    Map rawMap,
                    Collection rawCollection
                    ) {
                        public record MicronautRecord(int primitive) {}
                }
                """)
        def trainMetaModelClass = classLoader.loadClass("test.Train_")
        def constantProps = ["ID"                 : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.class.getName(), declaringType: "test.Train"],
                             "NAME"               : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.Train"],
                             "MODEL"              : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.Train"],
                             "CAPACITY"           : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Integer.class.getName(), declaringType: "test.Train"],
                             "SPEED"              : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Double.class.getName(), declaringType: "test.Train"],
                             "ELECTRIC"           : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Boolean.class.getName(), declaringType: "test.Train"],
                             "DEPARTURE_TIME"     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: LocalDateTime.class.getName(), declaringType: "test.Train"],
                             "CREATED_AT"         : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Instant.class.getName(), declaringType: "test.Train"],
                             "DEPARTURE_DATE"     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: LocalDate.class.getName(), declaringType: "test.Train"],
                             "DEPARTURE_TIME_ONLY": [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: LocalTime.class.getName(), declaringType: "test.Train"],
                             "MICRONAUT_RECORD"   : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: "test.Train.MicronautRecord", declaringType: "test.Train"],
                             "SEATS"              : [attributeType: JAKARTA_METAMODEL_LIST_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.Train"],
                             "SET"                : [attributeType: JAKARTA_METAMODEL_SET_ATTRIBUTE, fieldtype: Integer.class.getName(), declaringType: "test.Train"],
                             "COLLECTION"         : [attributeType: JAKARTA_METAMODEL_COLLECTION_ATTRIBUTE, fieldtype: Double.class.getName(), declaringType: "test.Train"],
                             "MAP"           : [attributeType: JAKARTA_METAMODEL_MAP_ATTRIBUTE, fieldtype: [String.class.getName(), String.class.getName()], declaringType: "test.Train"],
                             "RAW_SET"       : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Set.class.getName(), declaringType: "test.Train"],
                             "RAW_LIST"      : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: List.class.getName(), declaringType: "test.Train"],
                             "RAW_COLLECTION": [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Collection.class.getName(), declaringType: "test.Train"],
                             "RAW_MAP"       : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Map.class.getName(), declaringType: "test.Train"],

        ]

        expect:

        assert constantProps.keySet().stream().allMatch { o -> trainMetaModelClass.getField(o) != null && trainMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }
        trainMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_ENTITY_TYPE
        trainMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.Train'
        try {
            trainMetaModelClass.getField("transientField")
            throw new RuntimeException("Transient fields found, should be ignored.")
        } catch (NoSuchFieldException ignored) {
        }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert trainMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert trainMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == entrySet.getValue().declaringType
            if (entrySet.getValue().attributeType == JAKARTA_METAMODEL_MAP_ATTRIBUTE) {
                assert trainMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].getCanonicalName() == entrySet.getValue().fieldtype[0]
                assert trainMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][2].getCanonicalName() == entrySet.getValue().fieldtype[1]
            } else {
                if (entrySet.getValue().fieldtype.equals(Set.class.getName()) ||
                        entrySet.getValue().fieldtype.equals(List.class.getName()) ||
                        entrySet.getValue().fieldtype.equals(Collection.class.getName()) ||
                        entrySet.getValue().fieldtype.equals(Map.class.getName())) {
                    assert trainMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].rawType.getCanonicalName() == entrySet.getValue().fieldtype
                } else {
                    assert trainMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].getCanonicalName() == entrySet.getValue().fieldtype

                }
            }

        }

    }

    void "test metaModel Generation with inheritance"() {
        given:
        JavaFiles files = new JavaFiles()
        def parentCls = """
                package test;
                import io.micronaut.core.annotation.Nullable;
                import jakarta.persistence.*;
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.LocalTime;
                import java.util.*;
                @MappedSuperclass
                public class Parent {
                    @Id
                    Long id;
                    String name;
                    transient String transientField;
                    static final String TEST = "test";
                    public Parent () {}
                    public Parent(Long id, String name) {
                        this.id = id;
                        this.name = name;
                    }
                    public Long getId(){
                        return this.id;
                    }
                    public String getName(){
                        return this.name;
                    }
                    public void setId(Long id) {
                        this.id = id;
                    }
                    public void setName(String name) {
                        this.name = name;
                    }
                 }
"""
        def childCls = """
                   package test;
                import io.micronaut.core.annotation.Nullable;
                import jakarta.persistence.*;
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.LocalTime;
                import java.util.*;
                @Entity
                public class Child extends Parent {
                    Long age;
                    private Child () {}
                    private Child (Long id, String name, Long age) {
                        super(id, name);
                        this.age = age;
                    }
                    public Long getAge(){
                        return this.age;
                    }
                    public void setAge(Long age) {
                        this.age = age;
                    }
                }

                """
        files.add("test.Parent", parentCls)
        files.add("test.Child", childCls)

        def context = buildContext(files, true)
        def classLoader = context.getClassLoader()
        def parentMetaModelClass = classLoader.loadClass("test.Parent_")
        def childMetaModelClass = classLoader.loadClass("test.Child_")

        def constantProps = [ID  : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.class.getName(), declaringType: "test.Parent"],
                             NAME: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.Parent"],
                             AGE : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.class.getName(), declaringType: "test.Child"]]
        expect:
        assert constantProps.keySet().stream().filter { o -> o != "AGE" }.allMatch { o -> parentMetaModelClass.getField(o) != null && parentMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }
        assert constantProps.keySet().stream().allMatch { o -> childMetaModelClass.getField(o) != null && childMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }
        try {
            parentMetaModelClass.getField("AGE")
            throw new RuntimeException("Parent class shouldn't contain child fields.")
        } catch (NoSuchFieldException ignored) {
        }
        try {
            parentMetaModelClass.getField("transientField")
            throw new RuntimeException("Transient fields found, should be ignored.")
        } catch (NoSuchFieldException ignored) {
        }
        try {
            parentMetaModelClass.getField("TEST")
            throw new RuntimeException("Static fields found, should be ignored ")
        } catch (NoSuchFieldException ignored) {
        }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert childMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert childMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert childMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }

        parentMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_MAPPED_SUPER_CLASS_TYPE
        parentMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.Parent'

        childMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_ENTITY_TYPE
        childMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.Child'

    }

    void "test metaModel class Generation with access type annotation FIELD"() {
        given:

        def classLoader = buildClassLoader('test.FieldAccessClass', """
                package test;
                import io.micronaut.core.annotation.Nullable;
                import jakarta.persistence.*;
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.LocalTime;
                import java.util.*;
                @Access(AccessType.FIELD)
                @Entity
                public class FieldAccessClass {
                    @Id
                    Long id;
                    String name;
                    private String fieldWithoutAccessors;
                    public Long getId(){
                        return this.id;
                    }
                    public String getName(){
                        return this.name;
                    }
                    public void setId(Long id) {
                        this.id = id;
                    }
                    public void setName(String name) {
                        this.name = name;
                    }
                 }
                 """)

        def fieldAccessClassMetaModelClass = classLoader.loadClass("test.FieldAccessClass_")

        def constantProps = [ID                     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.class.getName(), declaringType: "test.FieldAccessClass"],
                             NAME                   : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.FieldAccessClass"],
                             FIELD_WITHOUT_ACCESSORS: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.FieldAccessClass"]]
        expect:

        assert constantProps.keySet().stream().allMatch { o -> fieldAccessClassMetaModelClass.getField(o) != null && fieldAccessClassMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert fieldAccessClassMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert fieldAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert fieldAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }
    }

    void "test metaModel Generation with access type annotations"() {
        given:

        def classLoader = buildClassLoader("test.PropertyAccessClass", """
                   package test;
                import io.micronaut.core.annotation.Nullable;
                import jakarta.persistence.*;
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.LocalTime;
                import java.util.*;
                @Entity
                @Access(AccessType.PROPERTY)
                public class PropertyAccessClass {
                    @Id
                    Long id;
                    String fieldWithoutAccessors;
                    String name;

                    public Long getId(){
                        return this.id;
                    }
                    public void setId(Long id) {
                        this.id = id;
                    }
                    public String getName(){
                        return this.name;
                    }
                    public void setName(String name) {
                        this.name = name;
                    }
                }
                """)
        def propertyAccessClassMetaModelClass = classLoader.loadClass("test.PropertyAccessClass_")

        def constantProps = [ID  : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.class.getName(), declaringType: "test.PropertyAccessClass"],
                             NAME: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.PropertyAccessClass"]]
        expect:

        assert constantProps.keySet().stream().allMatch { o -> propertyAccessClassMetaModelClass.getField(o) != null && propertyAccessClassMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert propertyAccessClassMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert propertyAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert propertyAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }
        try {
            propertyAccessClassMetaModelClass.getField("FIELD_WITHOUT_ACCESSORS")
            throw new RuntimeException("FIELD_WITHOUT_ACCESSORS shoudn't exists in property access type entity")
        } catch (NoSuchFieldException ignored) {
        }

    }

    void "test metaModel Generation with property type annotations and field annotated"() {
        given:

        def classLoader = buildClassLoader("test.PropertyAccessClass", """
                   package test;
                import io.micronaut.core.annotation.Nullable;
                import jakarta.persistence.*;
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.LocalTime;
                import java.util.*;
                @Entity
                @Access(AccessType.PROPERTY)
                public class PropertyAccessClass {
                    @Id
                    Long id;
                    @Access(AccessType.FIELD)
                    String fieldWithoutAccessors;
                    boolean active;
                    String name;

                    public Long getId(){
                        return this.id;
                    }
                    public void setId(Long id) {
                        this.id = id;
                    }
                    public String getName(){
                        return this.name;
                    }
                    public void setName(String name) {
                        this.name = name;
                    }
                    public boolean isActive() {
                        return this.active;
                    }
                    public void setActive(boolean active) {
                        this.active = active;
                    }
                }
                """)
        def propertyAccessClassMetaModelClass = classLoader.loadClass("test.PropertyAccessClass_")

        def constantProps = [ID                     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.class.getName(), declaringType: "test.PropertyAccessClass"],
                             NAME                   : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.PropertyAccessClass"],
                             FIELD_WITHOUT_ACCESSORS: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.PropertyAccessClass"],
                             ACTIVE                 : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Boolean.class.getName(), declaringType: "test.PropertyAccessClass"]]
        expect:

        assert constantProps.keySet().stream().allMatch { o -> propertyAccessClassMetaModelClass.getField(o) != null && propertyAccessClassMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert propertyAccessClassMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert propertyAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert propertyAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }
    }

    void "test metaModel Generation with embeddable entity"() {
        given:

        def classLoader = buildClassLoader("test.EmbeddableClass", """
                   package test;
                import io.micronaut.core.annotation.Nullable;
                import jakarta.persistence.*;
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.LocalTime;
                import java.util.*;
                @Embeddable
                public class EmbeddableClass {
                    @Id
                    Long id;
                    boolean active;
                    String name;

                    public Long getId(){
                        return this.id;
                    }
                    public void setId(Long id) {
                        this.id = id;
                    }
                    public String getName(){
                        return this.name;
                    }
                    public void setName(String name) {
                        this.name = name;
                    }
                    public boolean isActive() {
                        return this.active;
                    }
                    public void setActive(boolean active) {
                        this.active = active;
                    }
                }
                """)
        def embeddableClassMetaModelClass = classLoader.loadClass("test.EmbeddableClass_")

        def constantProps = [ID    : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.class.getName(), declaringType: "test.EmbeddableClass"],
                             NAME  : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.class.getName(), declaringType: "test.EmbeddableClass"],
                             ACTIVE: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Boolean.class.getName(), declaringType: "test.EmbeddableClass"]]
        expect:

        constantProps.keySet().stream().anyMatch { o -> embeddableClassMetaModelClass.getField(o) != null && embeddableClassMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert embeddableClassMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert embeddableClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert embeddableClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }

        embeddableClassMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_EMBEDDABLE_TYPE
        embeddableClassMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.EmbeddableClass'
    }


}
