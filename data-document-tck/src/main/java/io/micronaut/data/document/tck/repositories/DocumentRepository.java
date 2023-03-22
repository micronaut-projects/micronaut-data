package io.micronaut.data.document.tck.repositories;

import io.micronaut.data.document.tck.entities.Document;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

public interface DocumentRepository extends CrudRepository<Document, String>, JpaSpecificationExecutor<Document> {

    class Specifications {

        private Specifications() { }

        public static PredicateSpecification<Document> tagsArrayContains(String... tags) {
            return (root, criteriaBuilder) -> ((PersistentEntityCriteriaBuilder) criteriaBuilder).arrayContains(root.get("tags"), criteriaBuilder.literal(tags));
        }
    }
}
