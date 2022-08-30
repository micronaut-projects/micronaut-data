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
package io.micronaut.data.model;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.inject.ExecutableMethod;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Internal association utilities.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public final class AssociationUtils {
    /**
     * The split pattern for camel case.
     */
    static final Pattern CAMEL_CASE_SPLIT_PATTERN = Pattern.compile("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])");

    private AssociationUtils() { }

    /**
     * Gets the  join paths from the method.
     * @param method the method
     * @return the join paths, if none defined and not of type FETCH then an empty set
     */
    public static Set<JoinPath> getJoinFetchPaths(ExecutableMethod<?, ?> method) {
        return getJoinFetchPaths(method, true);
    }

    /**
     * Gets the join paths from the method annotations where annotations can be declared on the class or the method.
     * @param method the method
     * @param ignoreJoinType an indicator telling whether join type in the annotation is ignored and then {@link Join.Type#DEFAULT} will be used
     * @return the join paths, if none defined and not of type FETCH then an empty set
     */
    public static Set<JoinPath> getJoinFetchPaths(ExecutableMethod<?, ?> method, boolean ignoreJoinType) {
        return method.getAnnotationValuesByType(Join.class).stream().filter(
            AssociationUtils::isJoinFetch
        ).map(av -> {
            String path = av.stringValue().orElseThrow(() -> new IllegalStateException("Should not include annotations without a value definition"));
            String alias = av.stringValue("alias").orElse(null);
            Join.Type joinType;
            if (ignoreJoinType) {
                joinType = Join.Type.DEFAULT;
            } else {
                joinType = av.get("type", Join.Type.class).orElse(Join.Type.DEFAULT);
            }
            return new JoinPath(path, new Association[0], joinType, alias);
        }).collect(Collectors.toSet());
    }

    private static boolean isJoinFetch(AnnotationValue<Join> av) {
        if (!av.stringValue().isPresent()) {
            return false;
        }
        Optional<String> type = av.stringValue("type");
        return !type.isPresent() || type.get().contains("FETCH");
    }
}
