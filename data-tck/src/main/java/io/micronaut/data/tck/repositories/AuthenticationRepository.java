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
package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Authentication;
import io.micronaut.data.tck.entities.Authentication_;
import io.micronaut.data.tck.entities.Device;
import io.micronaut.data.tck.entities.Device_;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

public interface AuthenticationRepository extends CrudRepository<Authentication, Long>, JpaSpecificationExecutor<Authentication> {
    class Specification {
        public static PredicateSpecification<Authentication> withDeviceName(String deviceName) {
            return (root, cb) -> {
                Join<Authentication, Device> device = root.join(Authentication_.device, JoinType.RIGHT);
                return cb.equal(device.get(Device_.name), deviceName);
            };
        }
    }
}
