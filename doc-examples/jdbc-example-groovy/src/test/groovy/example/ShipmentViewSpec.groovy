package example

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import spock.lang.Specification

class ShipmentViewSpec extends Specification {

    void 'test duality view definition'() {
        when:
        String sql = new SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(PersistentEntity.of(ShipmentView))[0]

        then:
        sql.startsWith("CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW EU_SHIPMENT_VIEW AS SELECT JSON {")
        sql.contains("'trackingCode': sh.tracking_code WITH NOUPDATE")
        sql.contains('\'cost\': GENERATED USING PATH \'$.weight * $.rate\'')
        sql.contains("'label': GENERATED USING (UPPER(sh.reference))")
        sql.contains("'weight': sh.weight HIDDEN, 'rate': sh.rate HIDDEN}")
        sql.endsWith("WHERE sh.country IN ('FR', 'DE', 'IT') WITH CHECK OPTION")
    }
}
