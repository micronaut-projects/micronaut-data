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

class ImplicitETagUtilsSpec extends AbstractDataSpec {

    void "implicit ETag eligibility includes regular scalar and generated identity"() {
        given:
        def entity = buildEntity('test.Book', '''
@MappedEntity
record Book(@Id @GeneratedValue Long id,
    String title,
    String etag) {}
''')
        def id = property(entity, "id")
        def title = property(entity, "title")
        def etag = property(entity, "etag")

        expect:
        ImplicitETagUtils.isImplicitEtagEligible(entity, [], title, etag, false)
        ImplicitETagUtils.isImplicitEtagEligible(entity, [], id, etag, false)
    }

    void "implicit ETag eligibility excludes version and ETag properties"() {
        given:
        def entity = buildEntity('test.Book', '''
@MappedEntity
record Book(@Id Long id,
    String title,
    @Version Long version,
    String etag) {}
''')
        def version = property(entity, "version")
        def etag = property(entity, "etag")

        expect:
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], version, etag, false)
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], etag, etag, false)
    }

    void "implicit ETag eligibility excludes generated and read transformed non identity properties"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.DataTransformer;

@MappedEntity
record Book(@Id Long id,
    @GeneratedValue String generatedCode,
    @DataTransformer(read = "UPPER(title)") String normalizedTitle,
    String etag) {}
''')
        def generatedCode = property(entity, "generatedCode")
        def normalizedTitle = property(entity, "normalizedTitle")
        def etag = property(entity, "etag")

        expect:
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], generatedCode, etag, false)
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], normalizedTitle, etag, false)
    }

    void "implicit ETag eligibility excludes structured values"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import java.util.Map;

@MappedEntity
record Book(@Id Long id,
    @TypeDef(type = DataType.JSON) Map<String, Object> attributes,
    @TypeDef(type = DataType.OBJECT) Object payload,
    String[] tags,
    byte[] bytes,
    String etag) {}
''')
        def etag = property(entity, "etag")

        expect:
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], property(entity, "attributes"), etag, false)
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], property(entity, "payload"), etag, false)
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], property(entity, "tags"), etag, false)
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], property(entity, "bytes"), etag, false)
    }

    void "implicit ETag eligibility includes owning foreign key only when requested"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;

@MappedEntity
record Book(@Id Long id,
    @Relation(Relation.Kind.MANY_TO_ONE) Author author,
    String etag) {}

@MappedEntity
record Author(@Id Long id) {}
''')
        def author = property(entity, "author")
        def etag = property(entity, "etag")

        expect:
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [], author, etag, false)
        ImplicitETagUtils.isImplicitEtagEligible(entity, [], author, etag, true)
    }

    void "implicit ETag eligibility excludes non embedded association path"() {
        given:
        def entity = buildEntity('test.Book', '''
import io.micronaut.data.annotation.Relation;

@MappedEntity
record Book(@Id Long id,
    String title,
    @Relation(Relation.Kind.MANY_TO_ONE) Author author,
    String etag) {}

@MappedEntity
record Author(@Id Long id) {}
''')
        def author = property(entity, "author")
        def title = property(entity, "title")
        def etag = property(entity, "etag")

        expect:
        !ImplicitETagUtils.isImplicitEtagEligible(entity, [author], title, etag, true)
    }

    private static def property(def entity, String name) {
        def persistentProperty = entity.persistentProperties.find { it.name == name }
        if (persistentProperty != null) {
            return persistentProperty
        }
        def identityProperty = entity.identityProperties.find { it.name == name }
        if (identityProperty != null) {
            return identityProperty
        }
        if (entity.hasVersion() && entity.version.name == name) {
            return entity.version
        }
        return null
    }
}
