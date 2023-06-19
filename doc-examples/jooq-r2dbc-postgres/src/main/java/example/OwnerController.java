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
package example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

@Controller("/owners")
class OwnerController {

    private final IOwnerRepository ownerRepository;
    private final Mapper mapper;

    OwnerController(IOwnerRepository ownerRepository, Mapper mapper) {
        this.ownerRepository = ownerRepository;
        this.mapper = mapper;
    }

    @Get
    @Transactional(readOnly = true)
    Mono<List<OwnerDto>> all() {
        return ownerRepository.findAll()
                .map(mapper::toOwnerDto)
            .collectList();
    }

    @Get("/{name}")
    @Transactional(readOnly = true)
    Mono<OwnerDto> byName(String name) {
        return ownerRepository.findByName(name)
                .map(mapper::toOwnerDto);
    }

}
