/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.search.elasticsearch;

import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.search.elasticsearch.annotation.ElasticsearchRepository;
import jakarta.data.repository.Query;

import java.util.List;

/**
 * Jakarta Data queries over {@link Reading} that run as Painless scripts: comparisons between two properties
 * and comparisons of the lower or upper case form of a property.
 */
@ElasticsearchRepository
public interface ReadingRepository extends CrudRepository<Reading, String> {

    @Query("WHERE NOT (low = high) ORDER BY id")
    List<Reading> lowNotEqualToHigh();

    @Query("WHERE low <> high ORDER BY id")
    List<Reading> lowDifferentFromHigh();

    @Query("WHERE NOT (low < high) ORDER BY id")
    List<Reading> lowNotBelowHigh();

    @Query("WHERE LOWER(code) > :code ORDER BY id")
    List<Reading> lowerCodeAfter(String code);

    @Query("WHERE UPPER(code) = UPPER(:code) ORDER BY id")
    List<Reading> codeIgnoringCase(String code);

    @Query("WHERE NOT (LOWER(code) <= :code) ORDER BY id")
    List<Reading> lowerCodeNotUpTo(String code);

    @Query("WHERE LOWER(code) BETWEEN :low AND :high ORDER BY id")
    List<Reading> lowerCodeBetween(String low, String high);

    @Query("WHERE LOWER(code) BETWEEN LOWER(:low) AND LOWER(:high) ORDER BY id")
    List<Reading> lowerCodeBetweenFolded(String low, String high);

    @Query("WHERE LOWER(code) NOT BETWEEN :low AND :high ORDER BY id")
    List<Reading> lowerCodeNotBetween(String low, String high);
}
