package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder
import io.micronaut.data.repository.jpa.criteria.DeleteSpecification
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.data.repository.jpa.kotlin.CoroutineJpaSpecificationExecutor
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*
import java.util.UUID

@Repository
interface ProductSpecificationRepository : CoroutineCrudRepository<Product, Long>, CoroutineJpaSpecificationExecutor<Product>

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductRepositoryTest : PostgresHibernateReactiveProperties {

    @Inject
    lateinit var productRepository: ProductRepository
    @Inject
    lateinit var productSpecificationRepository: ProductSpecificationRepository
    @Inject
    lateinit var manufacturerRepository: ManufacturerRepository

    @BeforeAll
    fun setupData(): Unit = runBlocking {
        productRepository.deleteAll()
        manufacturerRepository.deleteAll()
        val apple = manufacturerRepository.save("Apple")
        productRepository.saveAll(listOf(
                Product(null,
                        "MacBook",
                        apple
                ),
                Product(null,
                        "iPhone",
                        apple
                )
        )).toList()
    }

    @Test
    fun testJoinSpec() = runBlocking {
        val list = productRepository.list()
        assertTrue(
            list.all { it.manufacturer.name == "Apple" }
        )
    }

    @Test
    fun testCoroutineSpecificationPaginationWithFetchJoin() = runBlocking {
        val specification = QuerySpecification<Product> { root, query, criteriaBuilder ->
            val manufacturer: Join<Product, Manufacturer> = if (query.resultType != Long::class.javaObjectType) {
                root.fetch<Product, Manufacturer>("manufacturer", JoinType.LEFT) as Join<Product, Manufacturer>
            } else {
                root.join<Product, Manufacturer>("manufacturer", JoinType.LEFT)
            }
            criteriaBuilder.equal(manufacturer.get<String>("name"), "Apple")
        }

        val page0 = productSpecificationRepository.findAll(
            specification,
            Pageable.from(0, 1, Sort.of(Sort.Order.asc("name")))
        )
        val page1 = productSpecificationRepository.findAll(
            specification,
            Pageable.from(1, 1, Sort.of(Sort.Order.asc("name")))
        )

        assertEquals(1, page0.content.size)
        assertEquals(2, page0.totalSize)
        assertEquals("iPhone", page0.content.single().name)
        assertEquals("Apple", page0.content.single().manufacturer.name)
        assertEquals(1, page1.content.size)
        assertEquals(2, page1.totalSize)
        assertEquals("MacBook", page1.content.single().name)
        assertEquals("Apple", page1.content.single().manufacturer.name)
    }

    @Test
    fun testCriteriaCallbacksReceiveHibernateCriteriaBuilder() = runBlocking {
        val callbacks = mutableListOf<String>()
        val prefix = "Criteria Builder ${UUID.randomUUID()}"
        val findName = "$prefix Find"
        val deleteName = "$prefix Delete"
        val deletePredicateName = "$prefix Delete Predicate"
        val updateName = "$prefix Update"
        val updateBuilderName = "$prefix Update Builder"
        val updatedName = "$prefix Updated"
        val updatedBuilderName = "$prefix Updated Builder"
        val manufacturer = manufacturerRepository.save("$prefix Manufacturer")
        listOf(
            findName,
            deleteName,
            deletePredicateName,
            updateName,
            updateBuilderName
        ).forEach { name ->
            productSpecificationRepository.save(Product(null, name, manufacturer))
        }

        assertEquals(findName, productSpecificationRepository.findOne(querySpec(findName, "findOne query", callbacks))!!.name)
        assertEquals(findName, productSpecificationRepository.findOne(predicateSpec(findName, "findOne predicate", callbacks))!!.name)
        assertEquals(listOf(findName), productSpecificationRepository.findAll(querySpec(findName, "findAll query", callbacks)).toList().map { it.name })
        assertEquals(listOf(findName), productSpecificationRepository.findAll(predicateSpec(findName, "findAll predicate", callbacks)).toList().map { it.name })
        assertEquals(listOf(findName), productSpecificationRepository.findAll(querySpec(findName, "findAll sort query", callbacks), Sort.of(Sort.Order.asc("name"))).toList().map { it.name })
        assertEquals(listOf(findName), productSpecificationRepository.findAll(predicateSpec(findName, "findAll sort predicate", callbacks), Sort.of(Sort.Order.asc("name"))).toList().map { it.name })
        assertEquals(listOf(findName), productSpecificationRepository.findAll(querySpec(findName, "findAll page query", callbacks), Pageable.from(0, 1)).content.map { it.name })
        assertEquals(listOf(findName), productSpecificationRepository.findAll(predicateSpec(findName, "findAll page predicate", callbacks), Pageable.from(0, 1)).content.map { it.name })
        assertEquals(1, productSpecificationRepository.count(querySpec(findName, "count query", callbacks)))
        assertEquals(1, productSpecificationRepository.count(predicateSpec(findName, "count predicate", callbacks)))
        assertTrue(productSpecificationRepository.exists(querySpec(findName, "exists query", callbacks)))
        assertTrue(productSpecificationRepository.exists(predicateSpec(findName, "exists predicate", callbacks)))
        assertEquals(findName, productSpecificationRepository.findOne(criteriaQueryBuilder(findName, "findOne builder", callbacks))!!.name)
        assertEquals(listOf(findName), productSpecificationRepository.findAll(criteriaQueryBuilder(findName, "findAll builder", callbacks)).toList().map { it.name })

        assertEquals(1, productSpecificationRepository.deleteAll(deleteSpec(deleteName, "deleteAll delete", callbacks)))
        assertEquals(1, productSpecificationRepository.deleteAll(predicateSpec(deletePredicateName, "deleteAll predicate", callbacks)))
        assertEquals(1, productSpecificationRepository.updateAll(updateSpec(updateName, updatedName, "updateAll update", callbacks)))
        assertEquals(1, productSpecificationRepository.deleteAll(criteriaDeleteBuilder(findName, "deleteAll builder", callbacks)))
        assertEquals(1, productSpecificationRepository.updateAll(criteriaUpdateBuilder(updateBuilderName, updatedBuilderName, "updateAll builder", callbacks)))

        assertTrue(productSpecificationRepository.exists(querySpec(updatedName, "exists updated", callbacks)))
        assertTrue(productSpecificationRepository.exists(querySpec(updatedBuilderName, "exists updated builder", callbacks)))
        assertTrue(
            callbacks.toSet().containsAll(
                setOf(
                    "findOne query",
                    "findOne predicate",
                    "findAll query",
                    "findAll predicate",
                    "findAll sort query",
                    "findAll sort predicate",
                    "findAll page query",
                    "findAll page predicate",
                    "count query",
                    "count predicate",
                    "exists query",
                    "exists predicate",
                    "findOne builder",
                    "findAll builder",
                    "deleteAll delete",
                    "deleteAll predicate",
                    "updateAll update",
                    "deleteAll builder",
                    "updateAll builder"
                )
            )
        )
    }

    private fun predicateSpec(name: String, callback: String, callbacks: MutableList<String>) =
        PredicateSpecification<Product> { root, criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            criteriaBuilder.equal(root.get<String>("name"), name)
        }

    private fun querySpec(name: String, callback: String, callbacks: MutableList<String>) =
        QuerySpecification<Product> { root, _, criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            criteriaBuilder.equal(root.get<String>("name"), name)
        }

    private fun deleteSpec(name: String, callback: String, callbacks: MutableList<String>) =
        DeleteSpecification<Product> { root, _, criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            criteriaBuilder.equal(root.get<String>("name"), name)
        }

    private fun updateSpec(name: String, newName: String, callback: String, callbacks: MutableList<String>) =
        UpdateSpecification<Product> { root, query, criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            query.set("name", newName)
            criteriaBuilder.equal(root.get<String>("name"), name)
        }

    private fun criteriaQueryBuilder(name: String, callback: String, callbacks: MutableList<String>) =
        CriteriaQueryBuilder<Product> { criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            val query = criteriaBuilder.createQuery(Product::class.java)
            val root = query.from(Product::class.java)
            query.select(root)
            query.where(criteriaBuilder.equal(root.get<String>("name"), name))
            query
        }

    private fun criteriaDeleteBuilder(name: String, callback: String, callbacks: MutableList<String>) =
        CriteriaDeleteBuilder<Product> { criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            val query = criteriaBuilder.createCriteriaDelete(Product::class.java)
            val root = query.from(Product::class.java)
            query.where(criteriaBuilder.equal(root.get<String>("name"), name))
            query
        }

    private fun criteriaUpdateBuilder(name: String, newName: String, callback: String, callbacks: MutableList<String>) =
        CriteriaUpdateBuilder<Product> { criteriaBuilder ->
            assertHibernateCriteriaBuilder(criteriaBuilder, callback, callbacks)
            val query = criteriaBuilder.createCriteriaUpdate(Product::class.java)
            val root = query.from(Product::class.java)
            query.set("name", newName)
            query.where(criteriaBuilder.equal(root.get<String>("name"), name))
            query
        }

    private fun assertHibernateCriteriaBuilder(criteriaBuilder: CriteriaBuilder, callback: String, callbacks: MutableList<String>) {
        assertTrue(criteriaBuilder is HibernateCriteriaBuilder, "$callback received ${criteriaBuilder.javaClass.name}")
        assertFalse(criteriaBuilder is RuntimeCriteriaBuilder, "$callback received Micronaut RuntimeCriteriaBuilder")
        callbacks.add(callback)
    }

}
