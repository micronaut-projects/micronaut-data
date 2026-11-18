from typing import Annotated

from jakarta.inject import Inject
from java.math import BigDecimal
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Account import Account
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
        assert self.bankGraph.findById(1).get().name == "Alice"
        assert [t.source for t in self.bankGraph.largeTransfers(BigDecimal(100))] == ["Alice", "Bob"]

    @Test
    def testSave(self):
        # tag::save[]
        self.bankGraph.save(Account(5, "Erin"))  # <1>
        erin = self.bankGraph.findByNameStartsWithOrderByName("Er")  # <2>
        # end::save[]

        assert [a.name for a in erin] == ["Erin"]
        self.bankGraph.deleteById(5)
        assert not self.bankGraph.findById(5).isPresent()
