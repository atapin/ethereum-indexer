package com.rarible.protocol.order.listener.service.descriptors.exchange.sudoswap

import com.rarible.core.apm.CaptureSpan
import com.rarible.core.apm.SpanType
import com.rarible.core.telemetry.metrics.RegisteredCounter
import com.rarible.ethereum.domain.EthUInt256
import com.rarible.protocol.contracts.exchange.sudoswap.v1.factory.NFTDepositEvent
import com.rarible.protocol.order.core.model.HistorySource
import com.rarible.protocol.order.core.model.PoolNftDeposit
import com.rarible.protocol.order.core.service.ContractsProvider
import com.rarible.protocol.order.listener.service.descriptors.PoolSubscriber
import com.rarible.protocol.order.listener.service.sudoswap.SudoSwapEventConverter
import java.time.Instant
import org.springframework.stereotype.Service
import scalether.domain.response.Log
import scalether.domain.response.Transaction

@Service
@CaptureSpan(type = SpanType.EVENT)
class SudoSwapDepositNftPairDescriptor(
    contractsProvider: ContractsProvider,
    private val sudoSwapEventConverter: SudoSwapEventConverter,
    private val sudoSwapDepositNftEventCounter: RegisteredCounter
): PoolSubscriber<PoolNftDeposit>(
    name = "sudo_nft_deposit",
    topic = NFTDepositEvent.id(),
    contracts = contractsProvider.pairFactoryV1()
) {
    override suspend fun convert(log: Log, transaction: Transaction, timestamp: Instant, index: Int, totalLogs: Int): List<PoolNftDeposit> {
        val event = NFTDepositEvent.apply(log)
        val details = sudoSwapEventConverter.getNftDepositDetails(log.address(), transaction).let {
            assert(it.size == totalLogs)
            it[index]
        }
        return listOf(
            PoolNftDeposit(
                hash = sudoSwapEventConverter.getPoolHash(event.poolAddress()),
                collection = details.collection,
                tokenIds = details.tokenIds.map { EthUInt256.of(it) },
                date = timestamp,
                source = HistorySource.SUDOSWAP
            )
        ).apply { sudoSwapDepositNftEventCounter.increment() }
    }
}
