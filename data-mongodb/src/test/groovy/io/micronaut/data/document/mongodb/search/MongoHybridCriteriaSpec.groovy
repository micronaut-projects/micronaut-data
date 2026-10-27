/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.document.mongodb.search

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQueryBuilder
import io.micronaut.data.model.search.SearchQuery
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.runtime.intercept.criteria.SearchQuerySupport
import spock.lang.Specification

/**
 * Renders the one aggregation of a hybrid search MongoDB fuses itself, without a deployment.
 */
class MongoHybridCriteriaSpec extends Specification {

    RuntimeCriteriaBuilder cb = new RuntimeCriteriaBuilder()

    private String render(SearchQuery searchQuery, Pageable pageable = Pageable.UNPAGED) {
        def query = SearchQuerySupport.hybridQuery(cb, AtlasArticle as Class<Object>, searchQuery, pageable)
        return ((PersistentEntityCriteriaQueryBuilder) query).build(AnnotationMetadata.EMPTY_METADATA, new MongoQueryBuilder()).query
    }

    void "a hybrid search renders a \$rankFusion over the search and the vector search pipelines"() {
        when:
        def pipeline = render(SearchQuery.builder("bread").vector("embedding", Vector.of([1f, 0f, 0f] as float[])).textWeight(2).build())

        then:
        pipeline.startsWith("[{\$rankFusion:{input:{pipelines:{text:[{\$search:{index:'default',compound:{must:[{\$mn_qp:1,\$mn_search:'ANY',\$mn_paths:['title','body'],\$mn_boosts:{title:10}}]}}},{\$limit:100}],vector:[{\$vectorSearch:{index:'vector',path:'embedding',queryVector:{\$mn_qp:0},numCandidates:1000,limit:100}}]}},combination:{weights:{text:2.0,vector:1.0}}}}")
        pipeline.contains("{\$addFields:{mn_score:{\$meta:'score'}}},{\$match:{mn_score:{\$gt:0}}}")
        pipeline.endsWith("{\$addFields:{__micronaut_sort_0:{\$meta:'score'}}},{\$sort:{__micronaut_sort_0:-1}},{\$unset:['__micronaut_sort_0']}]")
    }

    void "the page sizes the candidate window of both pipelines"() {
        when:
        def pipeline = render(SearchQuery.builder("bread").vector("embedding", Vector.of([1f, 0f, 0f] as float[])).candidates(10).build(), Pageable.from(5, 30))

        then:
        pipeline.contains("{\$limit:180}")
        pipeline.contains("numCandidates:1800,limit:180")
    }
}
