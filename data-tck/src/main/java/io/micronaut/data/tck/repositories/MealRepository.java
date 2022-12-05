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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Meal;

public interface MealRepository extends CrudRepository<Meal, Long> {

    @Nullable
    @Join("foods")
    Meal searchById(Long uuid);

    Meal findByIdForUpdate(Long id);

    @Join("foods")
    Meal searchByIdForUpdate(Long id);

    Iterable<Meal> findAllForUpdate();

    Iterable<Meal> findAllByCurrentBloodGlucoseLessThan(int currentBloodGlucose);

    Iterable<Meal> findAllByCurrentBloodGlucoseLessThanForUpdate(int currentBloodGlucose);

    Iterable<Meal> findByFoodsPortionGramsGreaterThan(int portionGrams);

    Iterable<Meal> findByFoodsPortionGramsGreaterThanForUpdate(int portionGrams);
}
