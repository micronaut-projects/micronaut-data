package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Client;
import io.micronaut.data.tck.entities.ClientCategory_;
import io.micronaut.data.tck.entities.Client_;
import jakarta.persistence.criteria.JoinType;

public interface ClientRepository extends CrudRepository<Client, Long>, JpaSpecificationExecutor<Client> {
    class Specifications {
        public static PredicateSpecification<Client> tierEquals(Client.Tier tier) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Client_.tier), tier);
        }

        public static PredicateSpecification<Client> nameEquals(String name) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Client_.name), name);
        }

        public static PredicateSpecification<Client> withCategoryListName(String name) {
            return (root, criteriaBuilder) -> {
                var category = root.join(Client_.categoriesList, JoinType.RIGHT);
                return criteriaBuilder.equal(category.get(ClientCategory_.name), name);
            };
        }

        public static PredicateSpecification<Client> withCategorySetName(String name) {
            return (root, criteriaBuilder) -> {
                var category = root.join(Client_.categoriesSet, JoinType.INNER);
                return criteriaBuilder.equal(category.get(ClientCategory_.name), name);
            };
        }

        public static PredicateSpecification<Client> mainCategoryIdEquals(Long id) {
            return (root, criteriaBuilder) -> {
                var category = root.join(Client_.mainCategory, JoinType.INNER);
                return criteriaBuilder.equal(category.get(ClientCategory_.id), id);
            };
        }

        public static PredicateSpecification<Client> mainCategoryNameEquals(String name) {
            return (root, criteriaBuilder) -> {
                var category = root.join(Client_.mainCategory, JoinType.INNER);
                return criteriaBuilder.equal(category.get(ClientCategory_.name), name);
            };
        }

//    public static  PredicateSpecification<Client> withEntryEquals(Map.Entry<String, String> entry) {
//        return ((root, criteriaBuilder) -> {
//            var propsJoin = root.join(Client_.properties);
//            return criteriaBuilder.equal(propsJoin.entry(), entry);
//        });
//    }
    }
}
