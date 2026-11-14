package example

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.notification.ChangeEvent
import jakarta.inject.Singleton
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Records the events delivered to the example after its handler has completed. */
@Singleton
@InterceptorBean(ChangeListener::class)
class ChangeEventRecorder : MethodInterceptor<Any, Any> {
    private val events = LinkedBlockingQueue<ChangeEvent<Book>>()

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val result = context.proceed()
        @Suppress("UNCHECKED_CAST")
        val event = context.parameterValues[0] as ChangeEvent<Book>
        events.add(event)
        return result
    }

    fun poll(timeout: Long, unit: TimeUnit): ChangeEvent<Book>? = events.poll(timeout, unit)
}
