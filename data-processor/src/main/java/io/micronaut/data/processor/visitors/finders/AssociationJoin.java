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
package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.Join;

/**
 * Defines how a join for a particular association path should be generated.
 * @author Sergio del Amo
 */
public class AssociationJoin {

    /**
     * The path to join.
     */
    @NonNull
    private String path;

    /**
     * The alias prefix to use for the join.
     */
    private String alias;

    /**
     * The join Type.
     */
    @NonNull
    private Join.Type joinType;

    /**
     * Constructor.
     */
    public AssociationJoin() {
    }

    /**
     *
     * @param path The Path to Join
     * @param alias The alias prefix to use for the join
     * @param joinType The join Type
     */
    public AssociationJoin(@NonNull String path, String alias, @NonNull Join.Type joinType) {
        this.path = path;
        this.alias = alias;
        this.joinType = joinType;
    }

    /**
     * @return The path to join.
     */
    @NonNull
    public String getPath() {
        return path;
    }

    /**
     *
     * @param path The Path to Join
     */
    public void setPath(@NonNull String path) {
        this.path = path;
    }

    /**
     *
     * @return The alias prefix to use for the join
     */
    public String getAlias() {
        return alias;
    }

    /**
     *
     * @param alias The alias prefix to use for the join
     */
    public void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     *
     * @return The join Type
     */
    @NonNull
    public Join.Type getJoinType() {
        return joinType;
    }

    /**
     *
     * @param joinType The join Type
     */
    public void setJoinType(@NonNull Join.Type joinType) {
        this.joinType = joinType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AssociationJoin that = (AssociationJoin) o;

        if (!path.equals(that.path)) {
            return false;
        }
        if (alias != null ? !alias.equals(that.alias) : that.alias != null) {
            return false;
        }
        return joinType == that.joinType;
    }

    @Override
    public int hashCode() {
        int result = path.hashCode();
        result = 31 * result + (alias != null ? alias.hashCode() : 0);
        result = 31 * result + joinType.hashCode();
        return result;
    }
}
