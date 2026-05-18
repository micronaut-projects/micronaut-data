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

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.tck.entities.Train;
import io.micronaut.data.tck.repositories.TrainRepository;
import jakarta.data.repository.stateful.Detach;
import jakarta.data.repository.stateful.Merge;
import jakarta.data.repository.stateful.Persist;
import jakarta.data.repository.stateful.Refresh;
import jakarta.data.repository.stateful.Remove;

@Repository
public interface HibernateTrainRepository extends TrainRepository {

    @Persist
    void makePersistent(Train entity);

    @Persist
    void makePersistentAll(Iterable<Train> entities);

    @Persist
    void makePersistentArray(Train[] entities);

    @Merge
    Train mergeTrain(Train entity);

    @Merge
    Iterable<Train> mergeTrains(Iterable<Train> entities);

    @Merge
    Train[] mergeTrainArray(Train[] entities);

    @Refresh
    void refreshTrain(Train entity);

    @Refresh
    void refreshTrains(Iterable<Train> entities);

    @Refresh
    void refreshTrainArray(Train[] entities);

    @Remove
    void removeTrain(Train entity);

    @Remove
    void removeTrains(Iterable<Train> entities);

    @Remove
    void removeTrainArray(Train[] entities);

    @Detach
    void detachTrain(Train entity);

    @Detach
    void detachTrains(Iterable<Train> entities);

    @Detach
    void detachTrainArray(Train[] entities);
}
