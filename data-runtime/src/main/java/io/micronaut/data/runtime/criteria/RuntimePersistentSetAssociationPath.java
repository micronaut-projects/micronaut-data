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
package io.micronaut.data.runtime.criteria;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentSetAssociationPath;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;

import java.util.List;

/**
 * The runtime set associated path.
 *
 * @param <Owner> The association owner type
 * @param <E>     The association entity type
 * @author Denis Stepanov
 * @since 3.5
 */
@Internal
final class RuntimePersistentSetAssociationPath<Owner, E> extends RuntimePersistentAssociationPath<Owner, E>
    implements PersistentSetAssociationPath<Owner, E> {

    RuntimePersistentSetAssociationPath(Path<?> parentPath,
                                        RuntimeAssociation<Owner> association,
                                        List<Association> associations,
                                        Join.Type associationJoinType,
                                        String alias,
                                        CriteriaBuilder criteriaBuilder) {
        super(parentPath, association, associations, associationJoinType, alias, criteriaBuilder);
    }

}
