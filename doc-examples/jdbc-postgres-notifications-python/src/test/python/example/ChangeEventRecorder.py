from jakarta.inject import Singleton
from micronaut.aop import InterceptorBean, MethodInterceptor, MethodInvocationContext
from micronaut.data.annotation import ChangeListener
from micronaut.data.notification import ChangeEvent
from java.util.concurrent import LinkedBlockingQueue, TimeUnit

from example.Book import Book


@Singleton
@InterceptorBean(value=[ChangeListener])
class ChangeEventRecorder(MethodInterceptor[object, object]):
    """Record events after the actual example handler has completed."""

    def __init__(self):
        self.events = LinkedBlockingQueue()

    def intercept(self, context: MethodInvocationContext[object, object]) -> object | None:
        result = context.proceed()
        self.events.add(context.getParameterValues()[0])
        return result

    def poll(self, timeout: int, unit: TimeUnit) -> ChangeEvent[Book] | None:
        return self.events.poll(timeout, unit)
