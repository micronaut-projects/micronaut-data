/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Food;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FoodRepository extends CrudRepository<Food, UUID>, JpaSpecificationExecutor<Food> {
    @NonNull
    @Override
    @Join("meal")
    Optional<Food> findById(@NonNull @NotNull UUID uuid);

    @NonNull
    @Join(value = "alternativeMeal", type = Join.Type.LEFT_FETCH)
    Food searchById(@NonNull @NotNull UUID uuid);

    @Join("meal")
    Food findByMealMidForUpdate(Long mid);

    List<Food> findAllByKeyOrderByLongName(String key);

    class Specifications {

        private Specifications() {}

        public static PredicateSpecification<Food> keyEquals(String key) {
            return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("key"), key);
        }
    }
}
