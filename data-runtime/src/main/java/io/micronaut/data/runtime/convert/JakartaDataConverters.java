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
package io.micronaut.data.runtime.convert;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.CursoredPageable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.jd.SpecificationConstraint;
import io.micronaut.data.model.jpa.criteria.impl.ExpressionOrder;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.runtime.date.DateTimeProvider;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.page.PageRequest;
import jakarta.data.page.impl.CursoredPageRecord;
import jakarta.data.page.impl.PageRecord;
import jakarta.data.restrict.Restriction;
import jakarta.inject.Provider;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Jakarta Data converters.
 *
 * @author Denis Stepanov
 * @since 4.13
 */
@Requires(classes = Order.class)
@Prototype
@Internal
final class JakartaDataConverters implements TypeConverterRegistrar {

    private final DateTimeProvider dateTimeProvider;
    private final Provider<RuntimeEntityRegistry> runtimeEntityRegistry;

    JakartaDataConverters(DateTimeProvider dateTimeProvider, Provider<RuntimeEntityRegistry> runtimeEntityRegistry) {
        this.dateTimeProvider = dateTimeProvider;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public void register(MutableConversionService conversionService) {
        JakartaDataRestrictionsConverter restrictionsConverter = new JakartaDataRestrictionsConverter(dateTimeProvider);
        conversionService.addConverter(Restriction.class, PredicateSpecification.class, restrictionsConverter);
        conversionService.addConverter(SpecificationConstraint.class, PredicateSpecification.class, new JakartaDataConstraintConverter(dateTimeProvider, runtimeEntityRegistry));
        conversionService.addConverter(Limit.class, io.micronaut.data.model.Limit.class,
            limit -> io.micronaut.data.model.Limit.of(limit.maxResults(), (int) limit.startAt() - 1));
        conversionService.addConverter(Order.class, Sort.class, order -> Sort.of(
            ((Order<?>) order).sorts().stream().map(sort -> toOrder(sort, restrictionsConverter)).toList()
        ));
        conversionService.addConverter(jakarta.data.Sort.class, Sort.class,
            sort -> Sort.of(toOrder(sort, restrictionsConverter))
        );
        conversionService.addConverter(jakarta.data.Sort[].class, Sort.class, sorts -> Sort.of(
                Arrays.stream(sorts).map(sort -> toOrder(sort, restrictionsConverter)).toList()
            )
        );
        conversionService.addConverter(jakarta.data.page.PageRequest.class, Pageable.class, pageRequest -> {
            if (pageRequest.mode() == PageRequest.Mode.CURSOR_NEXT || pageRequest.mode() == PageRequest.Mode.CURSOR_PREVIOUS) {
                return CursoredPageable.from(
                    (int) (pageRequest.page() - 1),
                    pageRequest.cursor().map(cursor -> Pageable.Cursor.of((List<Object>) cursor.elements())).orElse(null),
                    pageRequest.mode() == PageRequest.Mode.CURSOR_NEXT ? Pageable.Mode.CURSOR_NEXT : Pageable.Mode.CURSOR_PREVIOUS,
                    pageRequest.size(),
                    null,
                    pageRequest.requestTotal()
                );
            } else {
                Pageable pageable = Pageable.from((int) (pageRequest.page() - 1), pageRequest.size());
                if (pageRequest.requestTotal()) {
                    pageable = pageable.withTotal();
                } else {
                    pageable = pageable.withoutTotal();
                }
                return pageable;
            }
        });
        conversionService.addConverter(Pageable.class, jakarta.data.page.PageRequest.class, JakartaDataConverters::convert);
        conversionService.addConverter(Page.class, jakarta.data.page.Page.class, page ->
            new PageRecord<>(
                convert(page.getPageable()),
                page.getContent(),
                page.getPageable().requestTotal() ? page.getTotalSize() : -1
            )
        );
        conversionService.addConverter(CursoredPage.class, jakarta.data.page.CursoredPage.class, page -> {
                CursoredPage<?> cursoredPage = (CursoredPage<?>) page;
                return new CursoredPageRecord<>(
                    cursoredPage.getContent(),
                    cursoredPage.getCursors().stream().map(JakartaDataConverters::convertCursor).toList(),
                    cursoredPage.hasTotalSize() ? cursoredPage.getTotalSize() : -1L,
                    convert(cursoredPage.getPageable()),
                    convert(cursoredPage.nextPageable()),
                    convert(cursoredPage.previousPageable())
                );
            }
        );
    }

    private static PageRequest.Cursor convertCursor(Pageable.Cursor c) {
        return PageRequest.Cursor.forKey(c.elements().toArray());
    }

    @Nullable
    private static PageRequest convert(@Nullable CursoredPageable pageable) {
        if (pageable == null) {
            return null;
        }
        PageRequest.Cursor cursor = pageable.cursor().map(JakartaDataConverters::convertCursor).orElse(null);
        if (cursor == null) {
            return null;
        }
        if (pageable.getMode().equals(CursoredPageable.Mode.CURSOR_NEXT)) {
            return PageRequest.afterCursor(cursor, pageable.getNumber() + 1, pageable.getSize(), pageable.requestTotal());
        }
        if (pageable.getMode().equals(Pageable.Mode.CURSOR_PREVIOUS)) {
            return PageRequest.beforeCursor(cursor, pageable.getNumber() + 1, pageable.getSize(), pageable.requestTotal());
        }
        throw new IllegalArgumentException("Unknown mode " + pageable.getMode());
    }

    @Nullable
    private static PageRequest convert(@Nullable Pageable pageable) {
        if (pageable == null) {
            return null;
        }
        return PageRequest.ofPage(pageable.getNumber() + 1, pageable.getSize() == -1 ? Integer.MAX_VALUE : pageable.getSize(), pageable.requestTotal());
    }

    private static Sort.Order toOrder(jakarta.data.Sort<?> sort, JakartaDataRestrictionsConverter restrictionsConverter) {
        Sort.Order.Direction direction = sort.isAscending() ? Sort.Order.Direction.ASC : Sort.Order.Direction.DESC;
        Sort.Order.NullOrdering nullOrdering = switch (sort.nullOrdering()) {
            case FIRST -> Sort.Order.NullOrdering.FIRST;
            case LAST -> Sort.Order.NullOrdering.LAST;
            case UNSPECIFIED -> Sort.Order.NullOrdering.NONE;
        };
        jakarta.data.expression.Expression<?, ?> expression = sort.expression();
        String property = sort.property();
        if (property == null) {
            // Sorting by an expression rather than by an attribute name
            return new ExpressionOrder(
                String.valueOf(expression),
                direction,
                sort.ignoreCase(),
                nullOrdering,
                (root, criteriaBuilder) -> restrictionsConverter.toCriteriaExpression(root, criteriaBuilder, expression)
            );
        }
        return new Sort.Order(property, direction, sort.ignoreCase(), nullOrdering);
    }
}
