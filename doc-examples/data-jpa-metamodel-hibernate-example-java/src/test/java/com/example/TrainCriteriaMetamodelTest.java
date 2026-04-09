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
package com.example;

import com.example.repository.TrainRepository;
import io.micronaut.entities.Train;
import io.micronaut.entities.Train_;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class TrainCriteriaMetamodelTest {

    final TrainRepository trainRepository;
    final EntityManager entityManager;

    public TrainCriteriaMetamodelTest(TrainRepository trainRepository,
                                      EntityManager entityManager) {
        this.trainRepository = trainRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canBuildCriteriaQueryUsingGeneratedStaticMetamodel_filterByStringAndBoxedNumber() {
        Train t1 = new Train(
            "Night Express",
            "NE-1",
            500,
            160.5,
            true,
            LocalDateTime.of(2026, 1, 10, 20, 15),
            Instant.parse("2026-01-01T00:00:00Z"),
            LocalDate.of(2026, 1, 10),
            LocalTime.of(20, 15));

        Train t2 = new Train(
            "Local Shuttle",
            "LS-9",
            120,
            80.0,
            false,
            LocalDateTime.of(2026, 1, 11, 9, 0),
            Instant.parse("2026-01-02T00:00:00Z"),
            LocalDate.of(2026, 1, 11),
            LocalTime.of(9, 0));

        trainRepository.saveAll(List.of(t1, t2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Train> cq = cb.createQuery(Train.class);
        Root<Train> root = cq.from(Train.class);

        cq.select(root)
            .where(cb.and(
                cb.equal(root.get(Train_.model), "NE-1"),
                cb.greaterThan(root.get(Train_.capacity), 300)
            ));

        List<Train> result = entityManager.createQuery(cq).getResultList();

        assertEquals(1, result.size());
        assertEquals("Night Express", result.getFirst().getName());
        assertEquals(Integer.valueOf(500), result.getFirst().getCapacity());
    }

    @Test
    void canFilterByBooleanAndDouble_usingStaticMetamodel() {
        Train t1 = new Train(
            "A", "M1", 100, 90.0, true,
            LocalDateTime.of(2026, 2, 1, 8, 0),
            Instant.parse("2026-02-01T00:00:00Z"),
            LocalDate.of(2026, 2, 1),
            LocalTime.of(8, 0)
        );
        Train t2 = new Train(
            "B", "M2", 200, 150.0, false,
            LocalDateTime.of(2026, 2, 2, 8, 0),
            Instant.parse("2026-02-02T00:00:00Z"),
            LocalDate.of(2026, 2, 2),
            LocalTime.of(8, 0)
        );

        trainRepository.saveAll(List.of(t1, t2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Train> cq = cb.createQuery(Train.class);
        Root<Train> root = cq.from(Train.class);

        cq.select(root)
            .where(cb.and(
                cb.isTrue(root.get(Train_.electric)),
                cb.lessThan(root.get(Train_.speed), 100.0)
            ));

        List<Train> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("M1", result.getFirst().getModel());
    }

    @Test
    void canFilterByLocalDateTime_usingStaticMetamodel() {
        Train early = new Train(
            "Early", "E1", 50, 60.0, true,
            LocalDateTime.of(2026, 3, 1, 6, 30),
            Instant.parse("2026-03-01T00:00:00Z"),
            LocalDate.of(2026, 3, 1),
            LocalTime.of(6, 30)
        );
        Train late = new Train(
            "Late", "L1", 50, 60.0, true,
            LocalDateTime.of(2026, 3, 1, 18, 45),
            Instant.parse("2026-03-01T00:00:01Z"),
            LocalDate.of(2026, 3, 1),
            LocalTime.of(18, 45)
        );

        trainRepository.saveAll(List.of(early, late));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Train> cq = cb.createQuery(Train.class);
        Root<Train> root = cq.from(Train.class);

        cq.select(root)
            .where(cb.greaterThan(root.get(Train_.departureTime), LocalDateTime.of(2026, 3, 1, 12, 0)))
            .orderBy(cb.asc(root.get(Train_.departureTime)));

        List<Train> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("Late", result.getFirst().getName());
    }

    @Test
    void canFilterByInstant_usingStaticMetamodel() {
        Train t1 = new Train(
            "T1", "I1", 1, 1.0, true,
            LocalDateTime.of(2026, 4, 1, 10, 0),
            Instant.parse("2026-04-01T00:00:00Z"),
            LocalDate.of(2026, 4, 1),
            LocalTime.of(10, 0)
        );
        Train t2 = new Train(
            "T2", "I2", 1, 1.0, true,
            LocalDateTime.of(2026, 4, 1, 10, 0),
            Instant.parse("2026-04-02T00:00:00Z"),
            LocalDate.of(2026, 4, 1),
            LocalTime.of(10, 0)
        );

        trainRepository.saveAll(List.of(t1, t2));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Train> cq = cb.createQuery(Train.class);
        Root<Train> root = cq.from(Train.class);

        cq.select(root)
            .where(cb.greaterThan(root.get(Train_.createdAt), Instant.parse("2026-04-01T12:00:00Z")));

        List<Train> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("I2", result.getFirst().getModel());
    }

    @Test
    void canFilterByLocalDate_andLocalTime_usingStaticMetamodel() {
        Train morning = new Train(
            "Morning", "DT1", 10, 10.0, true,
            LocalDateTime.of(2026, 5, 10, 9, 15),
            Instant.parse("2026-05-01T00:00:00Z"),
            LocalDate.of(2026, 5, 10),
            LocalTime.of(9, 15)
        );
        Train evening = new Train(
            "Evening", "DT2", 10, 10.0, true,
            LocalDateTime.of(2026, 5, 10, 18, 0),
            Instant.parse("2026-05-01T00:00:00Z"),
            LocalDate.of(2026, 5, 10),
            LocalTime.of(18, 0)
        );

        trainRepository.saveAll(List.of(morning, evening));

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Train> cq = cb.createQuery(Train.class);
        Root<Train> root = cq.from(Train.class);

        cq.select(root)
            .where(cb.and(
                cb.equal(root.get(Train_.departureDate), LocalDate.of(2026, 5, 10)),
                cb.greaterThan(root.get(Train_.departureTimeOnly), LocalTime.of(12, 0))
            ));

        List<Train> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("Evening", result.getFirst().getName());
    }

}
