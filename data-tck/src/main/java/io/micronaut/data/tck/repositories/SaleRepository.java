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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryResult;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Discount;
import io.micronaut.data.tck.entities.Sale;
import io.micronaut.data.tck.entities.SaleDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SaleRepository extends CrudRepository<Sale, Long> {

    SaleDTO getById(Long id);

    @Override
    @Join(value = "items", type = Join.Type.LEFT_FETCH)
    Optional<Sale> findById(@NonNull Long id);

    void updateData(@Id Long id, @Parameter("data") Map<String, String> data, @Parameter("dataList") List<String> dataList);

    @Query("SELECT extra_data AS extraData FROM sale WHERE id = :id")
    @QueryResult(column = "extraData", type = QueryResult.Type.JSON, jsonDataType = JsonDataType.STRING)
    Optional<Discount> getDiscountById(Long id);

    @Query("SELECT extra_data AS DATA FROM sale WHERE name = :name")
    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.STRING)
    List<Sale> findAllByNameFromJson(String name);

    @Query("SELECT extra_data AS DATA FROM sale WHERE name = :name")
    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.STRING)
    Optional<Sale> findByNameFromJson(String name);

    @Join("items")
    @QueryResult(type = QueryResult.Type.TABULAR)
    Optional<Sale> findByName(String name);
}
