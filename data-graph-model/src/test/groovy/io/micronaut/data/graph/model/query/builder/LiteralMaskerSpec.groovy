package io.micronaut.data.graph.model.query.builder

import spock.lang.Specification
import spock.lang.Unroll

/**
 * Checks that string literals and comments are masked, keeping offsets, in Cypher and in SQL.
 */
class LiteralMaskerSpec extends Specification {

    @Unroll
    void "mask Cypher #description"() {
        expect:
        LiteralMasker.mask(query) == masked
        masked.length() == query.length()

        where:
        description         | query                                     | masked
        'plain query'       | 'MATCH (n) RETURN n'                      | 'MATCH (n) RETURN n'
        'single quotes'     | "WHERE n.name = '\$x' RETURN n"           | "WHERE n.name = '  ' RETURN n"
        'double quotes'     | 'WHERE n.name = "RETURN" RETURN n'        | 'WHERE n.name = "      " RETURN n'
        'backticks'         | 'MATCH (n:`My Label`) RETURN n'           | 'MATCH (n:`        `) RETURN n'
        'escaped quote'     | "WHERE n.name = 'it\\'s' RETURN n"        | "WHERE n.name = '     ' RETURN n"
        'line comment'      | 'MATCH (n) // DELETE n\nRETURN n'         | 'MATCH (n) ' + (' ' * 11) + '\nRETURN n'
        'block comment'     | 'MATCH (n) /* SET \$p */ RETURN n'        | 'MATCH (n)              RETURN n'
        'comment in quotes' | "RETURN '// not a comment' AS c"          | "RETURN '" + (' ' * 16) + "' AS c"
        'quotes in comment' | "RETURN n // it's\n, m"                   | "RETURN n " + (' ' * 7) + "\n, m"
        'SQL comment'       | 'RETURN n -- RETURN m'                    | 'RETURN n -- RETURN m'
        'trailing escape'   | "RETURN '\\"                              | "RETURN ' "
        'unclosed comment'  | 'RETURN n /* x'                           | 'RETURN n     '
    }

    @Unroll
    void "mask SQL #description"() {
        expect:
        LiteralMasker.mask(query, true) == masked
        masked.length() == query.length()

        where:
        description         | query                                     | masked
        'doubled quote'     | "WHERE n.name = 'it''s' RETURN n"         | "WHERE n.name = '  '' ' RETURN n"
        'no backslash'      | "WHERE n.name = 'a\\' RETURN n"           | "WHERE n.name = '  ' RETURN n"
        'SQL line comment'  | 'SELECT n -- RETURN m\nFROM t'            | 'SELECT n ' + (' ' * 11) + '\nFROM t'
        'Cypher comment'    | 'SELECT n // RETURN m\nFROM t'            | 'SELECT n ' + (' ' * 11) + '\nFROM t'
        'block comment'     | 'SELECT n /* x */ FROM t'                 | 'SELECT n ' + (' ' * 7) + ' FROM t'
        'dash in query'     | 'SELECT a - b FROM t'                     | 'SELECT a - b FROM t'
    }
}
