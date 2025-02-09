package com.rarible.protocol.order.core.service.block.approval

import com.rarible.blockchain.scanner.ethereum.model.ReversedEthereumLogRecord
import com.rarible.blockchain.scanner.ethereum.reduce.EntityEventsSubscriber
import com.rarible.blockchain.scanner.framework.data.LogRecordEvent
import com.rarible.protocol.order.core.configuration.OrderIndexerProperties
import com.rarible.protocol.order.core.misc.asEthereumLogRecord
import com.rarible.protocol.order.core.model.ApprovalHistory
import com.rarible.protocol.order.core.model.order.logger
import com.rarible.protocol.order.core.repository.order.OrderRepository
import com.rarible.protocol.order.core.service.OrderUpdateService
import com.rarible.protocol.order.core.service.approve.ApproveService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier("approval-event-subscriber")
class ApprovalEventSubscriber(
    private val orderRepository: OrderRepository,
    private val orderUpdateService: OrderUpdateService,
    private val approveService: ApproveService,
    private val properties: OrderIndexerProperties
) : EntityEventsSubscriber {

    override suspend fun onEntityEvents(events: List<LogRecordEvent>) {
        for (event in events) {
            reduceOrders(event.record.asEthereumLogRecord())
        }
    }

    private suspend fun reduceOrders(logEvent: ReversedEthereumLogRecord) {
        val history = logEvent.data as ApprovalHistory
        val blockNumber = logEvent.blockNumber ?: error("blockTimestamp can't be null")
        if (properties.handleApprovalAfterBlock > blockNumber) {
            logger.info(
                "Skip approval reindex event: block={}, tc={}, logIndex={}",
                logEvent.blockNumber, logEvent.transactionHash, logEvent.logIndex
            )
            return
        }
        val platform = approveService.getPlatform(history.operator) ?: run {
            logger.error("Can't get platform by operator ${history.operator}, event: $history")
            return
        }
        logger.info(
            "Process approval: maker={}, collection={}, platform={}, block={}, logIndex={}",
            history.owner, history.collection, platform, logEvent.blockNumber, logEvent.logIndex
        )
        orderRepository
            .findActiveSaleOrdersHashesByMakerAndToken(maker = history.owner, token = history.collection, platform)
            .collect {
                orderUpdateService.updateApproval(it, history.approved)
            }
    }
}

