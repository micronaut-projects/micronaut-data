/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.document.tck.repositories;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.document.tck.entities.Quantity;
import io.micronaut.data.document.tck.entities.Sale;
import io.micronaut.data.repository.CrudRepository;

public interface SaleRepository extends CrudRepository<Sale, String> {

    long updateQuantity(@Parameter("id") @Id String id, @Parameter("quantity") Quantity quantity);

}
