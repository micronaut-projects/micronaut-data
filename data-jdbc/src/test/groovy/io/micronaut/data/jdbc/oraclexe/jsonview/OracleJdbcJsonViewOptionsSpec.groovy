package io.micronaut.data.jdbc.oraclexe.jsonview

import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(environments = ["oracle-jsonview"])
class OracleJdbcJsonViewOptionsSpec extends Specification {

    @Inject
    ProductRepository productRepository

    @Inject
    SupplierRepository supplierRepository

    @Inject
    ProductViewRepository productViewRepository

    def setup() {
        productRepository.deleteAll()
        supplierRepository.deleteAll()
    }

    void "test the duality view DDL includes the where, hidden, generated and field options"() {
        when:
        def builder = new SqlQueryBuilder(Dialect.ORACLE)
        String[] sql = builder.buildCreateTableStatements(new RuntimePersistentEntity(ProductView))

        then:
        sql[0] == "CREATE OR REPLACE JSON RELATIONAL DUALITY VIEW product_view AS SELECT JSON {" +
                "'_id': pr.id, 'name': pr.name, 'region': pr.region, 'sku': pr.sku WITH NOUPDATE, " +
                "'total': GENERATED USING PATH '\$.price * \$.quantity', 'label': GENERATED USING (UPPER(pr.name)), " +
                "'supplier': (SELECT JSON {'supplierId': su.id, 'name': su.name, " +
                "'domain': GENERATED USING (SUBSTR(su.contact_email, INSTR(su.contact_email, '@') + 1)), " +
                "'contactEmail': su.contact_email HIDDEN} FROM TBL_SUPPLIER su WITH UPDATE NOCHECK  WHERE pr.\"SUPPLIER_ID\"=su.\"ID\"), " +
                "'price': pr.price HIDDEN, 'quantity': pr.quantity HIDDEN} " +
                "FROM TBL_PRODUCT pr WITH UPDATE INSERT DELETE  WHERE pr.region = 'EU' WITH CHECK OPTION"
    }

    void "test reading a duality view with a where filter, hidden and generated fields"() {
        given:
        def acme = supplierRepository.save(new Supplier(null, "Acme", "sales@acme.example"))
        productRepository.save(new Product(null, "Widget", "EU", 2.5d, 4, "W-1", acme))
        productRepository.save(new Product(null, "Gadget", "US", 10d, 1, "G-1", acme))

        when:
        def views = productViewRepository.findAll()

        then:
        views.size() == 1
        with(views[0]) {
            name() == "Widget"
            region() == "EU"
            total() == 10d
            label() == "WIDGET"
            supplier().name() == "Acme"
            supplier().domain() == "acme.example"
        }
    }

    void "test the where filter is enforced with check option"() {
        given:
        def acme = supplierRepository.save(new Supplier(null, "Acme", "sales@acme.example"))
        def product = productRepository.save(new Product(null, "Widget", "EU", 2.5d, 4, "W-1", acme))
        def view = productViewRepository.findById(product.id()).get()

        when:
        productViewRepository.update(new ProductView(view.id(), view.name(), "US", view.sku(), null, null, view.supplier()))

        then:
        thrown(DataAccessException)
        productRepository.findById(product.id()).get().region() == "EU"
    }

    void "test updating through the view keeps hidden columns and refreshes generated fields"() {
        given:
        def acme = supplierRepository.save(new Supplier(null, "Acme", "sales@acme.example"))
        def product = productRepository.save(new Product(null, "Widget", "EU", 2.5d, 4, "W-1", acme))
        def view = productViewRepository.findById(product.id()).get()

        when:
        productViewRepository.update(new ProductView(view.id(), "Sprocket", view.region(), view.sku(), 99d, "STALE", view.supplier()))
        def updated = productViewRepository.findById(product.id()).get()
        def row = productRepository.findById(product.id()).get()

        then:
        updated.name() == "Sprocket"
        updated.label() == "SPROCKET"
        updated.total() == 10d
        row.price() == 2.5d
        row.quantity() == 4
    }

    void "test a field that is not updatable cannot be changed through the view"() {
        given:
        def acme = supplierRepository.save(new Supplier(null, "Acme", "sales@acme.example"))
        def product = productRepository.save(new Product(null, "Widget", "EU", 2.5d, 4, "W-1", acme))
        def view = productViewRepository.findById(product.id()).get()

        when:"the view has a WHERE clause Oracle ignores the change instead of failing with ORA-40940"
        try {
            productViewRepository.update(new ProductView(view.id(), view.name(), view.region(), "W-2", null, null, view.supplier()))
        } catch (DataAccessException ignored) {
        }

        then:
        productRepository.findById(product.id()).get().sku() == "W-1"
    }
}
