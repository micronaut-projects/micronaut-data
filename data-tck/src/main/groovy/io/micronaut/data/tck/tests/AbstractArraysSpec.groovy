/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.ArraysEntity
import io.micronaut.data.tck.repositories.ArraysEntityRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractArraysSpec extends Specification {

    abstract ArraysEntityRepository getArraysEntityRepository()

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    def "should insert and update an entity with arrays"() {
        given:
            ArraysEntity entity = new ArraysEntity()
            entity.stringArray = ["XYZ", "123", "ABC"]
            entity.stringArrayCollection = ["XYZ", "123", "ABC"]
            entity.shortArray = [1, 2, 3]
            entity.shortPrimitiveArray = [1, 2, 3]
            entity.shortArrayCollection = [(short)1, (short)2, (short)3]
            entity.integerArray = [1, 2, 3]
            entity.integerPrimitiveArray = [1, 2, 3]
            entity.integerArrayCollection = [1, 2, 3]
            entity.longArray = [1, 2, 3]
            entity.longPrimitiveArray = [1, 2, 3]
            entity.longArrayCollection = [1L, 2L, 3L]
            entity.floatArray = [1, 2, 3]
            entity.floatPrimitiveArray = [1, 2, 3]
            entity.floatArrayCollection = [1f, 2f, 3f]
            entity.doubleArray = [1, 2, 3]
            entity.doublePrimitiveArray = [1, 2, 3]
            entity.doubleArrayCollection = [1d, 2d, 3d]
            entity.characterArray = ['a', 'b', 'c'] as char[]
            entity.characterPrimitiveArray = ['a', 'b', 'c'] as char[]
            entity.characterArrayCollection = ['a', 'b', 'c'] as char[]
            entity.booleanArray = [true, false, true, false]
            entity.booleanPrimitiveArray = [true, false, true, false]
            entity.booleanArrayCollection = [true, false, true, false]
        when:
            arraysEntityRepository.save(entity)
            ArraysEntity entityStored = arraysEntityRepository.findById(entity.someId).get()
        then:
            entityStored == entity
        when:
            def dto = arraysEntityRepository.queryBySomeId(entity.someId)
        then:
            dto
            dto.someId == entity.someId
            dto.stringArray == ["XYZ", "123", "ABC"] as String[]
            dto.stringArrayCollection == ["XYZ", "123", "ABC"]
            dto.shortArray == [1, 2, 3] as Short[]
            dto.shortPrimitiveArray == [1, 2, 3] as short[]
            dto.shortArrayCollection == [(short)1, (short)2, (short)3]
            dto.integerArray == [1, 2, 3] as Integer[]
            dto.integerPrimitiveArray == [1, 2, 3] as int[]
            dto.integerArrayCollection == [1, 2, 3]
            dto.longArray == [1, 2, 3] as Long[]
            dto.longPrimitiveArray == [1, 2, 3] as long[]
            dto.longArrayCollection == [1L, 2L, 3L]
            dto.floatArray == [1, 2, 3] as Float[]
            dto.floatPrimitiveArray == [1, 2, 3] as float[]
            dto.floatArrayCollection == [1f, 2f, 3f]
            dto.doubleArray == [1, 2, 3] as Double[]
            dto.doublePrimitiveArray == [1, 2, 3] as double[]
            dto.doubleArrayCollection == [1d, 2d, 3d]
            dto.characterArray == ['a', 'b', 'c'] as Character[]
            dto.characterPrimitiveArray == ['a', 'b', 'c'] as char[]
            dto.characterArrayCollection.toArray() == ['a', 'b', 'c'] as Character[]
            dto.booleanArray == [true, false, true, false] as Boolean[]
            dto.booleanPrimitiveArray == [true, false, true, false] as boolean[]
            dto.booleanArrayCollection == [true, false, true, false]
        when:
            entity.stringArray = ["ABC", "123", "XYZ"]
            entity.stringArrayCollection = ["ABC", "123", "XYZ"]
            entity.shortArray = [3, 2, 1]
            entity.shortPrimitiveArray = [3, 2, 1]
            entity.shortArrayCollection = [(short)3, (short)2, (short)1]
            entity.integerArray = [3, 2, 1]
            entity.integerPrimitiveArray = [3, 2, 1]
            entity.integerArrayCollection = [3, 2, 1]
            entity.longArray = [3, 2, 1]
            entity.longPrimitiveArray = [3, 2, 1]
            entity.longArrayCollection = [3L, 2L, 1L]
            entity.floatArray = [3, 2, 1]
            entity.floatPrimitiveArray = [3, 2, 1]
            entity.floatArrayCollection = [3f, 2f, 1f]
            entity.doubleArray = [3, 2, 1]
            entity.doublePrimitiveArray = [3, 2, 1]
            entity.doubleArrayCollection = [3d, 2d, 1d]
            entity.characterArray = ['c', 'b', 'a'] as char[]
            entity.characterPrimitiveArray = ['c', 'b', 'a'] as char[]
            entity.characterArrayCollection = ['c', 'b', 'a'] as char[]
            entity.booleanArray = [false, false, true, true]
            entity.booleanPrimitiveArray = [false, true, true, false]
            entity.booleanArrayCollection = [false, false, true, false]
            arraysEntityRepository.update(entity)
            entityStored = arraysEntityRepository.findById(entity.someId).get()
        then:
            entityStored == entity
        when:
            arraysEntityRepository.update(entityStored.someId,
                    ["111", "222", "333"] as String[],
                    ["AAA"],
                    [1, 2, 3, 4, 5] as Short[],
                    [100] as short[],
                    [], null, null, null
            )
            entityStored = arraysEntityRepository.findById(entity.someId).get()
        then:
            entityStored.stringArray == ["111", "222", "333"] as String[]
            entityStored.stringArrayCollection == ["AAA"]
            entityStored.shortArray == [1, 2, 3, 4, 5] as Short[]
            entityStored.shortPrimitiveArray == [100]  as short[]
            entityStored.shortArrayCollection == []
            entityStored.integerArray == null
            entityStored.integerPrimitiveArray == null
            entityStored.integerArrayCollection == null
    }

    void cleanup() {
        arraysEntityRepository?.deleteAll()
    }
}
