/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model.query;

import io.micronaut.core.naming.Named;
import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A parameter to a query.
 *
 * @author graemerocher
 * @since 1.0
 */
public class QueryParameter implements Named {

    private final String name;

    public QueryParameter(@NonNull String name) {
        ArgumentUtils.requireNonNull("name", name);
        this.name = name;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryParameter that = (QueryParameter) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Creates a new query parameter for the given name
     * @param name The name
     * @return The parameter
     */
    public static @NonNull QueryParameter of(@NonNull String name) {
        return new QueryParameter(name);
    }
}
