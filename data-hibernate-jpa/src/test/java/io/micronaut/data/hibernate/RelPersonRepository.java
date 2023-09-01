package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.RepositoryConfiguration;
import io.micronaut.data.hibernate.entities.RelPerson;
import io.micronaut.data.jpa.repository.JpaRepository;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.SetJoin;

import java.util.List;

@Repository
@RepositoryConfiguration(queryBuilder = JpaQueryBuilder.class)
public interface RelPersonRepository extends JpaRepository<RelPerson, Long>, JpaSpecificationExecutor<RelPerson> {

    @Override
    @Transactional
    List<RelPerson> findAll(PredicateSpecification<RelPerson> spec);

    class Specifications {

        public static PredicateSpecification<RelPerson> findRelPersonByParentAndFriends(Long parentId, List<Long> friendsId) {
            return (root, criteriaBuilder) -> {
                Join parentJoin = root.join("parent");
                SetJoin friendsJoin = root.joinSet("friends");
                return criteriaBuilder.and(criteriaBuilder.equal(parentJoin.get("id"), parentId), friendsJoin.get("id").in(friendsId));
            };
        }

        public static PredicateSpecification<RelPerson> findRelPersonByChildren(List<Long> childrenId) {
            return (root, criteriaBuilder) -> {
                SetJoin children = root.joinSet("children");
                return children.get("id").in(childrenId);
            };
        }
    }
}
