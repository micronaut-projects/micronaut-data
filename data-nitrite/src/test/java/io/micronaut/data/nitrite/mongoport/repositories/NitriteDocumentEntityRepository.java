package io.micronaut.data.nitrite.mongoport.repositories;

import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.mongoport.entities.NitriteDocument;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

import java.util.List;

@NitriteRepository
public interface NitriteDocumentEntityRepository extends CrudRepository<NitriteDocument, String>, JpaSpecificationExecutor<NitriteDocument> {

    List<NitriteDocument> findByTagsArrayContains(String tag);

    List<NitriteDocument> findByTagsArrayContains(List<String> tags);

    class Specifications {
        private Specifications() {}

        public static PredicateSpecification<NitriteDocument> tagsArrayContains(String... tags) {
            return (root, criteriaBuilder) -> ((PersistentEntityCriteriaBuilder) criteriaBuilder).arrayContains(root.get("tags"), criteriaBuilder.literal(tags));
        }
    }
}
