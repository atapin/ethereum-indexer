package com.rarible.protocol.order.core.service.floor

import com.rarible.core.test.data.randomAddress
import com.rarible.protocol.order.core.data.createOrder
import com.rarible.protocol.order.core.repository.order.OrderRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FloorSellOrderProviderTest {
    private val orderRepository = mockk<OrderRepository>()
    private val floorSellOrderProvider = FloorSellOrderProvider(orderRepository)

    @Test
    fun `should get all floor orders by currencies`() = runBlocking<Unit> {
        val token = randomAddress()
        val currencies = listOf(randomAddress(), randomAddress())
        val floorOrder1 = createOrder()
        val floorOrder2 = createOrder()
        coEvery { orderRepository.findActiveSellCurrenciesByCollection(token) } returns currencies
        coEvery { orderRepository.search(any()) } returnsMany listOf(
            listOf(floorOrder1, createOrder()),
            listOf(floorOrder2, createOrder()),
            emptyList() //this is for eth case
        )
        val orders = floorSellOrderProvider.getCurrencyFloorSells(token)
        assertThat(orders).containsExactlyInAnyOrderElementsOf(listOf(floorOrder1, floorOrder2))
    }
}