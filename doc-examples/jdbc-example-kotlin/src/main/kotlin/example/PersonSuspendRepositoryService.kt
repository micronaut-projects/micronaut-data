package example

import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.lang.Thread.currentThread
import java.util.*
import javax.transaction.Transactional

@Singleton
open class PersonSuspendRepositoryService(private val parentSuspendRepository: ParentSuspendRepository) {

    @Transactional
    open suspend fun save(p: Parent) {
        parentSuspendRepository.save(p)
    }

    @Transactional
    open suspend fun customFind(id: Int): Optional<Parent> {
        val threadName = currentThread().name
        delay(1000L)
        return withContext(IO) {
             if (threadName == currentThread().name) {
                 throw IllegalStateException("Test requires a different thread!")
             }
             parentSuspendRepository.queryById(id)
        }
    }

}
