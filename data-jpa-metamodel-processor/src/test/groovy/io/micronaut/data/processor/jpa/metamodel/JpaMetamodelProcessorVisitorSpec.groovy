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
                    Map<String, String> map
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
                             "MAP"                : [attributeType: JAKARTA_METAMODEL_MAP_ATTRIBUTE, fieldtype: [String.class.getName(), String.class.getName()], declaringType: "test.Train"]]

        expect:

        constantProps.keySet().stream().anyMatch { o -> trainMetaModelClass.getField(o) != null && trainMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }
        trainMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_ENTITY_TYPE
        trainMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.Train'
        try {
            trainMetaModelClass.getField("transientField")
            throw new RuntimeException("@Transient fields found, should be ignored.")
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
                assert trainMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].getCanonicalName() == entrySet.getValue().fieldtype
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
        constantProps.keySet().stream().anyMatch { o -> parentMetaModelClass.getField(o) != null && parentMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }
        constantProps.keySet().stream().anyMatch { o -> childMetaModelClass.getField(o) != null && childMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }
        try {
            parentMetaModelClass.getField("AGE")
            throw new RuntimeException("Parent class shouldn't contain child fields")
        } catch (NoSuchFieldException ignored) {
        }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert childMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert childMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].name == entrySet.getValue().declaringType
            assert childMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][1].name == entrySet.getValue().fieldtype
        }

        parentMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_ENTITY_TYPE
        parentMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.Parent'

        childMetaModelClass.getField('class_').getType().getName() == JAKARTA_METAMODEL_ENTITY_TYPE
        childMetaModelClass.getField('class_').getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == 'test.Child'

    }

}
