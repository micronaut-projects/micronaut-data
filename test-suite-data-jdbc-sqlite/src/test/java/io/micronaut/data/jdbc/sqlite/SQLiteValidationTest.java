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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.tck.entities.Food;
import io.micronaut.data.tck.entities.Meal;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties
class SQLiteValidationTest {

    @Inject
    SQLiteMealRepository mealRepository;

    @Inject
    SQLiteFoodRepository foodRepository;

    @Test
    void testSaveValidObjects() {
        Meal meal = new Meal(100);
        mealRepository.save(meal);
        Meal alternativeMeal = new Meal(50);
        mealRepository.save(alternativeMeal);

        Food food = new Food("test", 100, 100, meal);
        food.setAlternativeMeal(alternativeMeal);
        food = foodRepository.save(food);
        Food retrieved = foodRepository.findById(food.getFid()).orElse(null);

        assertNotNull(retrieved);
        assertEquals(food.getKey(), retrieved.getKey());
        assertEquals(food.getCarbohydrates(), retrieved.getCarbohydrates());
        assertEquals(1, mealRepository.searchById(meal.getMid()).getFoods().size());
        var foodId = food.getFid();
        assertDoesNotThrow(() -> foodRepository.searchById(foodId));
    }

    @Test
    void testSaveInvalidObjects() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> mealRepository.save(new Meal(10000)));
        assertTrue(e.getMessage().contains("currentBloodGlucose: must be less than or equal to 999"));
    }

    @Test
    void testUpdateInvalidObjects() {
        Meal meal = new Meal(100);
        mealRepository.save(meal);

        Food food = new Food("test", 100, 100, meal);
        food = foodRepository.save(food);
        Food retrieved = foodRepository.findById(food.getFid()).orElse(null);

        assertNotNull(retrieved);
        assertEquals(food.getKey(), retrieved.getKey());
        assertEquals(food.getCarbohydrates(), retrieved.getCarbohydrates());

        retrieved.getMeal().setCurrentBloodGlucose(10000);
        var invalidMeal = retrieved.getMeal();
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> mealRepository.update(invalidMeal));
        assertTrue(e.getMessage().contains("currentBloodGlucose: must be less than or equal to 999"));

        retrieved.getMeal().setCurrentBloodGlucose(101);
        foodRepository.update(retrieved);
        retrieved = foodRepository.findById(food.getFid()).orElse(null);

        assertNotNull(retrieved);
        assertEquals(101, retrieved.getMeal().getCurrentBloodGlucose());
    }
}
