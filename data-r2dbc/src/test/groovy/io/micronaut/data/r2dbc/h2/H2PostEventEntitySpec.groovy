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
package io.micronaut.data.r2dbc.h2

import io.micronaut.core.async.propagation.ReactorPropagation
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

@MicronautTest(transactional = false)
class H2PostEventEntitySpec extends Specification implements H2TestPropertyProvider {

    @Inject
    PostEventRecordReactiveRepository repository

    @Inject
    PostEventRecordListener listener

    void setup() {
        listener.clear()
    }

    void cleanup() {
        repository.deleteAll().block()
    }

    void "test post persist listener can replace a single entity"() {
        when:
            def saved = repository.save(new PostEventRecord(null, "single", null)).block()

        then:
            saved.id() != null
            saved.status() == PostEventRecordListener.STATUS
    }

    void "test post persist listener can replace batch entities"() {
        when:
            def saved = repository.saveAll([new PostEventRecord(null, "a", null), new PostEventRecord(null, "b", null)]).collectList().block()

        then:
            saved.size() == 2
            saved.every { it.id() != null && it.status() == PostEventRecordListener.STATUS }
    }

    void "test listeners see the propagated context"() {
        when:
            withPropagatedContext(repository.save(new PostEventRecord(null, "single", null)))

        then:
            listener.prePersistContexts == ["test"]
            listener.postPersistContexts == ["test"]

        when:
            listener.clear()
            withPropagatedContext(repository.saveAll([new PostEventRecord(null, "a", null), new PostEventRecord(null, "b", null)]))

        then:
            listener.prePersistContexts == ["test", "test"]
            listener.postPersistContexts == ["test", "test"]
    }

    private static List<Object> withPropagatedContext(Publisher<?> publisher) {
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty().plus(new PostEventRecordListener.TestContextElement("test"))
        return Flux.from(publisher)
            .contextWrite(ctx -> ReactorPropagation.addPropagatedContext(ctx, propagatedContext))
            .collectList()
            .block() as List<Object>
    }
}

@R2dbcRepository(dialect = Dialect.H2)
interface PostEventRecordReactiveRepository extends ReactorCrudRepository<PostEventRecord, Long> {
}
