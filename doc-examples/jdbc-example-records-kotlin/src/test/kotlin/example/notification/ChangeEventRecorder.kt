package example.notification

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.notification.ChangeEvent
import jakarta.inject.Singleton
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Records the events delivered to the example after its handler has completed. */
@Singleton
@Requires(env = ["oracle-cqn"])
@InterceptorBean(ChangeListener::class)
class ChangeEventRecorder : MethodInterceptor<Any, Any> {
    private val events = LinkedBlockingQueue<ChangeEvent<Library>>()

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        val result = context.proceed()
        @Suppress("UNCHECKED_CAST")
        val event = context.parameterValues[0] as ChangeEvent<Library>
        events.add(event)
        return result
    }

    fun poll(timeout: Long, unit: TimeUnit): ChangeEvent<Library>? = events.poll(timeout, unit)
}
