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
package io.micronaut.data.runtime.intercept.criteria;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.List;

/**
 * Runtime implementation of {@code Page find(Specification, Pageable)}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public class FindPageSpecificationInterceptor extends AbstractSpecificationInterceptor<Object, Object> {

    /**
     * Default constructor.
     *
     * @param operations            The operations
     */
    protected FindPageSpecificationInterceptor(RepositoryOperations operations) {
        super(operations);
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        if (context.getParameterValues().length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }

        Pageable pageable = getPageable(context);
        if (pageable.isUnpaged()) {
            PreparedQuery<?, ?> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.FIND_PAGE);
            Iterable<?> iterable = operations.findAll(preparedQuery);
            List<Object> resultList = (List<Object>) CollectionUtils.iterableToList(iterable);
            return Page.of(
                    resultList,
                    pageable,
                    resultList.size()
            );
        }
        PreparedQuery<?, ?> preparedQuery = preparedQueryForCriteria(methodKey, context, Type.FIND_PAGE);
        PreparedQuery<?, Number> countQuery = preparedQueryForCriteria(methodKey, context, Type.COUNT);

        Iterable<?> iterable = operations.findAll(preparedQuery);
        List<Object> resultList = (List<Object>) CollectionUtils.iterableToList(iterable);

        Number count = operations.findOne(countQuery);

        Page page = Page.of(resultList, getPageable(context), count != null ? count.longValue() : 0);
        Class<Object> rt = context.getReturnType().getType();
        if (rt.isInstance(page)) {
            return page;
        }
        return operations.getConversionService().convert(page, rt).orElseThrow(() -> new IllegalStateException("Unsupported page interface type " + rt));
    }

}
