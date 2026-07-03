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
package io.micronaut.data.hibernate;

import io.micronaut.data.tck.entities.Train;
import io.micronaut.data.tck.entities._Train;
import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.tck.tests.AbstractJakartaDataTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.data.Sort;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.restrict.Restriction;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@H2DBProperties
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public class HibernateJakartaDataTest extends AbstractJakartaDataTest {

    @Inject
    HibernateTrainRepository hibernateTrainRepository;

    @Inject
    ConnectionOperations<Session> connectionOperations;

    @Override
    protected boolean supportsCursorPaginationWithRestrictions() {
        return false;
    }

    // Enable after https://github.com/jakartaee/data/issues/1290

    @Override
    public void testRuntimeRestrictionsWithLength() {
    }

    @Override
    public void testRuntimeRestrictionsWithLengthGreaterThan() {
    }

    @Override
    public void testRuntimeRestrictionsWithLikeCustomWildcardsAndEscape() {
    }

    @Override
    public void testRuntimeRestrictionsWithNotLikeCustomWildcardsAndEscape() {
    }

    @Override
    public void testAbs() {
    }

    @Override
    public void testRuntimeRestrictionsWithNumericNegated() {
    }

    @Override
    public void testRuntimeRestrictionsWithNumericAbsOnEmbedded() {
    }

    @Test
    public void testPagedTrainsWithRestrictionAndSortIgnoreCase() {
        Train lowercase = new Train("alpha", "Regional", 210, 140.0, true);
        Train uppercase = new Train("Bravo", "Regional", 220, 145.0, true);
        trainRepository.saveAll(List.of(lowercase, uppercase));

        Restriction<Train> restriction = _Train.capacity.greaterThan(150);
        PageRequest pageRequest = PageRequest.ofSize(10);
        Sort<Train> sort = Sort.ascIgnoreCase("name");

        Page<Train> page = trainRepository.trainsPaged(restriction, pageRequest, sort);

        List<String> names = page.content().stream().map(Train::getName).collect(Collectors.toList());
        Assertions.assertTrue(names.contains("alpha"));
        Assertions.assertTrue(names.contains("Bravo"));
        Assertions.assertTrue(names.indexOf("alpha") < names.indexOf("Bravo"));
    }

    @Test
    public void testStatefulAnnotations() {
        connectionOperations.executeWrite(status -> {
            Train train = trainRepository.findAll().iterator().next();
            Train detachedTrain = new Train(train.getName(), train.getModel(), train.getCapacity(), train.getSpeed(), train.isElectric());
            detachedTrain.setId(train.getId());

            Train merged = hibernateTrainRepository.mergeTrain(detachedTrain);
            Assertions.assertNotSame(detachedTrain, merged);
            Assertions.assertEquals(detachedTrain.getName(), merged.getName());

            merged.setName("Merged Train" + System.nanoTime());
            hibernateTrainRepository.refreshTrain(merged);
            Assertions.assertEquals(trainRepository.findById(merged.getId()).orElseThrow().getName(), merged.getName());

            hibernateTrainRepository.detachTrain(merged);
            merged.setName("Detached Change" + System.nanoTime());
            Train finalMerged = merged;
            Assertions.assertThrows(IllegalArgumentException.class, () -> hibernateTrainRepository.refreshTrain(finalMerged));
            merged = hibernateTrainRepository.mergeTrain(merged);

            Train newTrain = new Train("Persisted Statefull", "Model", 100, 100.0, true);
            hibernateTrainRepository.makePersistent(newTrain);
            Assertions.assertNotNull(newTrain.getId());

            Train newTrain2 = new Train("Persisted Stateful 2", "Model", 120, 150.0, false);
            Train newTrain3 = new Train("Persisted Stateful 3", "Model", 130, 160.0, false);
            List<Train> persistedList = new ArrayList<>(List.of(newTrain2, newTrain3));
            hibernateTrainRepository.makePersistentAll(persistedList);
            persistedList.forEach(t -> Assertions.assertNotNull(t.getId()));

            Train newTrain4 = new Train("Persisted Stateful 4", "Model", 140, 170.0, false);
            Train newTrain5 = new Train("Persisted Stateful 5", "Model", 150, 180.0, false);
            Train[] persistedArray = {newTrain4, newTrain5};
            hibernateTrainRepository.makePersistentArray(persistedArray);
            Arrays.stream(persistedArray).forEach(t -> Assertions.assertNotNull(t.getId()));

            List<Train> toMergeList = persistedList.stream().map(trainRepository::update).collect(Collectors.toList());
            Iterable<Train> mergedList = hibernateTrainRepository.mergeTrains(toMergeList);
            List<Train> mergedListCopy = mergedList instanceof List<?> list ? (List<Train>) list : new ArrayList<>();
            if (!(mergedList instanceof List<?>)) {
                mergedList.forEach(mergedListCopy::add);
            }
            Assertions.assertEquals(toMergeList.size(), mergedListCopy.size());

            Train[] toMergeArray = Arrays.stream(persistedArray).map(trainRepository::update).toArray(Train[]::new);
            Train[] mergedArray = hibernateTrainRepository.mergeTrainArray(toMergeArray);
            Assertions.assertEquals(toMergeArray.length, mergedArray.length);

            hibernateTrainRepository.detachTrains(mergedListCopy);
            mergedListCopy.forEach(detached -> Assertions.assertThrows(IllegalArgumentException.class, () -> hibernateTrainRepository.refreshTrain(detached)));
            List<Train> reattachedList = StreamSupport.stream(hibernateTrainRepository.mergeTrains(mergedListCopy).spliterator(), false)
                .collect(Collectors.toList());

            hibernateTrainRepository.detachTrainArray(mergedArray);
            Arrays.stream(mergedArray).forEach(detached -> Assertions.assertThrows(IllegalArgumentException.class, () -> hibernateTrainRepository.refreshTrain(detached)));
            Train[] reattachedArray = hibernateTrainRepository.mergeTrainArray(mergedArray);

            hibernateTrainRepository.removeTrains(reattachedList);
            hibernateTrainRepository.removeTrainArray(reattachedArray);
            hibernateTrainRepository.removeTrain(newTrain);

            Assertions.assertThrows(IllegalArgumentException.class, () -> hibernateTrainRepository.refreshTrain(detachedTrain));
            return null;
        });
    }

}
