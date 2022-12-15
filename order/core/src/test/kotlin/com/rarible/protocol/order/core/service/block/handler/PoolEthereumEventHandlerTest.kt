package com.rarible.protocol.order.core.service.block.handler

import com.rarible.core.test.data.randomWord
import com.rarible.protocol.order.core.configuration.OrderIndexerProperties
import com.rarible.protocol.order.core.data.createLogEvent
import com.rarible.protocol.order.core.data.createOrderCancel
import com.rarible.protocol.order.core.data.createOrderSideMatch
import com.rarible.protocol.order.core.data.randomApproveHistory
import com.rarible.protocol.order.core.data.randomAuctionCreated
import com.rarible.protocol.order.core.data.randomPoolDeltaUpdate
import com.rarible.protocol.order.core.data.randomPoolFeeUpdate
import com.rarible.protocol.order.core.service.OrderUpdateService
import com.rarible.protocol.order.core.service.pool.listener.PoolOrderEventListener
import io.daonomic.rpc.domain.Word
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

internal class PoolEthereumEventHandlerTest {
    private val poolOrderEventListener = mockk<PoolOrderEventListener>()
    private val orderUpdateService = mockk<OrderUpdateService>()
    private val properties = mockk<OrderIndexerProperties.PoolEventHandleProperties>()
    private val handler = PoolEthereumEventHandler(poolOrderEventListener, orderUpdateService, properties)

    @Test
    fun `handle - all pool events`() = runBlocking<Unit> {
        val hash1 = Word.apply(randomWord())
        val hash2 = Word.apply(randomWord())

        val event1 = createLogEvent(randomPoolDeltaUpdate().copy(hash = hash1))
        val event2 = createLogEvent(randomPoolFeeUpdate().copy(hash = hash1))
        val event3 = createLogEvent(randomPoolDeltaUpdate().copy(hash = hash2))
        val event4 = createLogEvent(randomPoolFeeUpdate().copy(hash = hash2))

        val others = listOf(
            createLogEvent(createOrderSideMatch()),
            createLogEvent(createOrderCancel()),
            createLogEvent(randomAuctionCreated()),
            createLogEvent(randomApproveHistory())
        )
        coEvery {
            poolOrderEventListener.onPoolEvent(event1)
            poolOrderEventListener.onPoolEvent(event2)
            poolOrderEventListener.onPoolEvent(event3)
            poolOrderEventListener.onPoolEvent(event4)
            orderUpdateService.update(hash1)
            orderUpdateService.update(hash2)
        } returns Unit

        every { properties.parallel } returns false

        handler.handle(listOf(event1, event2, event3, event4) + others)

        coVerify(exactly = 1) {
            poolOrderEventListener.onPoolEvent(event1)
            poolOrderEventListener.onPoolEvent(event2)
            poolOrderEventListener.onPoolEvent(event3)
            poolOrderEventListener.onPoolEvent(event4)
        }
        coVerify(exactly = 1) {
            orderUpdateService.update(hash1)
            orderUpdateService.update(hash2)
        }
        coVerifyOrder {
            orderUpdateService.update(hash1)
            poolOrderEventListener.onPoolEvent(event1)
            poolOrderEventListener.onPoolEvent(event2)
        }
        coVerifyOrder {
            orderUpdateService.update(hash2)
            poolOrderEventListener.onPoolEvent(event3)
            poolOrderEventListener.onPoolEvent(event4)
        }
    }
}