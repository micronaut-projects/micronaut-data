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
package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder;
import io.micronaut.data.tck.entities.Jurisdiction;
import io.micronaut.data.tck.entities.Registration;
import io.micronaut.data.tck.entities.Vehicle;

@JdbcRepository(dialect = Dialect.H2)
public interface H2VehicleRepository extends CrudRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    Registration findFirstRegistrationById(Long id);

    Registration findSecondRegistrationById(Long id);

    Jurisdiction findFirstRegistrationJurisdictionById(Long id);

    Jurisdiction findSecondRegistrationJurisdictionById(Long id);

    class Specifications {
        static CriteriaQueryBuilder<Registration> findFirstRegistrationById(Long id) {
            return criteriaBuilder -> {
                var query = criteriaBuilder.createQuery(Registration.class);
                var root = query.from(Vehicle.class);
                query.select(root.get("firstRegistration")).where(criteriaBuilder.equal(root.get("id"), id));
                return query;
            };
        }
    }
}
