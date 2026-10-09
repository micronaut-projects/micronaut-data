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
package io.micronaut.data.runtime.operations.internal.sql

import spock.lang.Specification

/**
 * The cursor condition ({@code c > ?} here) is added to the query built so far without parsing it: an upper-case WHERE
 * means the query has a condition. A query built by the query builder already has its condition in parentheses; in a
 * query written by the user, the condition after the first WHERE is wrapped first.
 */
class CursorConditionSpec extends Specification {

    void "#description"() {
        given:
            def sql = new StringBuilder(query)
        when:
            DefaultSqlPreparedQuery.appendCursorCondition(sql, "c > ?", rawQuery)
        then:
            sql.toString() == expected

        where:
            description                                   | rawQuery | query                                         | expected
            "a native query with a WHERE"                 | true     | "SELECT * FROM t WHERE a = ? OR b = ?"        | "SELECT * FROM t WHERE ( a = ? OR b = ?) AND (c > ?)"
            "a native query without a WHERE"              | true     | "SELECT * FROM t"                             | "SELECT * FROM t WHERE (c > ?)"
            "a native query ending in a line comment"     | true     | "SELECT * FROM t WHERE a = ? -- note\n"       | "SELECT * FROM t WHERE ( a = ? -- note\n) AND (c > ?)"
            "a generated query with a WHERE"              | false    | 'SELECT t_."id" FROM "t" t_ WHERE (t_."a" = ? OR t_."b" = ?)' | 'SELECT t_."id" FROM "t" t_ WHERE (t_."a" = ? OR t_."b" = ?) AND (c > ?)'
            "a generated query without a WHERE"           | false    | 'SELECT t_."id" FROM "t" t_'                  | 'SELECT t_."id" FROM "t" t_ WHERE (c > ?)'
            "the pagination subquery of a join finder"    | false    | 'SELECT t_."id" FROM "t" t_ WHERE (t_."id" IN (SELECT p_."id" FROM "t" p_ WHERE (p_."id" IN (SELECT f_."id" FROM "t" f_)))' | 'SELECT t_."id" FROM "t" t_ WHERE (t_."id" IN (SELECT p_."id" FROM "t" p_ WHERE (p_."id" IN (SELECT f_."id" FROM "t" f_))) AND (c > ?)'
    }
}
