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


class JpaMetamodelProcessorVisitorSpec extends AbstractTypeElementSpec {

    final String SINGULAR_ATTRIBUTE = "jakarta.persistence.metamodel.SingularAttribute"
    final String SET_ATTRIBUTE = "jakarta.persistence.metamodel.SetAttribute"
    final String LIST_ATTRIBUTE = "jakarta.persistence.metamodel.ListAttribute"
    final String COLLECTION_ATTRIBUTE = "jakarta.persistence.metamodel.CollectionAttribute"
    final String MAP_ATTRIBUTE = "jakarta.persistence.metamodel.MapAttribute"

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
        def constantProps = ["ID"                 : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.Long", declaringType: "test.Train"],
                             "NAME"               : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.String", declaringType: "test.Train"],
                             "MODEL"              : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.String", declaringType: "test.Train"],
                             "CAPACITY"           : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.Integer", declaringType: "test.Train"],
                             "SPEED"              : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.Double", declaringType: "test.Train"],
                             "ELECTRIC"           : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.Boolean", declaringType: "test.Train"],
                             "DEPARTURE_TIME"     : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.time.LocalDateTime", declaringType: "test.Train"],
                             "CREATED_AT"         : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.time.Instant", declaringType: "test.Train"],
                             "DEPARTURE_DATE"     : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.time.LocalDate", declaringType: "test.Train"],
                             "DEPARTURE_TIME_ONLY": [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.time.LocalTime", declaringType: "test.Train"],
                             "MICRONAUT_RECORD"   : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "test.Train.MicronautRecord", declaringType: "test.Train"],
                             "SEATS"              : [attributeType: LIST_ATTRIBUTE, fieldtype: "java.lang.String", declaringType: "test.Train"],
                             "SET"                : [attributeType: SET_ATTRIBUTE, fieldtype: "java.lang.Integer", declaringType: "test.Train"],
                             "COLLECTION"         : [attributeType: COLLECTION_ATTRIBUTE, fieldtype: "java.lang.Double", declaringType: "test.Train"],
                             "MAP"                : [attributeType: MAP_ATTRIBUTE, fieldtype: ["java.lang.String", "java.lang.String"], declaringType: "test.Train"]]

        expect:

        constantProps.keySet().stream().anyMatch { o -> trainMetaModelClass.getField(o) != null && trainMetaModelClass.getProperties().get(o) == NameUtils.camelCase(o.toLowerCase()) }

        for (var entrySet : constantProps.entrySet()) {
            def field = NameUtils.camelCase(entrySet.getKey().toLowerCase())
            assert trainMetaModelClass.getField(field).getType().getName() == entrySet.getValue().attributeType
            assert trainMetaModelClass.getField(field).getProperties()["genericType"]["actualTypeArguments"][0].getCanonicalName() == entrySet.getValue().declaringType
            if (entrySet.getValue().attributeType == MAP_ATTRIBUTE) {
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

        def constantProps = [ID  : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.Long", declaringType: "test.Parent"],
                             NAME: [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.String", declaringType: "test.Parent"],
                             AGE : [attributeType: SINGULAR_ATTRIBUTE, fieldtype: "java.lang.Long", declaringType: "test.Child"]]
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
    }

}
