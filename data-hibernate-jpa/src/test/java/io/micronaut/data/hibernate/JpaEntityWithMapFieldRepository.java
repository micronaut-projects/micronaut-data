package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.hibernate.entities.EntityWithMapField;
import io.micronaut.data.hibernate.entities.EntityWithMapField_;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.SetJoin;

@Repository
public interface JpaEntityWithMapFieldRepository extends JpaRepository<EntityWithMapField, Long>, JpaSpecificationExecutor<EntityWithMapField> {

    final class Specification {
        public static PredicateSpecification<EntityWithMapField> propertyEquals(String key, String value) {
            return (root, cb) -> {
                MapJoin<EntityWithMapField, String, String> props = root.join(EntityWithMapField_.properties);
                root.fetch(EntityWithMapField_.properties);
                return cb.and(
                    cb.equal(props.key(), key),
                    cb.equal(props.value(), value)
                );
            };
        }

        public static PredicateSpecification<EntityWithMapField> hasTagInList(String tag) {
            return (root, cb) -> {
                root.fetch(EntityWithMapField_.tagsList, JoinType.LEFT);
                ListJoin<EntityWithMapField, String> tags = root.join(EntityWithMapField_.tagsList, JoinType.INNER);
                return cb.equal(tags, tag);
            };
        }

        public static PredicateSpecification<EntityWithMapField> hasTagInSet(String tag) {
            return (root, cb) -> {
                root.fetch(EntityWithMapField_.tagsSet, JoinType.LEFT);
                SetJoin<EntityWithMapField, String> tags = root.join(EntityWithMapField_.tagsSet, JoinType.INNER);
                return cb.equal(tags, tag);
            };
        }
    }
}
