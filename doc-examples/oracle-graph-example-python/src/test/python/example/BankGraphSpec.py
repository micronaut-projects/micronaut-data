from typing import Annotated

from jakarta.inject import Inject
from java.math import BigDecimal
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.BankGraph import BankGraph


@MicronautTest(transactional=False)
class BankGraphSpec:

    bankGraph: Annotated[BankGraph, Inject]

    @Test
    def testQueries(self):
        # tag::find[]
        big_senders = self.bankGraph.findByTransfersAmountGreaterThan(BigDecimal(1000))  # <1>
        connected = self.bankGraph.existsPathByTransfers(1, 3)  # <2>
        # end::find[]

        assert [a.name for a in big_senders] == ["Bob"]
        assert connected
        assert not self.bankGraph.existsPathByTransfers(3, 1)
        assert self.bankGraph.findById(1).name == "Alice"
        assert [t.source for t in self.bankGraph.largeTransfers(BigDecimal(100))] == ["Alice", "Bob"]
