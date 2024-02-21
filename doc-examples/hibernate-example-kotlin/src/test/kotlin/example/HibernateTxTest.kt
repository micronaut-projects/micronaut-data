package example

import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.joinMany
import io.micronaut.data.runtime.criteria.query
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.ListJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.hibernate.query.sqm.tree.domain.SqmListJoin
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.*

@MicronautTest(transactional = false, rollback = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class HibernateTxTest {

    @Inject
    private lateinit var repositorySuspended: ParentSuspendRepository
    @Inject
    private lateinit var repository: ParentRepository
    @Inject
    private lateinit var repositoryForCustomDb: ParentRepositoryForCustomDb
    @Inject
    private lateinit var service: PersonSuspendRepositoryService

    @BeforeEach
    fun cleanup() {
        repository.deleteAll()
        repositoryForCustomDb.deleteAll()
    }

    @Test
    @Order(1)
    internal fun `test transaction propagation`() = runBlocking {
        val parent = Parent("xyz", Collections.emptyList())
        val saved = repositorySuspended.save(parent)

        val found = service.customFind(saved.id!!).get()
        assertTrue(found.name == "xyz")
    }

    @Test
    @Order(2)
    fun normal() {
        assertThrows<RuntimeException> {
            service.normalStore()
        }

        Assertions.assertEquals(0, service.count())
    }

    @Test
    @Order(3)
    fun coroutines() = runBlocking {
        assertThrows<RuntimeException> {
            service.coroutinesStore()
        }

        Assertions.assertEquals(0, service.count())
    }

    @Test
    @Order(4)
    fun coroutinesGeneric() = runBlocking {
        assertThrows<RuntimeException> {
            service.coroutinesGenericStore()
        }

        Assertions.assertEquals(0, service.count())
    }

    @Test
    @Order(5)
    fun normal2() {
        assertThrows<RuntimeException> {
            service.normalStore()
        }

        Assertions.assertEquals(0, service.count())
    }

    @Test
    @Order(6)
    @Timeout(10)
    fun timeoutTest() = runBlocking {
        service.storeCoroutines()
        return@runBlocking
    }

    @Test
    @Order(7)
    fun normalWithCustomDSNotTransactional() {
        assertThrows<RuntimeException> {
            service.normalWithCustomDSNotTransactional()
        }

        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(1, service.countForCustomDb()) // Custom DB save is not transactional
    }

    @Test
    @Order(8)
    fun normalWithCustomDSTransactional() {
        assertThrows<RuntimeException> {
            service.normalWithCustomDSTransactional()
        }

        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(0, service.countForCustomDb())
    }

    @Test
    @Order(9)
    fun coroutinesStoreWithCustomDSNotTransactional() = runBlocking {
        assertThrows<RuntimeException> {
            service.coroutinesStoreWithCustomDBNotTransactional()
        }
        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(1, service.countForCustomDb()) // Custom DB save is not transactional
    }

    @Test
    @Order(10)
    fun coroutinesStoreWithCustomDSTransactional() {
        runBlocking {
            assertThrows<RuntimeException> {
                service.coroutinesStoreWithCustomDBTransactional()
            }
            Assertions.assertEquals(0, service.count())
            Assertions.assertEquals(0, service.countForCustomDb())
        }
        runBlocking {
            assertThrows<RuntimeException> {
                service.coroutinesStoreWithCustomDBTransactional()
            }
        }
        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(0, service.countForCustomDb())
    }

    @Test
    @Order(11)
    fun coroutinesGenericStoreWithCustomDSTransactional() {
        runBlocking {
            assertThrows<RuntimeException> {
                service.coroutinesGenericStoreWithCustomDb()
            }
            Assertions.assertEquals(0, service.count())
            Assertions.assertEquals(0, service.countForCustomDb())
        }
        runBlocking {
            assertThrows<RuntimeException> {
                service.coroutinesGenericStoreWithCustomDb()
            }
        }
        Assertions.assertEquals(0, service.count())
        Assertions.assertEquals(0, service.countForCustomDb())
    }

    @Test
    @Order(12)
    fun coroutineCriteria() {
        runBlocking {
            val parent = Parent("abc", Collections.emptyList())
            val saved = repositorySuspended.save(parent)

            val found1 = repositorySuspended.findOne(QuerySpecification { root, query, criteriaBuilder -> criteriaBuilder.equal(root.get<String>("name"), "abc")})
            Assertions.assertEquals(found1!!.id, saved.id)

            val found2 = repositorySuspended.findOne(PredicateSpecification { root, criteriaBuilder ->  criteriaBuilder.equal(root.get<String>("name"), "abc")})
            Assertions.assertEquals(found2!!.id, saved.id)
        }
    }

    @Test
    fun coroutineCriteria2() {
        runBlocking {
            val parent1 = Parent("abc", Collections.emptyList())
            val parent2 = Parent("abc", Collections.emptyList())
            val saved1 = repositorySuspended.save(parent1)
            val saved2 = repositorySuspended.save(parent2)

            val flowResult = repositorySuspended.findAll { root, query, criteriaBuilder -> criteriaBuilder.equal(root.get<String>("name"), "abc") }
            Assertions.assertEquals(listOf(saved1.name, saved2.name), flowResult.toList().map { it.name })

            val unpaginatedPage = repositorySuspended.findAll(
                { root, query, criteriaBuilder -> criteriaBuilder.equal(root.get<String>("name"), "abc") },
                Pageable.from(0, 1)
            )
            Assertions.assertEquals(1, unpaginatedPage.content.size)
            Assertions.assertEquals(listOf(saved1.name), unpaginatedPage.content.map { it.name })
            Assertions.assertEquals(2, unpaginatedPage.totalSize)
        }
    }

    @Test
    fun coroutineCriteria3() {
        runBlocking {
            val parent1 = Parent("BA", Collections.emptyList())
            val parent2 = Parent("AB", Collections.emptyList())
            val parent3 = Parent("AC", Collections.emptyList())
            val parent4 = Parent("AA", Collections.emptyList())
            repositorySuspended.save(parent1)
            repositorySuspended.save(parent2)
            repositorySuspended.save(parent3)
            repositorySuspended.save(parent4)

            val sortAsc = repositorySuspended.findAll(
                { root, query, criteriaBuilder -> criteriaBuilder.like(root.get("name"), "A%") },
                Pageable.from(0, 1, Sort.of(Sort.Order.asc("name")))
            )
            Assertions.assertEquals(1, sortAsc.content.size)
            Assertions.assertEquals(listOf(parent4.name), sortAsc.content.map { it.name })
            Assertions.assertEquals(3, sortAsc.totalSize)

            val sortDesc = repositorySuspended.findAll(
                { root, query, criteriaBuilder -> criteriaBuilder.like(root.get("name"), "A%") },
                Pageable.from(0, 1, Sort.of(Sort.Order.desc("name")))
            )
            Assertions.assertEquals(1, sortDesc.content.size)
            Assertions.assertEquals(listOf(parent3.name), sortDesc.content.map { it.name })
            Assertions.assertEquals(3, sortDesc.totalSize)
        }
    }

    @Test
    fun coroutineCriteria4() {
        runBlocking {
            val parent1 = Parent("abc", mutableListOf()).apply { children.add(Child("cde", parent = this)) }
            val parent2 = Parent("cde", mutableListOf()).apply { children.add(Child("abc", parent = this)) }
            val saved1 = repositorySuspended.save(parent1)
            val saved2 = repositorySuspended.save(parent2)

            val query = query {
                val children: ListJoin<Parent, Child> = if (query.resultType.kotlin != Long::class) {
                    root.fetch<Parent, Child>("children", JoinType.LEFT) as SqmListJoin<Parent, Child>
                }  else {
                    root.joinMany(Parent::children)
                }

                where {
                    or {
                        root[Parent::name] inList listOf("abc", "cde")
                        children[Child::name] inList listOf("abc", "cde")
                    }
                }
            }

            val flowResult = repositorySuspended.findAll(query)
            Assertions.assertEquals(listOf(saved1.name, saved2.name), flowResult.toList().map { it.name })

            val page0 = repositorySuspended.findAll(query, Pageable.from(0, 1))
            Assertions.assertEquals(1, page0.content.size)
            Assertions.assertEquals(listOf(saved1.name), page0.content.map { it.name })
            Assertions.assertEquals(listOf(saved1.children.first().name), page0.content.map { it.children.first().name })
            Assertions.assertEquals(2, page0.totalSize)

            val page1 = repositorySuspended.findAll(query, Pageable.from(1, 1))
            Assertions.assertEquals(1, page1.content.size)
            Assertions.assertEquals(listOf(saved2.name), page1.content.map { it.name })
            Assertions.assertEquals(listOf(saved2.children.first().name), page1.content.map { it.children.first().name })
            Assertions.assertEquals(2, page1.totalSize)

            val unpaged = repositorySuspended.findAll(query, Pageable.UNPAGED)
            Assertions.assertEquals(2, unpaged.content.size)
            Assertions.assertEquals(listOf(saved1.name, saved2.name), unpaged.content.map { it.name })
            Assertions.assertEquals(listOf(saved1.children.first().name, saved2.children.first().name), unpaged.content.map { it.children.first().name })
            Assertions.assertEquals(2, unpaged.totalSize)
        }
    }

}
