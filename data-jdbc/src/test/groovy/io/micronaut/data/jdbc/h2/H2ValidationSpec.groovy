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
package io.micronaut.data.jdbc.h2


import io.micronaut.data.tck.entities.Food
import io.micronaut.data.tck.entities.Meal
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.validation.ConstraintViolationException

@MicronautTest
@H2DBProperties
class H2ValidationSpec extends Specification {

    @Inject
    @Shared
    H2MealRepository mealRepository

    @Inject
    @Shared
    H2FoodRepository foodRepository

    void "test save valid objects"() {
        given:
        Meal meal = new Meal(100)
        mealRepository.save(meal)
        Meal alternativeMeal = new Meal(50)
        mealRepository.save(alternativeMeal)

        Food food = new Food("test", 100, 100, meal)
        food.alternativeMeal = alternativeMeal
        food = foodRepository.save(food)
        def retrieved = foodRepository.findById(food.fid).orElse(null)

        expect:
        retrieved.key == food.key
        retrieved.carbohydrates == food.carbohydrates
        mealRepository.searchById(meal.mid).foods.size() == 1
        foodRepository.searchById(food.fid)
    }

    void "test save invalid objects"() {
        when:"An invalid object is saved"
        mealRepository.save(new Meal(10000))

        then:"An exception occurs"
        def e = thrown(ConstraintViolationException)
        e.message.contains('currentBloodGlucose: must be less than or equal to 999')
    }

    void "test update invalid objects"() {
        when:
        Meal meal = new Meal(100)
        mealRepository.save(meal)

        Food food = new Food("test", 100, 100, meal)
        food = foodRepository.save(food)
        def retrieved = foodRepository.findById(food.fid).orElse(null)

        then:
        retrieved.key == food.key
        retrieved.carbohydrates == food.carbohydrates

        when:"An invalid value is updated"
        retrieved.meal.currentBloodGlucose = 10000
        mealRepository.update(retrieved.meal)

        then:"An exception occurs"
        def e = thrown(ConstraintViolationException)
        e.message.contains('currentBloodGlucose: must be less than or equal to 999')

        when:"A valid value is set"
        retrieved.meal.currentBloodGlucose = 101
        foodRepository.update(retrieved)
        retrieved = foodRepository.findById(food.fid).orElse(null)

        then:"it is saved and cascaded to correctly"
        retrieved.meal.currentBloodGlucose == 101

    }
}
