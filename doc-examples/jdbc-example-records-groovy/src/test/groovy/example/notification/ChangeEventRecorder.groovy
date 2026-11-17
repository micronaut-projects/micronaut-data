package example.notification

import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.notification.ChangeEvent
import jakarta.inject.Singleton
import org.jspecify.annotations.Nullable

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Records the events delivered to the example after its handler has completed. */
@Singleton
@Requires(env = 'oracle-cqn')
@InterceptorBean(ChangeListener)
class ChangeEventRecorder implements MethodInterceptor<Object, Object> {
    private final BlockingQueue<ChangeEvent<Library>> events = new LinkedBlockingQueue<>()

    @Override
    @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        def result = context.proceed()
        events.add((ChangeEvent<Library>) context.parameterValues[0])
        result
    }

    @Nullable ChangeEvent<Library> poll(long timeout, TimeUnit unit) {
        events.poll(timeout, unit)
    }
}
