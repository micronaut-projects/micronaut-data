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
package io.micronaut.data.hibernate;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.tck.entities.Shipment;
import io.micronaut.data.tck.entities.ShipmentDto;
import io.micronaut.data.tck.entities.ShipmentId;

import java.util.List;

@Repository
public interface JpaShipmentRepository extends CrudRepository<Shipment, ShipmentId> {

    Shipment findByShipmentIdCountry(String country);

    Shipment findByShipmentIdCountryAndShipmentIdCity(String country, String city);

    long countDistinct();

    List<Shipment> findAllByShipmentIdCountry(String country);

    List<ShipmentDto> queryAllByShipmentIdCountry(String country);
}
