package example

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShipmentViewSpec {

    @Test
    fun testDualityViewDefinition() {
        val sql = SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(PersistentEntity.of(ShipmentView::class.java))[0]

        assertTrue(sql.startsWith("CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW EU_SHIPMENT_VIEW AS SELECT JSON {"), sql)
        assertTrue(sql.contains("'trackingCode': sh.tracking_code WITH NOUPDATE"), sql)
        assertTrue(sql.contains("'cost': GENERATED USING PATH '\$.weight * \$.rate'"), sql)
        assertTrue(sql.contains("'label': GENERATED USING (UPPER(sh.reference))"), sql)
        assertTrue(sql.contains("'weight': sh.weight HIDDEN, 'rate': sh.rate HIDDEN}"), sql)
        assertTrue(sql.endsWith("WHERE sh.country IN ('FR', 'DE', 'IT') WITH CHECK OPTION"), sql)
    }
}
