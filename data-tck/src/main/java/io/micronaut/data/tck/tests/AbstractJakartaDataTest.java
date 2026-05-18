/*

 * Copyright 2017-2025 original authors
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
package io.micronaut.data.tck.tests;

import io.micronaut.data.tck.entities.Train;
import io.micronaut.data.tck.entities.TrainCZ;
import io.micronaut.data.tck.entities.TrainCZProjection;
import io.micronaut.data.tck.entities.TrainManufacturer;
import io.micronaut.data.tck.entities.TrainNameCapacityDto;
import io.micronaut.data.tck.entities.TrainNameModelDto;
import io.micronaut.data.tck.entities.TrainSpecs;
import io.micronaut.data.tck.entities._Train;
import io.micronaut.data.tck.entities._TrainManufacturer;
import io.micronaut.data.tck.entities._TrainSpecs;
import io.micronaut.data.tck.repositories.TrainRepository;
import io.micronaut.data.tck.repositories.TrainsRepository;
import io.micronaut.data.tck.services.JakartaDataTrainEventListener;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.constraint.EqualTo;
import jakarta.data.expression.TemporalExpression;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restrict;
import jakarta.data.restrict.Restriction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractJakartaDataTest {

    @Inject
    protected TrainRepository trainRepository;

    @Inject
    protected TrainsRepository trainsRepository;

    @Inject
    protected MockedDateTimeProvider dateTimeProvider;

    @BeforeEach
    public void setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // Create test data
        LocalDateTime baseTime = LocalDateTime.of(2023, 1, 1, 8, 0);
        LocalDate baseDate = baseTime.toLocalDate();
        LocalTime baseTimeOnly = baseTime.toLocalTime();
        Instant baseInstant = Instant.ofEpochSecond(1672531200); // 2023-01-01T00:00:00Z

        // Create companies
        TrainManufacturer siemens = new TrainManufacturer("Siemens", "Germany", 1847);
        TrainManufacturer alstom = new TrainManufacturer("Alstom", "France", 1928);
        TrainManufacturer bombardier = new TrainManufacturer("Bombardier", "Canada", 1942);

        // Create trains with embedded specs and manufacturer associations
        Train express1 = new Train("Express 1", "HighSpeed", 200, 300.0, true, baseTime, baseInstant);
        express1.setDepartureDate(baseDate);
        express1.setDepartureTimeOnly(baseTimeOnly);
        express1.setSpecs(new TrainSpecs("Electric", 500, 150.0));
        express1.setManufacturer(siemens);

        Train local1 = new Train("Local 1", "Standard", 150, 120.0, false, baseTime.plusHours(2), baseInstant.plusSeconds(7200));
        local1.setDepartureDate(baseDate);
        local1.setDepartureTimeOnly(baseTimeOnly.plusHours(2));
        local1.setSpecs(new TrainSpecs("Diesel", 300, 120.0));
        local1.setManufacturer(alstom);

        Train express2 = new Train("Express 2", "HighSpeed", 250, 350.0, true, baseTime.plusHours(4), baseInstant.plusSeconds(14400));
        express2.setDepartureDate(baseDate);
        express2.setDepartureTimeOnly(baseTimeOnly.plusHours(4));
        express2.setSpecs(new TrainSpecs("Electric", 600, 160.0));
        express2.setManufacturer(siemens);

        Train cargo1 = new Train("Cargo 1", "Freight", 50, 80.0, false, baseTime.plusHours(6), baseInstant.plusSeconds(21600));
        cargo1.setDepartureDate(baseDate);
        cargo1.setDepartureTimeOnly(baseTimeOnly.plusHours(6));
        cargo1.setSpecs(new TrainSpecs("Diesel", 1000, 200.0));
        cargo1.setManufacturer(bombardier);

        Train express3 = new Train("Express 3", "HighSpeed", 220, 320.0, true, baseTime.plusHours(8), baseInstant.plusSeconds(28800));
        express3.setDepartureDate(baseDate);
        express3.setDepartureTimeOnly(baseTimeOnly.plusHours(8));
        express3.setSpecs(new TrainSpecs("Electric", 550, 155.0));
        express3.setManufacturer(alstom);

        Train local2 = new Train("Local 2", "Standard", 180, 100.0, false, baseTime.plusHours(10), baseInstant.plusSeconds(36000));
        local2.setDepartureDate(baseDate);
        local2.setDepartureTimeOnly(baseTimeOnly.plusHours(10));
        local2.setSpecs(new TrainSpecs("Diesel", 350, 125.0));
        local2.setManufacturer(bombardier);

        trainRepository.saveAll(Arrays.asList(express1, local1, express2, cargo1, express3, local2));
    }

    @AfterEach
    public void cleanup() {
        trainRepository.deleteAll();
        TimeZone.setDefault(null);
    }

    @Inject
    private JakartaDataTrainEventListener trainEventListener;

    @Test
    public void testEvents() {
        trainEventListener.getEvents().clear();

        Train someTrain = new Train("TestTrain", "Model", 100, 200.0, true);
        someTrain.setSpecs(new TrainSpecs("TestTrain", 100, 200.0));
        trainRepository.save(someTrain);

        Assertions.assertEquals(2, trainEventListener.getEvents().size());
        Assertions.assertEquals(List.of("PreInsertEvent", "PostInsertEvent"), trainEventListener.getEvents());

        trainEventListener.getEvents().clear();

        Train myTrain = trainRepository.findByName("TestTrain").getFirst();
        myTrain.setName("Updated");
        trainRepository.update(myTrain);

        Assertions.assertEquals(2, trainEventListener.getEvents().size());
        Assertions.assertEquals(List.of("PreUpdateEvent", "PostUpdateEvent"), trainEventListener.getEvents());

        trainEventListener.getEvents().clear();

        trainRepository.delete(myTrain);

        Assertions.assertEquals(2, trainEventListener.getEvents().size());
        Assertions.assertEquals(List.of("PreDeleteEvent", "PostDeleteEvent"), trainEventListener.getEvents());
    }

    @Test
    public void testPrepend() {
        List<Train> trains = trainRepository.findTrains(_Train.name.prepend("The ").equalTo("The Express 1"));
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());
    }

    @Test
    public void testAppend() {
        List<Train> trains = trainRepository.findTrains(_Train.name.append(" Model").equalTo("Express 1 Model"));
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());
    }

    @Test
    public void testUnmatchable() {
        assertEquals(0, trainRepository.trains(Restrict.<Train>unrestricted().negate()).size());
    }

    @Test
    public void testSelect() {
        String model = trainsRepository.getModel("Express 1").orElseThrow();
        assertEquals("HighSpeed", model);
    }

    @Test
    public void testProjection() {
        Train express1 = trainRepository.findByName("Express 1").getFirst();
        TrainCZProjection trainCZ = trainRepository.projection(express1.getId());
        assertEquals("HighSpeed", trainCZ.model());
        assertEquals(300.0, trainCZ.rychlost());
        assertTrue(trainCZ.elektricky());
    }

    @Test
    public void testMethodProjection() {
        Train express1 = trainRepository.findByName("Express 1").getFirst();
        TrainCZ trainCZ = trainRepository.methodProjection(express1.getId());
        assertEquals("HighSpeed", trainCZ.model());
        assertEquals(300.0, trainCZ.rychlost());
        assertTrue(trainCZ.elektricky());
    }

    @Test
    public void testEqualToConstraint() {
        List<Train> trains = trainRepository.findByName("Express 1");
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());
    }

    @Test
    public void testFindByNameEqualsConstrain() {
        List<Train> trains = trainRepository.findByNameEqualsConstrain("Express 1");
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());
    }

    @Test
    public void testFindByNameEqualsConstrainValue() {
        List<Train> trains = trainRepository.findByNameEqualsConstrain(EqualTo.value("Express 1"));
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());
    }

    @Test
    public void testFindByNameEqualsConstrainAndRestriction() {
        List<Train> trains = trainRepository.findByNameEqualsConstrainWithRestriction(EqualTo.value("Express 1"), _Train.electric.equalTo(true));
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());

        trains = trainRepository.findByNameEqualsConstrainWithRestriction(EqualTo.value("Express 1"), _Train.electric.equalTo(false));
        assertEquals(0, trains.size());
    }

    @Test
    public void testFindByNameEqualsConstrainAndElectricConstraint() {
        List<Train> trains = trainRepository.findByNameEqualsConstrainAndElectricConstraint(EqualTo.value("Express 1"), EqualTo.value(true));
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());

        trains = trainRepository.findByNameEqualsConstrainAndElectricConstraint(EqualTo.value("Express 1"), EqualTo.value(false));
        assertEquals(0, trains.size());
    }

    @Test
    public void testFindByNameNotEqualsConstrain() {
        List<Train> trains = trainRepository.findByNameNotEqualsConstrain("Express 1");
        assertEquals(5, trains.size());
    }

    @Test
    public void testFindByCapacityAtLeast() {
        List<Train> trains = trainRepository.findByCapacityAtLeast(200);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() >= 200));
    }

    @Test
    public void testFindBySpeedAtMost() {
        List<Train> trains = trainRepository.findBySpeedAtMost(120.0);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getSpeed() <= 120.0));
    }

    @Test
    public void testFindByCapacityGreaterThanConstrain() {
        List<Train> trains = trainRepository.findByCapacityGreaterThanConstrain(200);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testFindByModelInConstrain() {
        List<Train> trains = trainRepository.findByModelInConstrain(Arrays.asList("HighSpeed", "Freight"));
        assertEquals(4, trains.size());
        trains.forEach(train ->
            assertTrue("HighSpeed".equals(train.getModel()) || "Freight".equals(train.getModel()))
        );
    }

    @Test
    public void testFindBySpeedLessThanConstrain() {
        List<Train> trains = trainRepository.findBySpeedLessThanConstrain(150.0);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getSpeed() < 150.0));
    }

    @Test
    public void testFindByNameLikeConstrain() {
        List<Train> trains = trainRepository.findByNameLikeConstrain("Express%");
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getName().startsWith("Express")));
    }

    @Test
    public void testFindByNameNotInConstrain() {
        List<Train> trains = trainRepository.findByNameNotInConstrain(Arrays.asList("Express 1", "Local 1"));
        assertEquals(4, trains.size());
        trains.forEach(train ->
            assertFalse("Express 1".equals(train.getName()) || "Local 1".equals(train.getName()))
        );
    }

    @Test
    public void testFindByModelNotLikeConstrain() {
        List<Train> trains = trainRepository.findByModelNotLikeConstrain("HighSpeed");
        assertEquals(3, trains.size());
        trains.forEach(train -> assertNotEquals("HighSpeed", train.getModel()));
    }

    @Test
    public void testRuntimeRestrictionsWithEqualTo() {
        Restriction<Train> restriction = _Train.name.equalTo("Express 1");
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());

        Train train = trainRepository.findTrain(restriction);
        assertEquals(trains.getFirst(), train);
    }

    @Test
    public void testNotEqualToConstraint() {
        List<Train> trains = trainRepository.findByModelNot("HighSpeed");
        assertEquals(3, trains.size());
        trains.forEach(train -> assertNotEquals("HighSpeed", train.getModel()));
    }

    @Test
    public void testRuntimeRestrictionsWithNotEqualTo() {
        Restriction<Train> restriction = _Train.model.notEqualTo("HighSpeed");
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertNotEquals("HighSpeed", train.getModel()));
    }

    @Test
    public void testGreaterThanConstraint() {
        List<Train> trains = trainRepository.findByCapacityGreaterThan(200);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testLessThanConstraint() {
        List<Train> trains = trainRepository.findBySpeedLessThan(150.0);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getSpeed() < 150.0));
    }

    @Test
    public void testRuntimeRestrictionsWithLessThan() {
        Restriction<Train> restriction = _Train.speed.lessThan(150.0);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getSpeed() < 150.0));
    }

    @Test
    public void testAtLeastConstraint() {
        List<Train> trains = trainRepository.findByCapacityGreaterThanEquals(200);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() >= 200));
    }

    @Test
    public void testRuntimeRestrictionsWithAtLeast() {
        Restriction<Train> restriction = _Train.capacity.greaterThanEqual(200);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() >= 200));
    }

    @Test
    public void testAtMostConstraint() {
        List<Train> trains = trainRepository.findBySpeedLessThanEquals(120.0);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getSpeed() <= 120.0));
    }

    @Test
    public void testBetweenConstraint() {
        List<Train> trains = trainRepository.findByCapacityBetween(180, 230);
        assertEquals(3, trains.size());
        trains.forEach(train -> {
            assertTrue(train.getCapacity() >= 180);
            assertTrue(train.getCapacity() <= 230);
        });
    }

    @Test
    public void testNotBetweenConstraint() {
        List<Train> trains = trainRepository.findBySpeedNotBetween(100.0, 300.0);
        assertEquals(3, trains.size());
        trains.forEach(train -> {
            assertTrue(train.getSpeed() <= 100.0 || train.getSpeed() >= 300.0);
        });
    }

    @Test
    public void testInConstraint() {
        List<Train> trains = trainRepository.findByModelIn(Arrays.asList("HighSpeed", "Freight"));
        assertEquals(4, trains.size());
        trains.forEach(train ->
            assertTrue("HighSpeed".equals(train.getModel()) || "Freight".equals(train.getModel()))
        );
    }

    @Test
    public void testNotInConstraint() {
        List<Train> trains = trainRepository.findByNameNotIn(Arrays.asList("Express 1", "Local 1"));
        assertEquals(4, trains.size());
        trains.forEach(train ->
            assertFalse("Express 1".equals(train.getName()) || "Local 1".equals(train.getName()))
        );
    }

    @Test
    public void testNullConstraint() {
        // Add a train with null model
        Train nullModelTrain = new Train("TestTrain", null, 100, 200.0, true);
        nullModelTrain.setSpecs(new TrainSpecs("TestTrain", 100, 200.0));
        trainRepository.save(nullModelTrain);

        List<Train> trains = trainRepository.findByModelIsNull();
        assertEquals(1, trains.size());
        assertNull(trains.get(0).getModel());

        // Cleanup
        trainRepository.delete(nullModelTrain);
    }

    @Test
    public void testNotNullConstraint() {
        List<Train> trains = trainRepository.findByModelIsNotNull();
        assertEquals(6, trains.size());
        trains.forEach(train -> assertNotNull(train.getModel()));
        // Add a train with null model
        Train nullModelTrain = new Train("TestTrain", null, 100, 200.0, true);
        nullModelTrain.setSpecs(new TrainSpecs("TestTrain", 100, 200.0));
        trainRepository.save(nullModelTrain);

        trains = trainRepository.findByModelIsNotNull();
        assertEquals(6, trains.size());
        trains.forEach(train -> assertNotNull(train.getModel()));

        // Cleanup
        trainRepository.delete(nullModelTrain);
    }

    @Test
    public void testLikeConstraint() {
        List<Train> trains = trainRepository.findByNameLike("Express%");
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getName().startsWith("Express")));
    }

    @Test
    public void testNotLikeConstraint() {
        List<Train> trains = trainRepository.findByModelNotLike("HighSpeed");
        assertEquals(3, trains.size());
        trains.forEach(train -> assertNotEquals("HighSpeed", train.getModel()));
    }

    @Test
    public void testRuntimeRestrictionsWithGreaterThan() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(200);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testRuntimeRestrictionsWithBetween() {
        Restriction<Train> restriction = _Train.speed.between(100.0, 300.0);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> {
            assertTrue(train.getSpeed() >= 100.0);
            assertTrue(train.getSpeed() <= 300.0);
        });
    }

    @Test
    public void testRuntimeRestrictionsWithIn() {
        Restriction<Train> restriction = _Train.model.in("HighSpeed", "Freight");
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(4, trains.size());
        trains.forEach(train ->
            assertTrue("HighSpeed".equals(train.getModel()) || "Freight".equals(train.getModel()))
        );
    }

    @Test
    public void testRuntimeRestrictionsWithContains() {
        List<Train> trains = trainRepository.findTrains(_Train.model.contains("High"));
        assertEquals(3, trains.size());
        trains.forEach(train ->
            assertTrue(train.getModel().contains("High"))
        );
    }

    @Test
    public void testRuntimeRestrictionsWithNotContains() {
        List<Train> trains = trainRepository.findTrains(_Train.name.notContains("Local"));
        assertEquals(4, trains.size());
        trains.forEach(train ->
            assertFalse(train.getName().contains("Local"))
        );
    }

    @Test
    public void testRuntimeRestrictionsWithStartsWith() {
        List<Train> trains = trainRepository.findTrains(_Train.name.startsWith("Express"));
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getName().startsWith("Express")));
    }

    @Test
    public void testRuntimeRestrictionsWithEndsWith() {
        List<Train> trains = trainRepository.findTrains(_Train.name.endsWith("1"));
        assertEquals(3, trains.size());
        assertEquals(3, trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getName().endsWith("1")).count());
        trains.forEach(train -> assertTrue(train.getName().endsWith("1")));
    }

    @Test
    public void testRuntimeRestrictionsWithLike() {
        Restriction<Train> restriction = _Train.name.like("Local%");
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getName().startsWith("Local")));
    }

    @Test
    public void testRuntimeRestrictionsWithNull() {
        // Add a train with null model
        Train nullModelTrain = new Train("Null Model Train", null, 100, 200.0, true);
        nullModelTrain.setSpecs(new TrainSpecs("TestTrain", 100, 200.0));
        trainRepository.save(nullModelTrain);

        Restriction<Train> restriction = _Train.model.isNull();
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(1, trains.size());
        assertNull(trains.getFirst().getModel());

        // Cleanup
        trainRepository.delete(nullModelTrain);
    }

    @Test
    public void testRuntimeRestrictionsWithNotNull() {
        Restriction<Train> restriction = _Train.name.isNull().negate();
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(6, trains.size());
        trains.forEach(train -> assertNotNull(train.getName()));
    }

    @Test
    public void testRuntimeRestrictionsCombined() {
        Restriction<Train> restriction = Restrict.all(
            _Train.capacity.greaterThan(150),
            _Train.electric.equalTo(true)
        );
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> {
            assertTrue(train.getCapacity() > 150);
            assertTrue(train.isElectric());
        });
    }

    @Test
    public void testRuntimeRestrictionsWithAtMost() {
        Restriction<Train> restriction = _Train.speed.lessThanEqual(120.0);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getSpeed() <= 120.0));
    }

    @Test
    public void testRuntimeRestrictionsWithNotBetween() {
        Restriction<Train> restriction = _Train.speed.notBetween(100.0, 300.0);
        List<Train> selected = trainRepository.findTrains(restriction);
        assertEquals(3, selected.size());
        selected.forEach(train -> {
            assertTrue(train.getSpeed() < 100.0 || train.getSpeed() > 300.0);
        });
        List<Train> trains = trainRepository.findTrains(Restrict.unrestricted());
        assertEquals(6, trains.size());
        trains.removeAll(selected);
        trains.forEach(train -> {
            assertFalse(train.getSpeed() < 100.0 || train.getSpeed() > 300.0);
        });
    }

    @Test
    public void testRuntimeRestrictionsWithNotIn() {
        Restriction<Train> restriction = _Train.name.notIn("Express 1", "Local 1");
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(4, trains.size());
        trains.forEach(train ->
            assertFalse("Express 1".equals(train.getName()) || "Local 1".equals(train.getName()))
        );
    }

    @Test
    public void testRuntimeRestrictionsWithNotLike() {
        Restriction<Train> restriction = _Train.model.notLike("HighSpeed");
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertNotEquals("HighSpeed", train.getModel()));
    }

    @Test
    public void testRuntimeRestrictionsWithAfterLocalDateTime() {
        LocalDateTime threshold = LocalDateTime.of(2023, 1, 1, 12, 0); // 12:00
        Restriction<Train> restriction = _Train.departureTime.greaterThan(threshold);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getDepartureTime().isAfter(threshold)));
    }

    @Test
    public void testRuntimeRestrictionsWithAfterLocalDateTimeCurrentDateTime() {
        LocalDateTime threshold = LocalDateTime.of(2023, 1, 1, 12, 0); // 12:00

        dateTimeProvider.setValue(threshold.atOffset(ZoneOffset.UTC));

        Restriction<Train> restriction = _Train.departureTime.greaterThan(TemporalExpression.localDateTime());
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());

        dateTimeProvider.setValue(null);
    }

    @Test
    public void testRuntimeRestrictionsWithAfterLocalDateCurrentDate() {
        LocalDate threshold = LocalDate.of(2023, 1, 1);

        dateTimeProvider.setValue(threshold.atStartOfDay().atOffset(ZoneOffset.UTC));

        Restriction<Train> restriction = _Train.departureDate.greaterThan(TemporalExpression.localDate());
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(0, trains.size()); // All trains have departureDate = 2023-01-01, so none are after

        // Test with a threshold that would match some trains
        LocalDate earlierThreshold = LocalDate.of(2021, 12, 31);
        dateTimeProvider.setValue(earlierThreshold.atStartOfDay().atOffset(ZoneOffset.UTC));

        restriction = _Train.departureDate.greaterThan(TemporalExpression.localDate());
        trains = trainRepository.findTrains(restriction);
        assertEquals(6, trains.size()); // All trains are after 2022-12-31
        assertEquals(trains.size(), trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getDepartureDate().isAfter(earlierThreshold)).count());

        dateTimeProvider.setValue(null);
    }

    @Test
    public void testRuntimeRestrictionsWithAfterLocalTimeCurrentTime() {
        LocalTime threshold = LocalTime.of(12, 0); // 12:00

        dateTimeProvider.setValue(LocalDateTime.of(LocalDate.of(2023, 1, 1), threshold).atOffset(ZoneOffset.UTC));

        Restriction<Train> restriction = _Train.departureTimeOnly.greaterThan(TemporalExpression.localTime());
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size()); // Trains with departure times: 8:00, 10:00, 12:00, 14:00, 16:00, 18:00 - so 14:00, 16:00, 18:00 are after 12:00
        assertEquals(trains.size(), trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getDepartureTimeOnly().isAfter(threshold)).count());

        dateTimeProvider.setValue(null);
    }

    @Test
    public void testRuntimeRestrictionsWithBeforeInstant() {
        Instant threshold = Instant.ofEpochSecond(1672531200 + 18000); // 2023-01-01T05:00:00Z
        Restriction<Train> restriction = _Train.createdAt.lessThan(threshold);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getCreatedAt().isBefore(threshold)));
    }

    @Test
    public void testRuntimeRestrictionsWithAfterInstant() {
        Instant threshold = Instant.ofEpochSecond(1672531200 + 18000); // 2023-01-01T05:00:00Z
        Restriction<Train> restriction = _Train.createdAt.greaterThan(threshold);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getCreatedAt().isAfter(threshold)));
    }

    @Test
    public void testRuntimeRestrictionsWithBeforeLocalDateTime() {
        LocalDateTime threshold = LocalDateTime.of(2023, 1, 1, 12, 0); // 12:00
        Restriction<Train> restriction = _Train.departureTime.lessThan(threshold);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getDepartureTime().isBefore(threshold)));
    }

    @Test
    public void testRuntimeRestrictionsWithEmbeddedPropertyEqualTo() {
        Restriction<Train> restriction = _Train.specs.navigate(_TrainSpecs.engineType).equalTo("Electric");
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertEquals("Electric", train.getSpecs().getEngineType()));
    }

    @Test
    public void testRuntimeRestrictionsWithEmbeddedPropertyGreaterThan() {
        Restriction<Train> restriction = _Train.specs.navigate(_TrainSpecs.maxLoad).greaterThan(500);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getSpecs().getMaxLoad() > 500));
        assertEquals(trains.size(), trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getSpecs().getMaxLoad() > 500).count());
    }

    @Test
    public void testRuntimeRestrictionsWithEmbeddedPropertyBetween() {
        Restriction<Train> restriction = _Train.specs.navigate(_TrainSpecs.weight).between(120.0, 160.0);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(5, trains.size());
        trains.forEach(train -> {
            assertTrue(train.getSpecs().getWeight() >= 120.0);
            assertTrue(train.getSpecs().getWeight() <= 160.0);
        });
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getSpecs().getWeight() >= 120.0 && train.getSpecs().getWeight() <= 160.0)
                .count()
        );
    }

    @Test
    public void testRuntimeRestrictionsWithEmbeddedPropertyIn() {
        Restriction<Train> restriction = _Train.specs.navigate(_TrainSpecs.engineType).in("Electric", "Diesel");
        List<Train> trains = trainRepository.trains(restriction);
        assertEquals(6, trains.size());
        trains.forEach(train ->
            assertTrue("Electric".equals(train.getSpecs().getEngineType()) || "Diesel".equals(train.getSpecs().getEngineType()))
        );
        assertEquals(
            trains.size(),
            trainRepository.trains(Restrict.unrestricted()).stream()
                .filter(train -> "Electric".equals(train.getSpecs().getEngineType()) || "Diesel".equals(train.getSpecs().getEngineType()))
                .count()
        );
    }

    @Test
    public void testRuntimeRestrictionsWithAssociationPropertyEqualTo() {
        Restriction<Train> restriction = _Train.manufacturer.navigate(_TrainManufacturer.name).equalTo("Siemens");
        List<Train> trains = trainRepository.findTrains$joinedManufacturer(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertEquals("Siemens", train.getManufacturer().getName()));
        assertEquals(
            trains.size(),
            trainRepository.findTrains$joinedManufacturer(Restrict.unrestricted()).stream()
                .filter(train -> "Siemens".equalsIgnoreCase(train.getManufacturer().getName()))
                .count()
        );
    }

    @Test
    public void testRuntimeRestrictionsWithAssociationPropertyGreaterThan() {
        Restriction<Train> restriction = _Train.manufacturer.navigate(_TrainManufacturer.foundedYear).greaterThan(1900);
        List<Train> trains = trainRepository.findTrains$joinedManufacturer(restriction);
        assertEquals(4, trains.size());
        trains.forEach(train -> assertTrue(train.getManufacturer().getFoundedYear() > 1900));
        assertEquals(
            trains.size(),
            trainRepository.findTrains$joinedManufacturer(Restrict.unrestricted()).stream()
                .filter(train -> train.getManufacturer().getFoundedYear() > 1900)
                .count()
        );
    }

    @Test
    public void testRuntimeRestrictionsWithAssociationPropertyBetween() {
        Restriction<Train> restriction = _Train.manufacturer.navigate(_TrainManufacturer.foundedYear).between(1840, 1950);
        List<Train> trains = trainRepository.findTrains$joinedManufacturer(restriction);
        assertEquals(6, trains.size());
        trains.forEach(train -> {
            assertTrue(train.getManufacturer().getFoundedYear() >= 1840);
            assertTrue(train.getManufacturer().getFoundedYear() <= 1950);
        });
    }

    @Test
    public void testRuntimeRestrictionsWithAssociationPropertyIn() {
        Restriction<Train> restriction = _Train.manufacturer.navigate(_TrainManufacturer.country).in("Germany", "France");
        List<Train> trains = trainRepository.findTrains$joinedManufacturer(restriction);
        assertEquals(4, trains.size());
        trains.forEach(train ->
            assertTrue("Germany".equals(train.getManufacturer().getCountry()) || "France".equals(train.getManufacturer().getCountry()))
        );
    }

    @Test
    public void testRuntimeRestrictionsCombinedEmbeddedAndAssociation() {
        Restriction<Train> restriction = Restrict.all(
            _Train.specs.navigate(_TrainSpecs.engineType).equalTo("Electric"),
            _Train.manufacturer.navigate(_TrainManufacturer.country).equalTo("Germany")
        );
        List<Train> trains = trainRepository.findTrainsWithJoinedManufacturer(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> {
            assertEquals("Electric", train.getSpecs().getEngineType());
            assertEquals("Germany", train.getManufacturer().getCountry());
        });
        assertEquals(
            trains.size(),
            trainRepository.findTrainsWithJoinedManufacturer(Restrict.unrestricted()).stream()
                .filter(train -> "Electric".equals(train.getSpecs().getEngineType()) && "Germany".equals(train.getManufacturer().getCountry()))
                .count()
        );
    }

    @Test
    public void testRuntimeRestrictionsWithOrder() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        Sort<Train> sort = _Train.name.asc();
        jakarta.data.Order<Train> order = jakarta.data.Order.by(sort);
        List<Train> trains = trainRepository.findTrainsWithOrder(restriction, order);
        assertEquals(4, trains.size());
        // Verify ordering by name ascending
        assertEquals("Express 1", trains.get(0).getName());
        assertEquals("Express 2", trains.get(1).getName());
        assertEquals("Express 3", trains.get(2).getName());
        assertEquals("Local 2", trains.get(3).getName());
        trains.forEach(train -> assertTrue(train.getCapacity() > 150));

        trains = trainRepository.findTrainsWithSorts(restriction, sort);
        assertEquals(4, trains.size());
        // Verify ordering by name ascending
        assertEquals("Express 1", trains.get(0).getName());
        assertEquals("Express 2", trains.get(1).getName());
        assertEquals("Express 3", trains.get(2).getName());
        assertEquals("Local 2", trains.get(3).getName());
        trains.forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testRuntimeRestrictionsWithOrder2() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        List<Train> trains = trainRepository.findTrainsWithOrderByName(restriction);
        assertEquals(4, trains.size());
        // Verify ordering by name ascending
        assertEquals("Express 1", trains.get(0).getName());
        assertEquals("Express 2", trains.get(1).getName());
        assertEquals("Express 3", trains.get(2).getName());
        assertEquals("Local 2", trains.get(3).getName());
        trains.forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testRuntimeRestrictionsWithOrderDescending() {
        Restriction<Train> restriction = _Train.electric.equalTo(true);
        Sort<Train> sort = _Train.name.desc();
        jakarta.data.Order<Train> order = jakarta.data.Order.by(sort);
        List<Train> trains = trainRepository.findTrainsWithOrder(restriction, order);
        assertEquals(3, trains.size());
        assertEquals("Express 3", trains.get(0).getName());
        assertEquals("Express 2", trains.get(1).getName());
        assertEquals("Express 1", trains.get(2).getName());
        trains.forEach(train -> assertTrue(train.isElectric()));

        trains = trainRepository.findTrainsWithSorts(restriction, sort);
        assertEquals(3, trains.size());
        assertEquals("Express 3", trains.get(0).getName());
        assertEquals("Express 2", trains.get(1).getName());
        assertEquals("Express 1", trains.get(2).getName());
        trains.forEach(train -> assertTrue(train.isElectric()));
    }

    @Test
    public void testRuntimeRestrictionsWithMultipleSorts() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        List<Train> trains = trainRepository.findTrainsWithOrder(restriction, Order.by(_Train.electric.asc(), _Train.capacity.desc()));
        assertEquals(4, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 150));
        // First should be non-electric (false comes before true in asc), then electric ones ordered by capacity desc
        assertFalse(trains.get(0).isElectric());
        assertEquals(180, trains.get(0).getCapacity());
        assertTrue(trains.get(1).isElectric());
        assertEquals(250, trains.get(1).getCapacity());

        trains = trainRepository.findTrainsWithSorts(restriction, _Train.electric.asc(), _Train.capacity.desc());
        assertEquals(4, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 150));
        // First should be non-electric (false comes before true in asc), then electric ones ordered by capacity desc
        assertFalse(trains.get(0).isElectric());
        assertEquals(180, trains.get(0).getCapacity());
        assertTrue(trains.get(1).isElectric());
        assertEquals(250, trains.get(1).getCapacity());
    }

    @Test
    public void testRuntimeRestrictionsWithEmbeddedPropertyOrder() {
        Restriction<Train> restriction = _Train.specs.navigate(_TrainSpecs.engineType).equalTo("Electric");
        jakarta.data.Order<Train> order = jakarta.data.Order.by(Sort.asc("specs.weight"));
//        NumericExpression<Train, Double> navigate = _Train.specs.navigate(_TrainSpecs.weight);
//        jakarta.data.Order<Train> order = jakarta.data.Order.by(Sort.asc(navigate));

        List<Train> trains = trainRepository.findTrainsWithOrder(restriction, order);

        assertEquals(3, trains.size());
        trains.forEach(train -> assertEquals("Electric", train.getSpecs().getEngineType()));
        // Verify ordering by embedded specs.weight ascending
        assertEquals(150.0, trains.get(0).getSpecs().getWeight());
        assertEquals(155.0, trains.get(1).getSpecs().getWeight());
        assertEquals(160.0, trains.get(2).getSpecs().getWeight());

        List<Train> electricTrains = trainRepository.findTrains(Restrict.unrestricted()).stream()
            .filter(train -> train.getSpecs().getEngineType().equals("Electric"))
            .sorted(Comparator.comparingDouble(o -> o.getSpecs().getWeight()))
            .toList();

        assertEquals(trains, electricTrains);
    }

    @Test
    public void testRuntimeRestrictionsWithEmbeddedPropertyMultipleSorts() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(180);
        List<Train> trains = trainRepository.findTrainsWithSorts(restriction,
            Sort.asc("specs.engineType"),
            Sort.desc("specs.maxLoad"));
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 180));
        // Should be ordered by engineType asc, then maxLoad desc
        assertEquals("Electric", trains.get(0).getSpecs().getEngineType());
        assertEquals(600, trains.get(0).getSpecs().getMaxLoad());
    }

    @Test
    public void testRuntimeRestrictionsWithAssociationPropertyMultipleSorts() {
        Restriction<Train> restriction = _Train.electric.equalTo(true);
        List<Train> trains = trainRepository.findTrainsWithSortsWithJoinedManufacturer(restriction,
            Sort.asc("manufacturer.country"),
            Sort.desc("manufacturer.foundedYear"));
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.isElectric()));
        // Should be ordered by country asc, then foundedYear desc
        // France (Alstom, 1928) should come before Germany (Siemens, 1847)
        assertEquals("Alstom", trains.get(0).getManufacturer().getName());
        assertEquals("France", trains.get(0).getManufacturer().getCountry());
        assertEquals("Siemens", trains.get(1).getManufacturer().getName());
        assertEquals("Germany", trains.get(1).getManufacturer().getCountry());
    }

    @Test
    public void testPagedTrainsWithRestriction() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest pageRequest = PageRequest.ofSize(2);

        Page<Train> page = trainRepository.trainsPaged(restriction, pageRequest);

        assertEquals(2, page.content().size());
        assertEquals(4, page.totalElements()); // Total matching records
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());

        page.content().forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testPagedTrainsWithRestrictionAndOrder() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest pageRequest = PageRequest.ofSize(2);
        Order<Train> order = Order.by(_Train.name.asc());

        Page<Train> page = trainRepository.trainsPaged(restriction, pageRequest, order);

        assertEquals(2, page.content().size());
        assertEquals(4, page.totalElements());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());

        // Verify ordering
        assertEquals("Express 1", page.content().get(0).getName());
        assertEquals("Express 2", page.content().get(1).getName());

        page.content().forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testPagedTrainsWithRestrictionAndSort() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest pageRequest = PageRequest.ofSize(2);
        Sort<Train> sort = _Train.name.desc();

        Page<Train> page = trainRepository.trainsPaged(restriction, pageRequest, sort);

        assertEquals(2, page.content().size());
        assertEquals(4, page.totalElements());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());

        // Verify ordering (descending)
        assertEquals("Local 2", page.content().get(0).getName());
        assertEquals("Express 3", page.content().get(1).getName());

        page.content().forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testPagedTrainsWithPageRequestFirst() {
        PageRequest pageRequest = PageRequest.ofSize(2);
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);

        Page<Train> page = trainRepository.trainsPaged2(pageRequest, restriction);

        assertEquals(2, page.content().size());
        assertEquals(4, page.totalElements());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());

        page.content().forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    protected boolean supportsCursorPaginationWithRestrictions() {
        return true;
    }

    @Test
    public void testCursoredPagedTrainsWithRestriction() {
        if (!supportsCursorPaginationWithRestrictions()) {
            return;
        }
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest pageRequest = PageRequest.ofSize(2);

        CursoredPage<Train> page = trainRepository.trainsCursoredPaged(restriction, pageRequest);

        assertEquals(2, page.content().size());
        assertTrue(page.hasNext());
//        assertFalse(page.hasPrevious());

        page.content().forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testCursoredPagedTrainsWithRestrictionAndOrder() {
        if (!supportsCursorPaginationWithRestrictions()) {
            return;
        }
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest pageRequest = PageRequest.ofSize(2);
        Order<Train> order = Order.by(_Train.name.asc());

        CursoredPage<Train> page = trainRepository.trainsCursoredPaged(restriction, pageRequest, order);

        assertEquals(2, page.content().size());
        assertTrue(page.hasNext());
//        assertFalse(page.hasPrevious());

        // Verify ordering
        assertEquals("Express 1", page.content().get(0).getName());
        assertEquals("Express 2", page.content().get(1).getName());

        page.content().forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testCursoredPagedTrainsWithRestrictionAndSort() {
        if (!supportsCursorPaginationWithRestrictions()) {
            return;
        }
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest pageRequest = PageRequest.ofSize(2);
        Sort<Train> sort = _Train.name.desc();

        CursoredPage<Train> page = trainRepository.trainsCursoredPaged(restriction, pageRequest, sort);

        assertEquals(2, page.content().size());
        assertTrue(page.hasNext());
//        assertFalse(page.hasPrevious());

        // Verify ordering (descending)
        assertEquals("Local 2", page.content().get(0).getName());
        assertEquals("Express 3", page.content().get(1).getName());

        page.content().forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testCursoredPagedTrainsWithPageRequestFirst() {
        if (!supportsCursorPaginationWithRestrictions()) {
            return;
        }
        PageRequest pageRequest = PageRequest.ofSize(2);
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);

        CursoredPage<Train> page = trainRepository.trainsCursoredPaged2(pageRequest, restriction);

        assertEquals(2, page.content().size());
        assertTrue(page.hasNext());
//        assertFalse(page.hasPrevious());

        page.content().forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testPagedTrainsNavigation() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest firstPage = PageRequest.ofSize(2);

        Page<Train> page1 = trainRepository.trainsPaged(restriction, firstPage);
        assertEquals(2, page1.content().size());
        assertTrue(page1.hasNext());
        assertFalse(page1.hasPrevious());

        PageRequest nextPage = page1.nextPageRequest();
        Page<Train> page2 = trainRepository.trainsPaged(restriction, nextPage);
        assertEquals(2, page2.content().size());
        assertFalse(page2.hasNext()); // Should be the last page
        assertTrue(page2.hasPrevious());

        // Verify different content
        assertNotEquals(page1.content().getFirst().getId(), page2.content().getFirst().getId());
    }

    @Test
    public void testCursoredPagedTrainsNavigation() {
        if (!supportsCursorPaginationWithRestrictions()) {
            return;
        }
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest firstPage = PageRequest.ofSize(2);

        CursoredPage<Train> page1 = trainRepository.trainsCursoredPaged(restriction, firstPage);
        assertEquals(2, page1.content().size());
        assertTrue(page1.hasNext());
//        assertFalse(page1.hasPrevious());

        CursoredPage<Train> page2 = trainRepository.trainsCursoredPaged(restriction, page1.nextPageRequest());
        assertEquals(2, page2.content().size());
//        assertFalse(page2.hasNext()); // Should be the last page
//        assertTrue(page2.hasPrevious());

        // Verify different content
        assertNotEquals(page1.content().getFirst().getId(), page2.content().getFirst().getId());
    }

    @Test
    public void testPagedTrainsWithEmptyRestriction() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(1000); // No trains match
        PageRequest pageRequest = PageRequest.ofSize(5);

        Page<Train> page = trainRepository.trainsPaged(restriction, pageRequest);

        assertEquals(0, page.content().size());
        assertEquals(0, page.totalElements());
        assertFalse(page.hasNext());
        assertFalse(page.hasPrevious());
    }

    @Test
    public void testCursoredPagedTrainsWithEmptyRestriction() {
        if (!supportsCursorPaginationWithRestrictions()) {
            return;
        }
        Restriction<Train> restriction = _Train.capacity.greaterThan(1000); // No trains match
        PageRequest pageRequest = PageRequest.ofSize(5);

        CursoredPage<Train> page = trainRepository.trainsCursoredPaged(restriction, pageRequest);

        assertEquals(0, page.content().size());
        assertFalse(page.hasNext());
        assertFalse(page.hasPrevious());
    }

    @Test
    public void testRuntimeRestrictionsWithUpper() {
        List<Train> trains = trainRepository.findTrains(_Train.name.upper().equalTo("EXPRESS 1"));
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());
    }

    @Test
    public void testRuntimeRestrictionsWithLower() {
        List<Train> trains = trainRepository.findTrains(_Train.name.lower().equalTo("express 1"));
        assertEquals(1, trains.size());
        assertEquals("Express 1", trains.getFirst().getName());
    }

    @Test
    public void testRuntimeRestrictionsWithLeft() {
        List<Train> trains = trainRepository.findTrains(_Train.name.left(7).equalTo("Express"));
        assertEquals(3, trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getName().substring(0, 7).startsWith("Express")).count());
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getName().startsWith("Express")));
    }

    @Test
    public void testRuntimeRestrictionsWithRight() {
        List<Train> trains = trainRepository.findTrains(_Train.name.right(1).equalTo("1"));
        assertEquals(3, trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getName().substring(train.getName().length() - 1).startsWith("1")).count());
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getName().endsWith("1")));
    }

    @Test
    public void testRuntimeRestrictionsWithLength() {
        List<Train> trains = trainRepository.findTrains(_Train.name.length().equalTo(9));
        assertEquals(3, trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getName().length() == 9).count());
        assertEquals(3, trains.size()); // "Express 1" and "Express 2" are both 9 characters
        trains.forEach(train -> assertEquals(9, train.getName().length()));
    }

    @Test
    public void testRuntimeRestrictionsWithLengthGreaterThan() {
        List<Train> trains = trainRepository.findTrains(_Train.name.length().greaterThan(8));
        assertEquals(3, trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getName().length() > 8).count());
        assertEquals(3, trains.size()); // "Express 1", "Express 2", "Express 3", "Local 2" are > 8 chars
        trains.forEach(train -> assertTrue(train.getName().length() > 8));
    }

    @Test
    public void testRuntimeRestrictionsWithLikeCustomWildcards() {
        List<Train> trains = trainRepository.findTrains(_Train.name.like("Express F", 'F', 'B'));
        assertEquals(3, trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getName().substring(0, train.getName().length() - 1).equals("Express ")).count());
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getName().startsWith("Express")));
    }

    @Test
    public void testRuntimeRestrictionsWithLikeCustomWildcards2() {
        List<Train> trains = trainRepository.findTrains(_Train.name.like("ExpressB", 'F', 'B'));
        assertEquals(3, trainRepository.findTrains(Restrict.unrestricted()).stream().filter(train -> train.getName().startsWith("Express")).count());
        assertEquals(3, trains.size());
        trains.forEach(train -> assertTrue(train.getName().startsWith("Express")));
    }

    @Test
    public void testRuntimeRestrictionsWithLikeCustomWildcardsAndEscape() {
        // Add a train with special characters to test escape functionality
        Train specialTrain = new Train("Test%Train", "Special", 100, 200.0, true);
        specialTrain.setSpecs(new TrainSpecs("Test", 100, 200.0));
        trainRepository.save(specialTrain);

        try {
            List<Train> trains = trainRepository.findTrains(_Train.name.like("Test\\%Train", '%', '_', '\\'));
            assertEquals(1, trains.size());
            assertEquals("Test%Train", trains.getFirst().getName());
        } finally {
            // Cleanup
            trainRepository.delete(specialTrain);
        }
    }

    @Test
    public void testRuntimeRestrictionsWithNotLikeCustomWildcards() {
        List<Train> trains = trainRepository.findTrains(_Train.name.notLike("Express F", 'F', 'B'));
        assertEquals(3, trains.size());
        trains.forEach(train -> assertFalse(train.getName().startsWith("Express")));
    }

    @Test
    public void testRuntimeRestrictionsWithNotLikeCustomWildcards2() {
        List<Train> trains = trainRepository.findTrains(_Train.name.notLike("ExpressB", 'F', 'B'));
        assertEquals(3, trains.size());
        trains.forEach(train -> assertFalse(train.getName().startsWith("Express")));
    }

    @Test
    public void testRuntimeRestrictionsWithNotLikeCustomWildcardsAndEscape() {
        // Add a train with special characters to test escape functionality
        Train specialTrain = new Train("Test%Train", "Special", 100, 200.0, true);
        specialTrain.setSpecs(new TrainSpecs("Test", 100, 200.0));
        trainRepository.save(specialTrain);

        try {
            List<Train> trains = trainRepository.findTrains(_Train.name.notLike("Test\\%Train", '%', '_', '\\'));
            assertEquals(6, trains.size()); // All trains except the special one
            trains.forEach(train -> assertNotEquals("Test%Train", train.getName()));
        } finally {
            // Cleanup
            trainRepository.delete(specialTrain);
        }
    }

    @Test
    public void testAbs() {
        Train specialTrain = new Train("NegativeTestTrain", "Special", 100, 200.0, true);
        specialTrain.setSpecs(new TrainSpecs("Test", -100, 200.0));
        trainRepository.save(specialTrain);

        try {
            assertEquals(6, trainRepository.findTrains(_Train.specs.navigate(_TrainSpecs.maxLoad).greaterThan(0)).size());
            assertEquals(7, trainRepository.findTrains(_Train.specs.navigate(_TrainSpecs.maxLoad).abs().greaterThan(0)).size());
        } finally {
            // Cleanup
            trainRepository.delete(specialTrain);
        }
    }

    @Test
    public void testRuntimeRestrictionsWithNotStartsWith() {
        List<Train> trains = trainRepository.findTrains(_Train.name.notStartsWith("Express"));
        assertEquals(3, trains.size());
        trains.forEach(train -> assertFalse(train.getName().startsWith("Express")));
    }

    @Test
    public void testRuntimeRestrictionsWithNotEndsWith() {
        List<Train> trains = trainRepository.findTrains(_Train.name.notEndsWith("1"));
        assertEquals(3, trains.size());
        trains.forEach(train -> assertFalse(train.getName().endsWith("1")));
    }

    @Test
    public void testRuntimeRestrictionsWithNumericNegated() {
        // Test negated on speed (all positive values, so negated will be negative)
        assertEquals(
            4,
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> -train.getSpeed() < -100.0)
                .count()
        );
        Restriction<Train> restriction = _Train.speed.negated().lessThan(-100.0);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(4, trains.size());
        trains.forEach(train -> assertTrue(train.getSpeed() > 100.0));
    }

    @Test
    public void testRuntimeRestrictionsWithNumericPlus() {
        // Test capacity + 50 > 250 (should match trains with capacity > 200)
        assertEquals(
            2,
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() + 50 > 250)
                .count()
        );
        Restriction<Train> restriction = _Train.capacity.plus(50).greaterThan(250);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testRuntimeRestrictionsWithNumericMinus() {
        // Test capacity - 50 > 150 (should match trains with capacity > 200)
        assertEquals(
            2,
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() - 50 > 150)
                .count()
        );
        Restriction<Train> restriction = _Train.capacity.minus(50).greaterThan(150);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testRuntimeRestrictionsWithNumericTimes() {
        // Test capacity * 2 > 400 (should match trains with capacity > 200)
        assertEquals(
            2,
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() * 2 > 400)
                .count()
        );
        Restriction<Train> restriction = _Train.capacity.times(2).greaterThan(400);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testRuntimeRestrictionsWithNumericDivide() {
        // Test capacity / 2 > 100 (should match trains with capacity > 200)
        assertEquals(
            2,
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() / 2 > 100)
                .count()
        );
        Restriction<Train> restriction = _Train.capacity.dividedBy(2).greaterThan(100);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testRuntimeRestrictionsWithNumericExpressionOperations() {
        // Test capacity + capacity > 400 (should match trains with capacity > 200)
        assertEquals(
            2,
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() + train.getCapacity() > 400)
                .count()
        );
        Restriction<Train> restriction = _Train.capacity.plus(_Train.capacity).greaterThan(400);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testRuntimeRestrictionsWithNumericEqualTo() {
        // Test capacity equal to 200
        Restriction<Train> restriction = _Train.capacity.equalTo(200);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(1, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() == 200)
                .count()
        );
        assertEquals(200, trains.getFirst().getCapacity());
    }

    @Test
    public void testRuntimeRestrictionsWithNumericEqualToExpression() {
        // Test capacity equal to capacity (should match all trains)
        Restriction<Train> restriction = _Train.capacity.equalTo(_Train.capacity);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(6, trains.size());
    }

    @Test
    public void testRuntimeRestrictionsWithNumericComplexExpression() {
        // Test (capacity + 50) * 2 > 500 (should match trains with capacity > 200)
        Restriction<Train> restriction = _Train.capacity.plus(50).times(2).greaterThan(500);
        assertEquals(
            2,
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> (train.getCapacity() + 50) * 2 > 500)
                .count()
        );
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(2, trains.size());
        trains.forEach(train -> assertTrue(train.getCapacity() > 200));
    }

    @Test
    public void testRuntimeRestrictionsWithNumericAbsOnEmbedded() {
        // Test abs on embedded weight (all positive values, so abs should return same)
        Restriction<Train> restriction = _Train.specs.navigate(_TrainSpecs.weight).abs().greaterThan(140.0);
        List<Train> trains = trainRepository.findTrains(restriction);
        assertEquals(4, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> Math.abs(train.getSpecs().getWeight()) > 140.0)
                .count()
        );
        trains.forEach(train -> assertTrue(train.getSpecs().getWeight() > 140.0));
    }

    @Test
    public void testFirstAnnotationWithRestriction() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        List<Train> trains = trainRepository.findFirst2Trains(restriction);
        assertEquals(2, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() > 150)
                .limit(2)
                .count()
        );
        trains.forEach(train -> assertTrue(train.getCapacity() > 150));
    }

    @Test
    public void testFirstAnnotationWithRestrictionSingle() {
        Restriction<Train> restriction = _Train.electric.equalTo(true);
        List<Train> trains = trainRepository.findFirstTrain(restriction);
        assertEquals(1, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(Train::isElectric)
                .limit(1)
                .count()
        );
        assertTrue(trains.getFirst().isElectric());
    }

    @Test
    public void testFirstAnnotationWithRestrictionThree() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(180);
        List<Train> trains = trainRepository.findFirst3Trains(restriction);
        assertEquals(3, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() > 180)
                .limit(3)
                .count()
        );
        trains.forEach(train -> assertTrue(train.getCapacity() > 180));
    }

    @Test
    public void testFindOrderByFirstSelectSingleField() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        List<String> trainNames = trainRepository.findFirst2TrainNamesOrderedByName(restriction);
        assertEquals(2, trainNames.size());
        assertEquals(
            trainNames.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() > 150)
                .sorted(Comparator.comparing(Train::getName))
                .limit(2)
                .count()
        );
        // Verify ordering by name
        assertEquals("Express 1", trainNames.get(0));
        assertEquals("Express 2", trainNames.get(1));
    }

    @Test
    public void testFindOrderByFirstSelectMultipleFields() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(180);
        List<TrainNameCapacityDto> trains = trainRepository.findFirst3TrainsOrderedByCapacity(restriction);
        assertEquals(3, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() > 180)
                .sorted(Comparator.comparing(Train::getCapacity))
                .limit(3)
                .count()
        );
        // Verify ordering by capacity
        assertEquals(200, trains.get(0).capacity());
        assertEquals(220, trains.get(1).capacity());
        assertEquals(250, trains.get(2).capacity());
    }

    @Test
    public void testFindOrderByFirstNoSelect() {
        Restriction<Train> restriction = _Train.electric.equalTo(true);
        List<Train> trains = trainRepository.findFirstTrainOrderedBySpeed(restriction);
        assertEquals(1, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(Train::isElectric)
                .sorted(Comparator.comparing(Train::getSpeed))
                .limit(1)
                .count()
        );
        assertTrue(trains.getFirst().isElectric());
        // Should be the one with the lowest speed among electric trains
        Train expected = trainRepository.findTrains(Restrict.unrestricted()).stream()
            .filter(Train::isElectric)
            .min(Comparator.comparing(Train::getSpeed))
            .orElseThrow();
        assertEquals(expected.getSpeed(), trains.getFirst().getSpeed());
    }

    @Test
    public void testFindOrderByFirstSelectTwoFields() {
        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        List<TrainNameModelDto> trains = trainRepository.findFirst4TrainsOrderedByName(restriction);
        assertEquals(4, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .filter(train -> train.getCapacity() > 150)
                .sorted(Comparator.comparing(Train::getName))
                .limit(4)
                .count()
        );
        // Verify ordering by name
        assertEquals("Express 1", trains.get(0).name());
        assertEquals("Express 2", trains.get(1).name());
        assertEquals("Express 3", trains.get(2).name());
        assertEquals("Local 2", trains.get(3).name());
    }

    @Test
    public void testFindOrderByFirstSelectSingleFieldNoRestriction() {
        List<String> trainNames = trainRepository.findFirst2TrainNamesOrderedByName();
        assertEquals(2, trainNames.size());
        assertEquals(
            trainNames.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .sorted(Comparator.comparing(Train::getName))
                .limit(2)
                .map(Train::getName)
                .count()
        );
        // Verify ordering by name (first 2 when ordered by name)
        assertEquals("Cargo 1", trainNames.get(0));
        assertEquals("Express 1", trainNames.get(1));
    }

    @Test
    public void testFindOrderByFirstSelectMultipleFieldsNoRestriction() {
        List<Train> trains = trainRepository.findFirst3TrainsOrderedByCapacity();
        assertEquals(3, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .sorted(Comparator.comparing(Train::getCapacity))
                .limit(3)
                .count()
        );
        // Verify ordering by capacity (first 3 when ordered by capacity)
        assertEquals(50, trains.get(0).getCapacity());
        assertEquals(150, trains.get(1).getCapacity());
        assertEquals(180, trains.get(2).getCapacity());
    }

    @Test
    public void testFindOrderByFirstNoSelectNoRestriction() {
        List<Train> trains = trainRepository.findFirstTrainOrderedBySpeed();
        assertEquals(1, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .sorted(Comparator.comparing(Train::getSpeed))
                .limit(1)
                .count()
        );
        // Should be the one with the lowest speed among all trains
        Train expected = trainRepository.findTrains(Restrict.unrestricted()).stream()
            .min(Comparator.comparing(Train::getSpeed))
            .orElseThrow();
        assertEquals(expected.getSpeed(), trains.getFirst().getSpeed());
    }

    @Test
    public void testFindOrderByFirstSelectTwoFieldsNoRestriction() {
        List<Train> trains = trainRepository.findFirst4TrainsOrderedByName();
        assertEquals(4, trains.size());
        assertEquals(
            trains.size(),
            trainRepository.findTrains(Restrict.unrestricted()).stream()
                .sorted(Comparator.comparing(Train::getName))
                .limit(4)
                .count()
        );
        // Verify ordering by name (first 4 when ordered by name)
        assertEquals("Cargo 1", trains.get(0).getName());
        assertEquals("Express 1", trains.get(1).getName());
        assertEquals("Express 2", trains.get(2).getName());
        assertEquals("Express 3", trains.get(3).getName());
    }

}
