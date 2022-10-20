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

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.ArraysDto;
import io.micronaut.data.tck.entities.ArraysEntity;

import io.micronaut.core.annotation.Nullable;
import java.util.Collection;

public interface ArraysEntityRepository extends CrudRepository<ArraysEntity, Long> {

    void update(@Id Long id,
                @Parameter("stringArray") String[] stringArray,
                @Parameter("stringArrayCollection") Collection<String> stringArrayCollection,
                @Parameter("shortArray") Short[] shortArray,
                @Parameter("shortPrimitiveArray") short[] shortPrimitiveArray,
                @Parameter("shortArrayCollection") Collection<Short> shortArrayCollection,
                @Parameter("integerArray") @Nullable Integer[] integerArray,
                @Parameter("integerPrimitiveArray") @Nullable int[] integerPrimitiveArray,
                @Parameter("integerArrayCollection") @Nullable Collection<Integer> integerArrayCollection
    );

    ArraysDto queryBySomeId(Long someId);
}
