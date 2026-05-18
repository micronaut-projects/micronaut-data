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
package io.micronaut.data.tck.repositories;

import io.micronaut.data.tck.entities.Train;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Select;

import java.util.Optional;

public interface TrainsRepository {

    @Find(Train.class)
    @Select("model")
    Optional<String> getModel(@By("name") String name);

}
