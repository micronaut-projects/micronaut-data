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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.query.builder.QueryOutParameterBinding;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchFailedException;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.TypedElement;
import jakarta.persistence.Tuple;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Oracle raw query support for parsing and transforming {@code RETURNING ... INTO} clauses.
 *
 * @author Radovan Radic
 * @since 5.0
 */
@Internal
final class OracleRawQueryReturningSupport {

    private OracleRawQueryReturningSupport() {
    }

    static QueryResult buildQueryResult(String finalQueryString,
                                        List<String> queryParts,
                                        List<QueryParameterBinding> parameterBindings,
                                        SourcePersistentEntity entity,
                                        @Nullable TypedElement resultType,
                                        BiFunction<String, DataType, QueryOutParameterBinding> outBindingFactory) {
        OracleReturningClause returningClause = parseReturningClause(finalQueryString);
        if (returningClause.intoClause() == null) {
            throw new MatchFailedException("Oracle raw queries with RETURNING must declare explicit returned columns and an INTO clause with positional '?' placeholders");
        }
        OracleReturningBindings returningBindings = resolveReturningBindings(entity, returningClause.selection(), resultType, outBindingFactory);
        validateIntoClause(returningClause.intoClause(), returningBindings.outBindings().size());
        return buildExplicitQueryResult(finalQueryString, queryParts, parameterBindings, returningBindings.outBindings());
    }

    private static QueryResult buildExplicitQueryResult(String finalQueryString,
                                                        List<String> queryParts,
                                                        List<QueryParameterBinding> parameterBindings,
                                                        List<QueryOutParameterBinding> outBindings) {
        if (isAnonymousBlock(finalQueryString)) {
            return QueryResult.of(finalQueryString, queryParts, parameterBindings, outBindings, Map.of());
        }
        if (!parameterBindings.isEmpty()) {
            wrapQueryPartsInOracleBlock(queryParts);
            return QueryResult.of(assembleSqlFromQueryParts(queryParts), queryParts, parameterBindings, outBindings, Map.of());
        }
        String wrappedSql = wrapSqlInOracleBlock(finalQueryString);
        return QueryResult.of(wrappedSql, List.of(wrappedSql), parameterBindings, outBindings, Map.of());
    }

    private static boolean isAnonymousBlock(String query) {
        String trimmed = query.trim().toLowerCase(Locale.ENGLISH);
        return trimmed.startsWith("begin") && trimmed.endsWith("end;");
    }

    private static OracleReturningClause parseReturningClause(String finalQueryString) {
        String withoutSemicolon = stripTrailingSemicolon(finalQueryString).trim();
        int returningIdx = lastKeywordOutsideQuotes(withoutSemicolon, "returning");
        if (returningIdx < 0) {
            throw new MatchFailedException("Oracle RETURNING clause was not found in query: " + finalQueryString);
        }
        int selectionStart = returningIdx + "returning".length();
        int statementEndIdx = firstKeywordOutsideQuotes(withoutSemicolon, ";", selectionStart);
        int clauseEndIdx = statementEndIdx > -1 ? statementEndIdx : withoutSemicolon.length();
        int intoIdx = firstKeywordOutsideQuotes(withoutSemicolon, "into", selectionStart);
        String selection;
        String intoClause = null;
        if (intoIdx > -1 && intoIdx < clauseEndIdx) {
            selection = withoutSemicolon.substring(selectionStart, intoIdx).trim();
            intoClause = withoutSemicolon.substring(intoIdx + "into".length(), clauseEndIdx).trim();
            intoClause = stripTrailingSemicolon(intoClause).trim();
        } else {
            selection = withoutSemicolon.substring(selectionStart, clauseEndIdx).trim();
        }
        return new OracleReturningClause(selection, intoClause);
    }

    private static int firstKeywordOutsideQuotes(String query, String keyword, int startIndex) {
        return findKeywordOutsideQuotes(query, keyword, startIndex, false);
    }

    private static int lastKeywordOutsideQuotes(String query, String keyword) {
        return findKeywordOutsideQuotes(query, keyword, 0, true);
    }

    private static int findKeywordOutsideQuotes(String query, String keyword, int startIndex, boolean findLast) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        int match = -1;
        int keywordLength = keyword.length();
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (inSingleQuote) {
                if (c == '\'' && (i + 1 >= query.length() || query.charAt(i + 1) != '\'')) {
                    inSingleQuote = false;
                } else if (c == '\'' && i + 1 < query.length() && query.charAt(i + 1) == '\'') {
                    i++;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (c == '"' && (i + 1 >= query.length() || query.charAt(i + 1) != '"')) {
                    inDoubleQuote = false;
                } else if (c == '"' && i + 1 < query.length() && query.charAt(i + 1) == '"') {
                    i++;
                }
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                continue;
            }
            if (c == '"') {
                inDoubleQuote = true;
                continue;
            }
            if (i < startIndex) {
                continue;
            }
            if (query.regionMatches(true, i, keyword, 0, keywordLength) && isKeywordBoundary(query, i - 1) && isKeywordBoundary(query, i + keywordLength)) {
                if (!findLast) {
                    return i;
                }
                match = i;
            }
        }
        return match;
    }

    private static boolean isKeywordBoundary(String query, int index) {
        if (index < 0 || index >= query.length()) {
            return true;
        }
        char c = query.charAt(index);
        return !Character.isLetterOrDigit(c) && c != '_';
    }

    private static OracleReturningBindings resolveReturningBindings(SourcePersistentEntity entity,
                                                                   String selection,
                                                                   @Nullable TypedElement resultType,
                                                                   BiFunction<String, DataType, QueryOutParameterBinding> outBindingFactory) {
        List<QueryOutParameterBinding> outBindings = new ArrayList<>();
        List<String> outColumns = new ArrayList<>();
        List<String> parts = splitByComma(selection).stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        if (parts.isEmpty() || (parts.size() == 1 && parts.get(0).equals("*"))) {
            throw new MatchFailedException("Oracle raw queries with RETURNING must declare explicit returned columns instead of RETURNING *");
        }
        validateReturningColumnCount(parts, resultType);
        for (String part : parts) {
            String col = canonicalizeReturningColumn(entity, normalizeReturnedColumn(part));
            outColumns.add(col);
            outBindings.add(outBindingFactory.apply(col, resolveReturningDataType(entity, col)));
        }
        if (outBindings.isEmpty()) {
            throw new MatchFailedException("RETURNING clause must contain at least one column for Oracle");
        }
        return new OracleReturningBindings(outColumns, outBindings);
    }

    private static void validateReturningColumnCount(List<String> parts, @Nullable TypedElement resultType) {
        if (parts.size() > 1 && !supportsMultiColumnReturning(resultType)) {
            throw new MatchFailedException("Oracle raw queries returning multiple columns require an entity return type");
        }
    }

    private static boolean supportsMultiColumnReturning(@Nullable TypedElement resultType) {
        if (resultType == null) {
            return false;
        }
        if (resultType instanceof ClassElement classElement) {
            if (TypeUtils.isEntity(classElement) || TypeUtils.isIterableOfEntity(classElement)) {
                return true;
            }
            if (TypeUtils.isDto(classElement) || TypeUtils.isIterableOfDto(classElement)) {
                return true;
            }
            if (Tuple.class.getName().equals(classElement.getName())) {
                return true;
            }
            if (classElement.isArray() && Object.class.getName().equals(classElement.fromArray().getName())) {
                return true;
            }
        }
        return false;
    }

    private static void validateIntoClause(String intoClause, int expectedCount) {
        List<String> intoParts = splitByComma(intoClause).stream().map(String::trim).filter(s -> !s.isEmpty()).map(OracleRawQueryReturningSupport::stripTrailingSemicolon).toList();
        if (intoParts.size() != expectedCount) {
            throw new MatchFailedException("Oracle RETURNING ... INTO placeholder count must match returned column count: " + expectedCount + " columns, " + intoParts.size() + " INTO target(s)");
        }
        for (String intoPart : intoParts) {
            if (!"?".equals(intoPart)) {
                throw new MatchFailedException("Oracle raw queries with RETURNING ... INTO must use positional '?' placeholders in the INTO clause: " + intoPart);
            }
        }
    }

    private static DataType resolveReturningDataType(SourcePersistentEntity entity, String col) {
        DataType dt = DataType.STRING;
        SourcePersistentProperty prop = resolveReturningProperty(entity, col);
        if (prop != null) {
            if (prop instanceof Association assocProp) {
                try {
                    var ae = assocProp.getAssociatedEntity();
                    if (ae != null && ae.hasIdentity()) {
                        dt = ae.getIdentity().getDataType();
                    }
                } catch (Exception ignored) {
                    dt = DataType.STRING;
                }
            } else if (prop.getDataType() != null) {
                dt = prop.getDataType();
            }
        }
        return dt;
    }

    private static String canonicalizeReturningColumn(SourcePersistentEntity entity, String col) {
        SourcePersistentProperty prop = resolveReturningProperty(entity, col);
        return prop != null ? prop.getPersistedName() : col;
    }

    @Nullable
    private static SourcePersistentProperty resolveReturningProperty(SourcePersistentEntity entity, String col) {
        var prop = entity.getPropertyByNameIgnoreCase(col);
        if (prop == null) {
            for (var p : entity.getPersistentProperties()) {
                if (p.getPersistedName().equalsIgnoreCase(col)) {
                    prop = p;
                    break;
                }
            }
        }
        return prop;
    }

    private static String normalizeReturnedColumn(String column) {
        if (column.length() < 2) {
            return column;
        }
        char quote = column.charAt(0);
        if ((quote == '"' || quote == '`') && column.charAt(column.length() - 1) == quote) {
            String unquoted = column.substring(1, column.length() - 1);
            if (unquoted.indexOf('.') == -1 && unquoted.indexOf(quote) == -1) {
                return unquoted;
            }
        }
        return column;
    }

    private static void wrapQueryPartsInOracleBlock(List<String> queryParts) {
        if (!queryParts.isEmpty()) {
            queryParts.set(0, "BEGIN " + queryParts.get(0));
        } else {
            queryParts.add("BEGIN ");
        }
        int last = queryParts.size() - 1;
        String tail = last >= 0 ? queryParts.get(last) : "";
        if (last >= 0) {
            queryParts.set(last, stripTrailingSemicolon(tail) + "; END;");
        } else {
            queryParts.add(stripTrailingSemicolon(tail) + "; END;");
        }
    }

    private static String wrapSqlInOracleBlock(String sql) {
        return "BEGIN " + stripTrailingSemicolon(sql) + "; END;";
    }

    private static String assembleSqlFromQueryParts(List<String> queryParts) {
        if (queryParts.size() == 1) {
            return queryParts.get(0);
        }
        var sqlBuilder = new StringBuilder(queryParts.get(0));
        for (int i = 1; i < queryParts.size(); i++) {
            sqlBuilder.append(SqlQueryBuilder.DEFAULT_POSITIONAL_PARAMETER_MARKER).append(queryParts.get(i));
        }
        return sqlBuilder.toString();
    }

    private static String stripTrailingSemicolon(String s) {
        String t = s.trim();
        if (t.endsWith(";")) {
            return t.substring(0, t.length() - 1);
        }
        return s;
    }

    private static List<String> splitByComma(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            }
            if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private record OracleReturningClause(String selection, @Nullable String intoClause) {
    }

    private record OracleReturningBindings(List<String> outColumns, List<QueryOutParameterBinding> outBindings) {
    }
}
