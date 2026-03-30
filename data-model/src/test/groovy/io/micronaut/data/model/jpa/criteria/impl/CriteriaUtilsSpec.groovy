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
package io.micronaut.data.model.jpa.criteria.impl

import io.micronaut.data.model.PersistentProperty
import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression
import io.micronaut.data.model.jpa.criteria.impl.predicate.ConjunctionPredicate
import io.micronaut.data.model.jpa.criteria.impl.predicate.GeoIntersectsPredicate
import io.micronaut.data.model.jpa.criteria.impl.predicate.GeoWithinPredicate
import io.micronaut.data.model.jpa.criteria.impl.predicate.NearPredicate
import io.micronaut.data.model.jpa.criteria.impl.predicate.NearSpherePredicate
import io.micronaut.data.model.jpa.criteria.impl.predicate.TextPredicate
import spock.lang.Specification

class CriteriaUtilsSpec extends Specification {

    void 'extracts parameters from text predicate in declared order'() {
        given:
        def search = parameter(String, 'search')
        def language = parameter(String, 'language')
        def caseSensitive = parameter(Boolean, 'caseSensitive')
        def diacriticSensitive = parameter(Boolean, 'diacriticSensitive')

        when:
        def parameters = CriteriaUtils.extractPredicateParameters(new TextPredicate(search, language, caseSensitive, diacriticSensitive))

        then:
        parameters as List == [search, language, caseSensitive, diacriticSensitive]
    }

    void 'extracts only parameter expressions from mongo-specific predicates and nested conjunctions'() {
        given:
        def property = Stub(DefaultPersistentPropertyPath) {
            getProperty() >> Stub(PersistentProperty)
        }
        def textSearch = parameter(String, 'textSearch')
        def geoWithinGeometry = parameter(Map, 'geoWithinGeometry')
        def geoIntersectsGeometry = parameter(Map, 'geoIntersectsGeometry')
        def nearGeometry = parameter(Map, 'nearGeometry')
        def nearMin = parameter(Double, 'nearMin')
        def nearSphereGeometry = parameter(Map, 'nearSphereGeometry')
        def nearSphereMax = parameter(Double, 'nearSphereMax')

        def predicate = new ConjunctionPredicate([
                new TextPredicate(textSearch, new LiteralExpression<>('en'), null, null),
                new GeoWithinPredicate(property, geoWithinGeometry),
                new GeoIntersectsPredicate(property, geoIntersectsGeometry),
                new NearPredicate(property, nearGeometry, nearMin, new LiteralExpression<>(2000d)),
                new NearSpherePredicate(property, nearSphereGeometry, new LiteralExpression<>(0d), nearSphereMax)
        ])

        when:
        def parameters = CriteriaUtils.extractPredicateParameters(predicate)

        then:
        parameters as List == [textSearch, geoWithinGeometry, geoIntersectsGeometry, nearGeometry, nearMin, nearSphereGeometry, nearSphereMax]
    }

    private static <T> IParameterExpression<T> parameter(Class<T> type, String name) {
        new DefaultParameterExpression<>(type, name, null)
    }
}
