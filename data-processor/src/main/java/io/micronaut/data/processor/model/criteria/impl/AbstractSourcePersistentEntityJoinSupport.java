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
package io.micronaut.data.processor.model.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityJoinSupport;
import io.micronaut.data.processor.model.SourceAssociation;

import java.util.List;

/**
 * The internal source implementation of {@link AbstractPersistentEntityJoinSupport}.
 *
 * @param <T> The association entity type
 * @param <J>     The association entity type
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
abstract class AbstractSourcePersistentEntityJoinSupport<T, J> extends AbstractPersistentEntityJoinSupport<T, J> {

    protected abstract List<Association> getCurrentPath();

    @Override
    protected <X, Y> PersistentAssociationPath<X, Y> createJoinAssociation(Association association,
                                                                           io.micronaut.data.annotation.Join.Type associationJoinType,
                                                                           String alias) {
        return new SourcePersistentAssociationPath<>(this, (SourceAssociation) association, getCurrentPath(), associationJoinType, alias);
    }

}
