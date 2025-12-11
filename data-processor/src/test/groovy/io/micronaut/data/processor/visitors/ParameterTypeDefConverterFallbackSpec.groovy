/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.model.DataType

class ParameterTypeDefConverterFallbackSpec extends AbstractDataSpec {

    void "converter is resolved from parameter TYPE when parameter itself has no @TypeDef"() {
        given:
        def repository = buildRepository('test.FallbackRepository', '''
import java.util.UUID;

import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.vector.DoubleVectorAttributeConverter;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.ANSI)
interface FallbackRepository extends GenericRepository<Dummy, UUID> {

    // Parameter is of a TYPE that carries @TypeDef(converter=...), but the parameter itself is NOT annotated
    @Query(value = "select 1 where :p is not null", nativeQuery = true)
    int usesTypeAnnotatedParam(ParamType p);
}

@MappedEntity
class Dummy {
    @io.micronaut.data.annotation.Id
    UUID id;
}

@TypeDef(type = DataType.OBJECT, converter = DoubleVectorAttributeConverter.class)
class ParamType {
}
''')

        and:
        def loader = repository.getBeanType().getClassLoader()
        def paramType = loader.loadClass('test.ParamType')

        when:
        def m = repository.getRequiredMethod("usesTypeAnnotatedParam", paramType)
        def dm = m.getAnnotationMetadata().getAnnotation(DataMethod)
        // Extract the parameter annotations from DataMethod (compile-time metadata)
        def params = dm.getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
        def dataTypes = io.micronaut.data.processor.visitors.TestUtils.getDataTypes(dm)

        then: "exactly one parameter captured"
        params != null
        params.size() == 1

        and: "fallback resolved the converter from the TYPE annotation"
        params.get(0).classValue(DataMethodQueryParameter.META_MEMBER_CONVERTER).isPresent()
        params.get(0).classValue(DataMethodQueryParameter.META_MEMBER_CONVERTER).get().getName()
                == 'io.micronaut.data.model.runtime.convert.vector.DoubleVectorAttributeConverter'

        and: "data type can be resolved as OBJECT (no SQL type inference at this stage)"
        dataTypes.length == 1
        dataTypes[0] == DataType.OBJECT
    }
}
