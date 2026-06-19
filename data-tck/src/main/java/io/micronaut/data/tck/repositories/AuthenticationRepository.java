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
