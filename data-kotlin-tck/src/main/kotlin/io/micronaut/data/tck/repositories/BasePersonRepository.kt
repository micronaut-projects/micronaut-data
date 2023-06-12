/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.tck.repositories

import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.PageableRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.tck.entities.Person
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Root
import jakarta.transaction.Transactional
import java.util.*

interface BasePersonRepository : CrudRepository<Person, Long>, PageableRepository<Person, Long>,
    JpaSpecificationExecutor<Person> {

    @Transactional(Transactional.TxType.MANDATORY)
    fun queryById(id: Long): Optional<Person>

    object Specifications {
        fun nameEquals(name: String?): PredicateSpecification<Person?> {
            return PredicateSpecification { root: Root<Person?>, criteriaBuilder: CriteriaBuilder ->
                criteriaBuilder.equal(
                    root.get<Any>("name"),
                    name
                )
            }
        }

        fun nameEqualsCaseInsensitive(name: String): PredicateSpecification<Person?> {
            return PredicateSpecification<Person?> { root: Root<Person?>, criteriaBuilder: CriteriaBuilder ->
                criteriaBuilder.equal(
                    criteriaBuilder.lower(root.get<String>("name")),
                    name.lowercase(Locale.getDefault())
                )
            }
        }

        fun idsIn(vararg ids: Long?): PredicateSpecification<Person?> {
            return PredicateSpecification { root: Root<Person?>, criteriaBuilder: CriteriaBuilder? ->
                root.get<Any>("id").`in`(
                    Arrays.asList(*ids)
                )
            }
        }
    }
}
