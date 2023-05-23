package example

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.persistence.Entity
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*

@MicronautTest(transactional = false)
class BeanIntrospectionSpec() {

    @Test
    @Disabled
    fun testFindEntities() {
        val packageName = this.javaClass.packageName
        val allEntities = BeanIntrospector.SHARED.findIntrospections(Entity::class.java)
        val allIntrospected = BeanIntrospector.SHARED.findIntrospections(Introspected::class.java)
        val introspectedInPackage = BeanIntrospector.SHARED.findIntrospections(Introspected::class.java, packageName)
        val entitiesInPackage = BeanIntrospector.SHARED.findIntrospections(Entity::class.java, packageName)
        assert(allIntrospected.isNotEmpty())
        assert(introspectedInPackage.isNotEmpty())
        assert(entitiesInPackage.isNotEmpty())
        assert(allEntities.isNotEmpty())
    }
}
