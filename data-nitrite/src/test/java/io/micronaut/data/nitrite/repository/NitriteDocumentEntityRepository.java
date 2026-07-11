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
package io.micronaut.data.nitrite.repository;

import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder;
import io.micronaut.data.nitrite.annotation.NitriteRepository;
import io.micronaut.data.nitrite.model.NitriteDocument;
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
