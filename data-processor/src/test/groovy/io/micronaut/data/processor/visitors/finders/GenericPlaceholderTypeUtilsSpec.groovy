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
package io.micronaut.data.processor.visitors.finders

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement
import spock.lang.Specification

/**
 * Tests that a method level generic placeholder, such as the {@code S} of the inherited
 * {@code <S extends T> S save(S entity)}, is resolved to its bound before the type is inspected.
 *
 * <p>The Java element model represents such a placeholder with an element that already exposes the
 * annotations of its bound, so a Java source cannot reproduce this. Other language element models
 * expose the placeholder itself, which is the case modelled here.</p>
 */
class GenericPlaceholderTypeUtilsSpec extends Specification {

    private static final String ENTITY_NAME = "example.Book"

    private ClassElement entityElement() {
        ClassElement entity = Stub(ClassElement)
        entity.getName() >> ENTITY_NAME
        entity.isArray() >> false
        entity.hasStereotype(MappedEntity) >> true
        // @MappedEntity is itself annotated with @Introspected
        entity.hasStereotype(Introspected) >> true
        return entity
    }

    /**
     * A placeholder that does not expose the annotations of its bound, only {@link
     * GenericPlaceholderElement#getBounds()}.
     */
    private GenericPlaceholderElement placeholderElement(ClassElement bound) {
        GenericPlaceholderElement placeholder = Stub(GenericPlaceholderElement)
        placeholder.getName() >> "example.S"
        placeholder.getVariableName() >> "S"
        placeholder.isArray() >> false
        placeholder.hasStereotype(MappedEntity) >> false
        placeholder.hasStereotype(Introspected) >> false
        placeholder.getResolved() >> Optional.empty()
        placeholder.getBounds() >> [bound]
        return placeholder
    }

    private ClassElement iterableOf(ClassElement typeArgument) {
        ClassElement iterable = Stub(ClassElement)
        iterable.getName() >> Iterable.name
        iterable.isArray() >> false
        iterable.isAssignable(Iterable) >> true
        iterable.getFirstTypeArgument() >> Optional.of(typeArgument)
        return iterable
    }

    private MethodElement methodReturning(ClassElement returnType) {
        MethodElement methodElement = Stub(MethodElement)
        methodElement.getGenericReturnType() >> returnType
        methodElement.getReturnType() >> returnType
        methodElement.isSuspend() >> false
        return methodElement
    }

    void "test an entity placeholder is recognised as an entity"() {
        given:
        def bound = entityElement()
        def placeholder = placeholderElement(bound)

        expect:
        TypeUtils.resolveTypeBound(placeholder).name == ENTITY_NAME
        TypeUtils.isEntity(placeholder)
        TypeUtils.isEntityOrDto(placeholder)
        TypeUtils.isEntityOfType(placeholder, bound)
    }

    void "test an iterable of an entity placeholder is recognised as an iterable of entity"() {
        given:
        def bound = entityElement()
        def iterable = iterableOf(placeholderElement(bound))

        expect:
        TypeUtils.isIterableOfEntity(iterable)
        TypeUtils.hasPersistedTypeArgument(iterable)
        TypeUtils.isIterableOfEntityType(iterable, bound)
    }

    void "test a save method returning an entity placeholder produces an entity"() {
        given: "the return type of <S extends Book> S save(S entity)"
        def methodElement = methodReturning(placeholderElement(entityElement()))

        expect: "the return type is supported by SaveMethodMatcher"
        TypeUtils.doesMethodProducesAnEntityIterableOfAnEntity(methodElement)
        !TypeUtils.doesReturnVoid(methodElement)
        !TypeUtils.doesMethodProducesANumber(methodElement)
    }

    void "test a save method returning an iterable of an entity placeholder produces an entity"() {
        given: "the return type of <S extends Book> Iterable<S> saveAll(Iterable<S> entities)"
        def methodElement = methodReturning(iterableOf(placeholderElement(entityElement())))

        expect: "the return type is supported by SaveMethodMatcher"
        TypeUtils.doesMethodProducesAnEntityIterableOfAnEntity(methodElement)
        !TypeUtils.doesReturnVoid(methodElement)
        !TypeUtils.doesMethodProducesANumber(methodElement)
    }

    void "test a placeholder without a resolvable bound is left alone"() {
        given:
        GenericPlaceholderElement placeholder = Stub(GenericPlaceholderElement)
        placeholder.getName() >> "example.S"
        placeholder.getResolved() >> Optional.empty()
        placeholder.getBounds() >> []

        expect:
        TypeUtils.resolveTypeBound(placeholder).is(placeholder)
        !TypeUtils.isEntity(placeholder)
    }
}
