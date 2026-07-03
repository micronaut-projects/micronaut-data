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

package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.EmployeeId;
import io.micronaut.data.tck.entities.EmployeeId_;
import io.micronaut.data.tck.entities.EmployeeMixedAccessEmbeddedId;
import io.micronaut.data.tck.entities.EmployeeMixedAccessEmbeddedId_;

public interface EmployeeMixedAccessEmbeddedIdRepository extends CrudRepository<EmployeeMixedAccessEmbeddedId, EmployeeId>, JpaSpecificationExecutor<EmployeeMixedAccessEmbeddedId> {

    class Specification {

        public static PredicateSpecification<EmployeeMixedAccessEmbeddedId> embeddedIdEquals(Long id, String number) {
            return (root, cb) -> cb.and(
                cb.equal(root.get(EmployeeMixedAccessEmbeddedId_.id).get(EmployeeId_.id), id),
                cb.equal(root.get(EmployeeMixedAccessEmbeddedId_.id).get(EmployeeId_.number), number)
            );
        }

        public static PredicateSpecification<EmployeeMixedAccessEmbeddedId> embeddedIdIdEquals(Long id) {
            return (root, cb) -> cb.equal(root.get(EmployeeMixedAccessEmbeddedId_.id).get(EmployeeId_.id), id);
        }

        // This throws Null pointer exception because jdbcRepository doesn't take properties without accessors annotated with access type field into consideration.
//        public static PredicateSpecification<EmployeeMixedAccessEmbeddedId> fieldAnnotatedEquals(String s) {
//            return (root, cb) -> cb.equal(root.get(EmployeeMixedAccessEmbeddedId_.fieldAnnotated), s);
//        }
    }

}
