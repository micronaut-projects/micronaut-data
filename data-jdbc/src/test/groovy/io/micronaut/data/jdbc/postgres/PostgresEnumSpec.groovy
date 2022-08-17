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
package io.micronaut.data.jdbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.convert.JdbcConversionContext
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.convert.AttributeConverter
import io.micronaut.data.repository.PageableRepository
import io.micronaut.transaction.jdbc.DelegatingDataSource
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Array
import java.sql.Connection

class PostgresEnumsSpec extends Specification implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Override
    Dialect dialect() {
        return Dialect.POSTGRES
    }

    def "test native enums"() {
        given:
            def repo = context.getBean(EnuRepo)
            def e1 = repo.save(new EnuEntity(null, "ABC", Happiness.ecstatic))
            def e2 = repo.save(new EnuEntity(null, "XYZ1", Happiness.happy))
            def e3 = repo.save(new EnuEntity(null, "XYZ2", Happiness.happy))
        when:
            def e = repo.findById(e1.id).get()
        then:
            e.happiness == Happiness.ecstatic
            repo.findByName("ABC").id == e.id
            repo.findByHappiness(Happiness.ecstatic).id == e.id
            repo.findByHappinessIn([Happiness.happy, Happiness.very_happy]).size() == 2
            repo.findHappinessByHappinessIn([Happiness.happy, Happiness.very_happy]) == [Happiness.happy, Happiness.happy]
            repo.findByHappinessAsArray(Happiness.happy, Happiness.very_happy).size() == 2
            repo.findHappinessByHappinessAsArray(Happiness.happy, Happiness.very_happy) == [Happiness.happy, Happiness.happy]
        when:
            repo.update(e1.id, Happiness.very_happy)
            e = repo.findById(e1.id).get()
        then:
            e.happiness == Happiness.very_happy
    }

}

@MappedEntity("pg_enumz")
class EnuEntity {
    @Id
    @GeneratedValue
    Long id
    String name
    @MappedProperty(definition = "happiness", type = DataType.OBJECT)
    Happiness happiness

    EnuEntity(Long id, String name, Happiness happiness) {
        this.id = id
        this.name = name
        this.happiness = happiness
    }
}

enum Happiness {
    happy, very_happy, ecstatic
}

@JdbcRepository(dialect = Dialect.POSTGRES)
interface EnuRepo extends PageableRepository<EnuEntity, Long> {

    EnuEntity findByName(String name)

    EnuEntity findByHappiness(Happiness val)

    void update(@Id Long id, Happiness happiness)

    Collection<EnuEntity> findByHappinessIn(Collection<Happiness> vals)

    Collection<Happiness> findHappinessByHappinessIn(Collection<Happiness> vals)

    @Query("select * from pg_enumz where happiness = ANY (:vals)")
    Collection<EnuEntity> findByHappinessAsArray(@TypeDef(type = DataType.OBJECT, converter = ArraySetter.class) Happiness... vals)

    @Query("select happiness from pg_enumz where happiness = ANY (:vals)")
    Collection<Happiness> findHappinessByHappinessAsArray(@TypeDef(type = DataType.OBJECT, converter = ArraySetter.class) Happiness... vals)

}

@Singleton
class ArraySetter implements AttributeConverter<Object, Array> {

    @Override
    Array convertToPersistedValue(Object entityValue, ConversionContext context) {
        return (context as JdbcConversionContext).connection.createArrayOf("happiness", entityValue)
    }

    @Override
    Object convertToEntityValue(Array persistedValue, ConversionContext context) {
        throw new IllegalStateException("Not supported!")
    }
}
