from typing import Annotated

from jakarta.inject import Inject
from java.math import BigDecimal
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Account import Account
from example.BankGraph import BankGraph


@MicronautTest(transactional=False)
class BankGraphSpec:

    bankGraph: Annotated[BankGraph, Inject]

    @AfterEach
    def afterEach(self):
        self.bankGraph.deleteById(5).block()

    @Test
    def testReactive(self):
        # tag::reactive[]
        big_senders = self.bankGraph.save(Account(5, "Erin")).thenMany(  # <1>
            self.bankGraph.findByTransfersAmountGreaterThan(BigDecimal(1000))  # <2>
        ).collectList().block()  # <3>
        # end::reactive[]

        assert [a.name for a in big_senders] == ["Bob"]
        assert self.bankGraph.findById(5).block().name == "Erin"
        assert self.bankGraph.existsPathByTransfers(1, 3).block()
