package io.micronaut.data.r2dbc.config

import spock.lang.Specification

class R2dbcSchemaGeneratorDualityViewSpec extends Specification {

    void "other statements are run as they are"() {
        expect:
        R2dbcSchemaGenerator.executeImmediateDualityView("CREATE TABLE t (id NUMBER)") == "CREATE TABLE t (id NUMBER)"
    }

    void "a duality view is appended to a CLOB in literals of at most 4000 characters"() {
        given:
        String view = "CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW v AS SELECT JSON {" +
                (1..2000).collect { "'f$it': GENERATED USING (SUBSTR(t.c, INSTR(t.c, '@') + 1))" }.join(", ") +
                "} FROM t WITH UPDATE"

        when:
        String block = R2dbcSchemaGenerator.executeImmediateDualityView(view)
        def literals = (block =~ /ddl := (?:ddl \|\| )?'([^']*+(?:''[^']*+)*+)';/).collect { it[1] as String }

        then:
        view.length() > 32767
        block.startsWith("DECLARE ddl CLOB; BEGIN ddl := '")
        block.endsWith(" EXECUTE IMMEDIATE ddl; END;")
        literals.size() == Math.ceil(view.length() / 4000d) as int
        literals.every { it.replace("''", "'").length() <= 4000 }
        literals.collect { it.replace("''", "'") }.join("") == view
    }

    void "a surrogate pair is not split between literals"() {
        given:
        String view = "CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW v AS SELECT JSON {'a': t.a} FROM t"
        view = view + ("x" * (3999 - view.length())) + "😀" + " WITH UPDATE"

        when:
        String block = R2dbcSchemaGenerator.executeImmediateDualityView(view)

        then:
        block.contains("x'; ddl := ddl || '😀 WITH UPDATE';")
    }
}
