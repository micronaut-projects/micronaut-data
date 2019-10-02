/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.annotation.Property
import io.micronaut.data.tck.entities.Food
import io.micronaut.data.tck.entities.Meal
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.validation.ConstraintViolationException

@MicronautTest
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.dialect", value = "H2")
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

        Food food = new Food("test", 100, 100, meal)
        food = foodRepository.save(food)
        def retrieved = foodRepository.findById(food.id).orElse(null)

        expect:
        retrieved.key == food.key
        retrieved.carbohydrates == food.carbohydrates
    }

    void "test save invalid objects"() {
        when:"An invalid object is saved"
        mealRepository.save(new Meal(10000))

        then:"An exception occurs"
        def e = thrown(ConstraintViolationException)
        e.message.contains('currentBloodGlucose: must be less than or equal to 999')
    }
}
