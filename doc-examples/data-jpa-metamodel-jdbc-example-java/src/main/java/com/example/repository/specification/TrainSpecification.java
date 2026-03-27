/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.example.repository.specification;

import com.example.Train;
import com.example.Train_;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TrainSpecification {
    public static PredicateSpecification<Train> trainModelEqual(String model) {
        return (root, cb) -> cb.equal(root.get(Train_.model), model);
    }

    public static PredicateSpecification<Train> capacityBiggerThan(Integer capacity) {
        return (root, cb) -> cb.greaterThan(root.get(Train_.capacity), capacity);
    }

    public static PredicateSpecification<Train> isElectric() {
        return (root, cb) -> cb.isTrue(root.get(Train_.electric));
    }

    public static PredicateSpecification<Train> speedLessThan(Double speed) {
        return (root, cb) -> cb.lessThan(root.get(Train_.speed), speed);
    }

    public static PredicateSpecification<Train> departureTimeGreaterThan(LocalDateTime departureTime) {
        return (root, cb) -> cb.greaterThan(root.get(Train_.departureTime), departureTime);
    }

    public static PredicateSpecification<Train> createdAtGreaterThan(Instant instant) {
        return (root, cb) -> cb.greaterThan(root.get(Train_.createdAt), instant);
    }

    public static PredicateSpecification<Train> departureDateEqual(LocalDate localDate) {
        return ((root, criteriaBuilder) -> criteriaBuilder.equal(root.get(Train_.departureDate), localDate));
    }

    public static PredicateSpecification<Train> departureTimeOnlyGreaterThan(LocalTime localTime) {
        return ((root, criteriaBuilder) -> criteriaBuilder.greaterThan(root.get(Train_.departureTimeOnly), localTime));
    }

}
