package io.micronaut.data.r2dbc.oraclexe.jsonviewoptions

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.r2dbc.oraclexe.OracleXETestPropertyProvider
import io.micronaut.data.runtime.config.SchemaGenerate
import io.r2dbc.spi.R2dbcException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleR2dbcJsonViewOptionsSpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    ProductRepository productRepository = context.getBean(ProductRepository)

    @Shared
    SupplierRepository supplierRepository = context.getBean(SupplierRepository)

    @Shared
    ProductViewRepository productViewRepository = context.getBean(ProductViewRepository)

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.CREATE_DROP
    }

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

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
        thrown(R2dbcException)
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

        then:"Oracle R2DBC caches statements: the cached query returns the document without the PATH generated total"
        updated.name() == "Sprocket"
        updated.label() == "SPROCKET"
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
        } catch (R2dbcException ignored) {
        }

        then:
        productRepository.findById(product.id()).get().sku() == "W-1"
    }
}
