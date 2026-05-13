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
import spock.lang.Ignore

import java.lang.reflect.ParameterizedType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import static io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor.JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE
import static io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor.JAKARTA_METAMODEL_ENTITY_TYPE
import static io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor.JAKARTA_METAMODEL_MAP_ATTRIBUTE
import static io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor.JAKARTA_METAMODEL_EMBEDDABLE_TYPE
import static io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor.JAKARTA_METAMODEL_LIST_ATTRIBUTE
import static io.micronaut.data.processor.jpa.metamodel.JpaMetamodelProcessor.JAKARTA_METAMODEL_SET_ATTRIBUTE

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
                    String[] strings,
                    MicronautRecord micronautRecord,
                    @Transient
                    String transientField,
                    List<String> seats,
                    Set<Integer> set,
                    Collection<Double> collection,
                    Map<String, String> map,
                    Map<String, String> maps,
                    Set<Object> objectSet,
                    Set rawSet,
                    List rawList,
                    Map rawMap,
                    Collection rawCollection
                    ) {
                        public record MicronautRecord(int primitive) {}
                }
                """)
        def trainMetaModelClass = classLoader.loadClass("test.Train_")
        def constantProps = ["ID"                 : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Long.name], declaringType: "test.Train"],
                             "NAME"               : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [String.name], declaringType: "test.Train"],
                             "MODEL"              : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [String.name], declaringType: "test.Train"],
                             "CAPACITY"           : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Integer.name], declaringType: "test.Train"],
                             "SPEED"              : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Double.name], declaringType: "test.Train"],
                             "ELECTRIC"           : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Boolean.name], declaringType: "test.Train"],
                             "DEPARTURE_TIME"     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [LocalDateTime.name], declaringType: "test.Train"],
                             "CREATED_AT"         : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Instant.name], declaringType: "test.Train"],
                             "DEPARTURE_DATE"     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [LocalDate.name], declaringType: "test.Train"],
                             "DEPARTURE_TIME_ONLY": [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [LocalTime.name], declaringType: "test.Train"],
                             "MICRONAUT_RECORD"   : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: ["test.Train.MicronautRecord"], declaringType: "test.Train"],
                             "SEATS"              : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [List.name, String.name], declaringType: "test.Train"],
                             "SET"                : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Set.name, Integer.name], declaringType: "test.Train"],
                             "COLLECTION"         : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Collection.name, Double.name], declaringType: "test.Train"],
                             "MAP"                : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Map.name, String.name, String.name], declaringType: "test.Train"],
                             "RAW_SET"            : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Set.name], declaringType: "test.Train"],
                             "RAW_LIST"           : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [List.name], declaringType: "test.Train"],
                             "RAW_COLLECTION"     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Collection.name], declaringType: "test.Train"],
                             "RAW_MAP"            : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: [Map.name], declaringType: "test.Train"],

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

            def fieldTypeArgs = trainMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"]
            assert fieldTypeArgs[0].getCanonicalName() == entrySet.getValue().declaringType

            if (entrySet.getValue().attributeType == JAKARTA_METAMODEL_MAP_ATTRIBUTE) {
                assert fieldTypeArgs[1].getCanonicalName() == entrySet.getValue().fieldtype[0]
                assert fieldTypeArgs[2].getCanonicalName() == entrySet.getValue().fieldtype[1]
            } else {
                if ((entrySet.getValue().fieldtype.first.equals(Set.name) ||
                        entrySet.getValue().fieldtype.first.equals(List.name) ||
                        entrySet.getValue().fieldtype.first.equals(Collection.name)) && entrySet.getValue().fieldtype.size() > 1) {
                    assert fieldTypeArgs[1].rawType.getCanonicalName() == entrySet.getValue().fieldtype[0]
                    assert fieldTypeArgs[1]["actualTypeArguments"][0].getCanonicalName() == entrySet.getValue().fieldtype[1]

                } else if (entrySet.getValue().fieldtype.first.equals(Map.name) && entrySet.getValue().fieldtype.size() > 1) {
                    if (fieldTypeArgs.findAll().isEmpty()) {
                        continue;
                    }
                    assert fieldTypeArgs[1].rawType.getCanonicalName() == entrySet.getValue().fieldtype[0]
                    assert fieldTypeArgs[1]["actualTypeArguments"][0].getCanonicalName() == entrySet.getValue().fieldtype[1]
                    assert fieldTypeArgs[1]["actualTypeArguments"][1].getCanonicalName() == entrySet.getValue().fieldtype[2]
                } else {
                    if (fieldTypeArgs[1] instanceof ParameterizedType) {
                        assert fieldTypeArgs[1].rawType.getCanonicalName() == entrySet.getValue().fieldtype[0]
                    } else {
                        assert fieldTypeArgs[1].getCanonicalName() == entrySet.getValue().fieldtype[0]
                    }
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

        def constantProps = [ID  : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.Parent"],
                             NAME: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.Parent"],
                             AGE : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.Child"]]
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

    @Ignore("Access annotation not supported currently.")
    void "test metaModel class Generation with access type annotation FIELD"() {
        given:

        def classLoader = buildClassLoader('test.FieldAccessClass', """
                package test;
                import io.micronaut.core.annotation.Introspected;import io.micronaut.core.annotation.Nullable;
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
                    String fieldWithoutAccessors;
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

        def constantProps = [ID                     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.FieldAccessClass"],
                             NAME                   : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.FieldAccessClass"],
                             FIELD_WITHOUT_ACCESSORS: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.FieldAccessClass"]]
        expect:

        assert constantProps.keySet().stream().allMatch { o -> fieldAccessClassMetaModelClass.getField(o) != null && fieldAccessClassMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert fieldAccessClassMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert fieldAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert fieldAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }
    }

    @Ignore("Access annotation not supported currently.")
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

        def constantProps = [ID  : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.PropertyAccessClass"],
                             NAME: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.PropertyAccessClass"]]
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

    @Ignore("Access annotation not supported currently.")
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

        def constantProps = [ID                     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.PropertyAccessClass"],
                             NAME                   : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.PropertyAccessClass"],
                             FIELD_WITHOUT_ACCESSORS: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.PropertyAccessClass"],
                             ACTIVE                 : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Boolean.name, declaringType: "test.PropertyAccessClass"]]
        expect:

        assert constantProps.keySet().stream().allMatch { o -> propertyAccessClassMetaModelClass.getField(o) != null && propertyAccessClassMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert propertyAccessClassMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert propertyAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert propertyAccessClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }
    }

    @Ignore("Access annotation not supported currently.")
    void "test metaModel Generation with mixed access"() {
        given:

        def classLoader = buildClassLoader("test.EmployeeMixedAccessEmbeddedId", """
        package test;

        import jakarta.persistence.*;

        @Entity
        public class EmployeeMixedAccessEmbeddedId {
            private EmployeeId id;
            private String name;
            private double salary;
            private String fieldWithoutAccessors;
            @Access(AccessType.FIELD)
            private String fieldAnnotated;

            @EmbeddedId
            public EmployeeId getId() {
                return id;
            }

            public void setId(EmployeeId id) {
                this.id = id;
            }

            @Column(name = "name")
            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            @Column(name = "salary")
            public double getSalary() {
                return salary;
            }

            public void setSalary(double salary) {
                this.salary = salary;
            }

            @Embeddable
            public static class EmployeeId {
                private Long id;
                private String number;

                public Long getId() {
                    return id;
                }

                public void setId(Long id) {
                    this.id = id;
                }

                public String getNumber() {
                    return number;
                }

                public void setNumber(String number) {
                    this.number = number;
                }
            }
        }
                """)
        def employeeMixedAccessEmbaddedId = classLoader.loadClass("test.EmployeeMixedAccessEmbeddedId_")

        def constantProps = [ID             : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: "test.EmployeeMixedAccessEmbeddedId\$EmployeeId", declaringType: "test.EmployeeMixedAccessEmbeddedId"],
                             NAME           : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.EmployeeMixedAccessEmbeddedId"],
                             SALARY         : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Double.name, declaringType: "test.EmployeeMixedAccessEmbeddedId"],
                             FIELD_ANNOTATED: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.EmployeeMixedAccessEmbeddedId"]]
        expect:

        assert constantProps.keySet().stream().allMatch { o -> employeeMixedAccessEmbaddedId.getField(o) != null && employeeMixedAccessEmbaddedId.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert employeeMixedAccessEmbaddedId.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert employeeMixedAccessEmbaddedId.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert employeeMixedAccessEmbaddedId.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }

        employeeMixedAccessEmbaddedId.getField('class_').getType().getName() == JAKARTA_METAMODEL_ENTITY_TYPE
        employeeMixedAccessEmbaddedId.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.EmployeeMixedAccessEmbeddedId'

        try {
            employeeMixedAccessEmbaddedId.getField('fieldWithoutAccessors')
            throw new RuntimeException("fieldWithoutAccessors shouldn't exists in the metamodel generated class")
        } catch (NoSuchFieldException ignored) {
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

        def constantProps = [ID    : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.EmbeddableClass"],
                             NAME  : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.EmbeddableClass"],
                             ACTIVE: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Boolean.name, declaringType: "test.EmbeddableClass"]]
        expect:

        assert constantProps.keySet().stream().allMatch { o -> embeddableClassMetaModelClass.getField(o) != null && embeddableClassMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert embeddableClassMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert embeddableClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert embeddableClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }

        embeddableClassMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_EMBEDDABLE_TYPE
        embeddableClassMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.EmbeddableClass'
    }

    void "test metaModel Generation with MappedEntity annotation"() {
        given:

        def classLoader = buildClassLoader("test.MappedEntityTest", """
                   package test;
                import io.micronaut.core.annotation.Nullable;
                import io.micronaut.data.annotation.MappedEntity;
                import jakarta.persistence.*;
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.LocalTime;
                import java.util.*;
                @MappedEntity
                public class MappedEntityTest {
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
        def embeddableClassMetaModelClass = classLoader.loadClass("test.MappedEntityTest_")

        def constantProps = [ID    : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.MappedEntityTest"],
                             NAME  : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.MappedEntityTest"],
                             ACTIVE: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Boolean.name, declaringType: "test.MappedEntityTest"]]
        expect:

        assert constantProps.keySet().stream().allMatch { o -> embeddableClassMetaModelClass.getField(o) != null && embeddableClassMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert embeddableClassMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert embeddableClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert embeddableClassMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }

        embeddableClassMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_ENTITY_TYPE
        embeddableClassMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.MappedEntityTest'
    }

    void "test metaModel Generation with bidirectional OneToMany and ManyToOne"() {
        given:
        JavaFiles files = new JavaFiles()

        files.add("test.Department", """
        package test;

        import jakarta.persistence.*;
        import java.util.*;

        @Entity
        public class Department {
            @Id
            Long id;
            String name;
            @OneToMany(mappedBy = "department")
            List<Employee> employees = new ArrayList<>();

            public Long getId() {
                return id;
            }

            public void setId(Long id) {
                this.id = id;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public List<Employee> getEmployees() {
                return employees;
            }

            public void setEmployees(List<Employee> employees) {
                this.employees = employees;
            }
        }
    """)

        files.add("test.Employee", """
        package test;

        import jakarta.persistence.*;

        @Entity
        public class Employee {
            @Id
            Long id;
            String name;
            @ManyToOne
            Department department;

            public Long getId() {
                return id;
            }

            public void setId(Long id) {
                this.id = id;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public Department getDepartment() {
                return department;
            }

            public void setDepartment(Department department) {
                this.department = department;
            }
        }
    """)

        def context = buildContext(files, true)
        def cl = context.classLoader

        def deptMeta = cl.loadClass("test.Department_")
        def empMeta = cl.loadClass("test.Employee_")

        def deptConstantProps = [
                ID       : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.Department"],
                NAME     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.Department"],
                EMPLOYEES: [attributeType: JAKARTA_METAMODEL_LIST_ATTRIBUTE, fieldtype: "test.Employee", declaringType: "test.Department"],
        ]

        def empConstantProps = [
                ID        : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.Employee"],
                NAME      : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.Employee"],
                DEPARTMENT: [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: "test.Department", declaringType: "test.Employee"],
        ]

        expect: "Department constants + fields"
        assert deptConstantProps.keySet().stream().allMatch { o ->
            deptMeta.getField(o) != null && deptMeta.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase())
        }
        for (def entry : deptConstantProps.entrySet()) {
            def field = NameUtils.camelCase(entry.key.toLowerCase())
            def f = deptMeta.getField(field)
            assert f.type.name == entry.value.attributeType

            def typeArgs = f.getProperties()["genericType"]["actualTypeArguments"]
            assert typeArgs[0].name == entry.value.declaringType

            if (entry.value.attributeType == JAKARTA_METAMODEL_LIST_ATTRIBUTE) {
                assert typeArgs[1].name == entry.value.fieldtype
            } else {
                assert typeArgs[1].name == entry.value.fieldtype
            }
        }

        and: "Employee constants + fields"
        assert empConstantProps.keySet().stream().allMatch { o ->
            empMeta.getField(o) != null && empMeta.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase())
        }
        for (def entry : empConstantProps.entrySet()) {
            def field = NameUtils.camelCase(entry.key.toLowerCase())
            def f = empMeta.getField(field)
            assert f.type.name == entry.value.attributeType
            def typeArgs = f.getProperties()["genericType"]["actualTypeArguments"]
            assert typeArgs[0].name == entry.value.declaringType
            assert typeArgs[1].name == entry.value.fieldtype
        }

        and: "class_ field types"
        deptMeta.getField('class_').type.name == JAKARTA_METAMODEL_ENTITY_TYPE
        empMeta.getField('class_').type.name == JAKARTA_METAMODEL_ENTITY_TYPE
    }

    void "test metaModel Generation with bidirectional ManyToMany"() {
        given:
        JavaFiles files = new JavaFiles()

        files.add("test.Student", """
        package test;

        import jakarta.persistence.*;
        import java.util.*;

        @Entity
        public class Student {
            @Id
            Long id;
            String name;
            @ManyToMany(mappedBy = "students")
            Set<Course> courses = new HashSet<>();

            public Long getId() {
                return id;
            }

            public void setId(Long id) {
                this.id = id;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public Set<Course> getCourses() {
                return courses;
            }

            public void setCourses(Set<Course> courses) {
                this.courses = courses;
            }
        }
    """)

        files.add("test.Course", """
        package test;

        import jakarta.persistence.*;
        import java.util.*;

        @Entity
        public class Course {
            @Id
            Long id;

            String title;

            @ManyToMany
            Set<Student> students = new HashSet<>();

            public Long getId() {
                return id;
            }

            public void setId(Long id) {
                this.id = id;
            }

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }

            public Set<Student> getStudents() {
                return students;
            }

            public void setStudents(Set<Student> students) {
                this.students = students;
            }
        }
    """)

        def context = buildContext(files, true)
        def cl = context.classLoader

        def studentMeta = cl.loadClass("test.Student_")
        def courseMeta = cl.loadClass("test.Course_")

        def studentConstantProps = [
                ID     : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.Student"],
                NAME   : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.Student"],
                COURSES: [attributeType: JAKARTA_METAMODEL_SET_ATTRIBUTE, fieldtype: "test.Course", declaringType: "test.Student"],
        ]

        def courseConstantProps = [
                ID      : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: Long.name, declaringType: "test.Course"],
                TITLE   : [attributeType: JAKARTA_METAMODEL_SINGULAR_ATTRIBUTE, fieldtype: String.name, declaringType: "test.Course"],
                STUDENTS: [attributeType: JAKARTA_METAMODEL_SET_ATTRIBUTE, fieldtype: "test.Student", declaringType: "test.Course"],
        ]

        expect: "Student constants + fields"
        assert studentConstantProps.keySet().stream().allMatch { o ->
            studentMeta.getField(o) != null && studentMeta.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase())
        }
        for (def entry : studentConstantProps.entrySet()) {
            def field = NameUtils.camelCase(entry.key.toLowerCase())
            def f = studentMeta.getField(field)
            assert f.type.name == entry.value.attributeType
            def typeArgs = f.getProperties()["genericType"]["actualTypeArguments"]
            assert typeArgs[0].name == entry.value.declaringType
            assert typeArgs[1].name == entry.value.fieldtype
        }

        and: "Course constants + fields"
        assert courseConstantProps.keySet().stream().allMatch { o ->
            courseMeta.getField(o) != null && courseMeta.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase())
        }
        for (def entry : courseConstantProps.entrySet()) {
            def field = NameUtils.camelCase(entry.key.toLowerCase())
            def f = courseMeta.getField(field)
            assert f.type.name == entry.value.attributeType
            def typeArgs = f.getProperties()["genericType"]["actualTypeArguments"]
            assert typeArgs[0].name == entry.value.declaringType
            assert typeArgs[1].name == entry.value.fieldtype
        }

        and: "class_ field types"
        studentMeta.getField('class_').type.name == JAKARTA_METAMODEL_ENTITY_TYPE
        courseMeta.getField('class_').type.name == JAKARTA_METAMODEL_ENTITY_TYPE
    }

}
