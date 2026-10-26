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
package io.micronaut.data.runtime.schema;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.runtime.config.SchemaGenerate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The contributions of the {@link SchemaContributor} beans to the schema generation of one data source.
 *
 * @param tableEntities          The entities whose tables the schema generator generates
 * @param dropStatements         The drop statements, run first with {@code CREATE_DROP}
 * @param beforeTablesStatements The statements run before the tables are created
 * @param createStatements       The statements run after the tables are created
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public record SchemaContributions(PersistentEntity[] tableEntities,
                                  List<String> dropStatements,
                                  List<String> beforeTablesStatements,
                                  List<String> createStatements) {

    /**
     * Collects the contributions for a data source.
     *
     * @param contributors The contributors, in order
     * @param context      The schema generation of the data source, with all its entities
     * @return The contributions
     */
    public static SchemaContributions of(Collection<? extends SchemaContributor> contributors, SchemaContext context) {
        List<PersistentEntity> remaining = new ArrayList<>(context.entities());
        List<String> drop = new ArrayList<>();
        List<String> beforeTables = new ArrayList<>();
        List<String> create = new ArrayList<>();
        boolean generates = context.schemaGenerate() == SchemaGenerate.CREATE || context.schemaGenerate() == SchemaGenerate.CREATE_DROP;
        for (SchemaContributor contributor : contributors) {
            List<PersistentEntity> selected = List.copyOf(contributor.selectEntities(context, List.copyOf(remaining)));
            remaining.removeAll(selected);
            if (!generates) {
                continue;
            }
            if (context.schemaGenerate() == SchemaGenerate.CREATE_DROP) {
                drop.addAll(contributor.buildDropStatements(context, selected));
            }
            beforeTables.addAll(contributor.buildBeforeTablesStatements(context, selected));
            create.addAll(contributor.buildCreateStatements(context, selected));
        }
        return new SchemaContributions(remaining.toArray(PersistentEntity[]::new), List.copyOf(drop), List.copyOf(beforeTables), List.copyOf(create));
    }

    /**
     * @return Whether there are no tables to generate and no contributed statements
     */
    public boolean isEmpty() {
        return tableEntities.length == 0 && dropStatements.isEmpty() && beforeTablesStatements.isEmpty() && createStatements.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SchemaContributions(PersistentEntity[] otherTables, List<String> otherDrop, List<String> otherBeforeTables, List<String> otherCreate)
            && Arrays.equals(tableEntities, otherTables)
            && dropStatements.equals(otherDrop)
            && beforeTablesStatements.equals(otherBeforeTables)
            && createStatements.equals(otherCreate);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(tableEntities) + dropStatements.hashCode() + beforeTablesStatements.hashCode() * 7 + createStatements.hashCode() * 17;
    }

    @Override
    public String toString() {
        return "SchemaContributions{tables=" + tableEntities.length + ", drop=" + dropStatements + ", beforeTables=" + beforeTablesStatements + ", create=" + createStatements + "}";
    }
}
