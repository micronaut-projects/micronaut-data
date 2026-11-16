from micronaut.data.model import PersistentEntity
from micronaut.data.model.query.builder.sql import Dialect, SqlQueryBuilder
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.ShipmentView import ShipmentView


@MicronautTest(startApplication=False)
class ShipmentViewSpec:

    @Test
    def dualityViewDefinition(self):
        sql = SqlQueryBuilder(Dialect.ORACLE).buildCreateTableStatements(PersistentEntity.of(ShipmentView))[0]

        assert sql.startswith("CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW EU_SHIPMENT_VIEW AS SELECT JSON {"), sql
        assert "'trackingCode': sh.tracking_code WITH NOUPDATE" in sql, sql
        assert "'cost': GENERATED USING PATH '$.weight * $.rate'" in sql, sql
        assert "'label': GENERATED USING (UPPER(sh.reference))" in sql, sql
        assert "'weight': sh.weight HIDDEN, 'rate': sh.rate HIDDEN}" in sql, sql
        assert sql.endswith("WHERE sh.country IN ('FR', 'DE', 'IT') WITH CHECK OPTION"), sql
