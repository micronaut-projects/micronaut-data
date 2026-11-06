# tag::registrar[]
from jakarta.inject import Singleton
from java.util import Map
from micronaut.data.graph.runtime import GraphValueEncoderRegistrar, GraphValueEncoders

from example.Money import Money


@Singleton
class MoneyEncoderRegistrar(GraphValueEncoderRegistrar):

    def register(self, encoders: GraphValueEncoders) -> None:
        encoders.register(Money, lambda money, e: Map.of(  # <1>
            "amount", e.encode(money.amount),  # <2>
            "currency", money.currency
        ))
# end::registrar[]
