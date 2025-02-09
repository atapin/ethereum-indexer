package com.rarible.protocol.order.listener.service.descriptors.exchange.opensea

import com.rarible.core.apm.CaptureSpan
import com.rarible.core.apm.SpanType
import com.rarible.core.telemetry.metrics.RegisteredCounter
import com.rarible.ethereum.domain.EthUInt256
import com.rarible.protocol.contracts.exchange.seaport.v1.CounterIncrementedEvent
import com.rarible.protocol.order.core.model.ChangeNonceHistory
import com.rarible.protocol.order.core.model.HistorySource
import com.rarible.protocol.order.core.service.ContractsProvider
import com.rarible.protocol.order.listener.service.descriptors.NonceSubscriber
import org.springframework.stereotype.Service
import scalether.domain.response.Log
import scalether.domain.response.Transaction
import java.time.Instant

@Service
@CaptureSpan(type = SpanType.EVENT)
class SeaportExchangeChangeCounterDescriptor(
    contractsProvider: ContractsProvider,
    private val seaportCounterEventCounter: RegisteredCounter
) : NonceSubscriber(
    name = "os_counter_incremented",
    topic = CounterIncrementedEvent.id(),
    contracts = contractsProvider.seaportV1()
) {
    override suspend fun convert(log: Log, transaction: Transaction, timestamp: Instant, index: Int, totalLogs: Int): List<ChangeNonceHistory> {
        val event = CounterIncrementedEvent.apply(log)
        return listOf(
            ChangeNonceHistory(
                maker = event.offerer(),
                newNonce = EthUInt256.of(event.newCounter()),
                date = timestamp,
                source = HistorySource.OPEN_SEA
            )
        ).also { seaportCounterEventCounter.increment() }
    }
}