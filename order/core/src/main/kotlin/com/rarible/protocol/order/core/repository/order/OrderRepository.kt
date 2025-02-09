package com.rarible.protocol.order.core.repository.order

import com.rarible.ethereum.domain.EthUInt256
import com.rarible.protocol.order.core.misc.div
import com.rarible.protocol.order.core.model.Asset
import com.rarible.protocol.order.core.model.AssetType
import com.rarible.protocol.order.core.model.Erc20AssetType
import com.rarible.protocol.order.core.model.NftAssetType
import com.rarible.protocol.order.core.model.Order
import com.rarible.protocol.order.core.model.OrderStatus
import com.rarible.protocol.order.core.model.OrderType
import com.rarible.protocol.order.core.model.Platform
import io.daonomic.rpc.domain.Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import scalether.domain.Address
import java.time.Instant
import java.util.*

interface OrderRepository {
    /**
     * **This method should not be used in business code.** Order state is handled by `OrderUpdateService`.*
     *
     * To insert or update an order call `OrderUpdateService.save(OrderVersion)`.
     * Still there are possible use-cases for using this method: to update transient order's fields (`makeStock`) for example.
     * @see [com.rarible.protocol.order.core.service.OrderUpdateService]
     */

    suspend fun save(order: Order): Order

    suspend fun findById(hash: Word): Order?

    fun findAll(hashes: Collection<Word>): Flow<Order>

    fun findAll(platform: Platform, status: OrderStatus, fromHash: Word?): Flow<Order>

    suspend fun search(query: Query): List<Order>

    suspend fun remove(hash: Word): Boolean

    fun findActive(): Flow<Order>

    fun findAll(): Flow<Order>

    fun findByTargetNftAndNotCanceled(maker: Address, token: Address, tokenId: EthUInt256): Flow<Order>

    fun findByTargetBalanceAndNotCanceled(maker: Address, token: Address): Flow<Order>

    fun findAllBeforeLastUpdateAt(lastUpdatedAt: Date?, status: OrderStatus?, platform: Platform?): Flow<Order>

    fun findMakeTypesOfBidOrders(token: Address, tokenId: EthUInt256): Flow<AssetType>

    suspend fun findByMake(token: Address, tokenId: EthUInt256): Order?

    suspend fun findOpenSeaHashesByMakerAndByNonce(maker: Address, fromIncluding: Long, toExcluding: Long): Flow<Word>

    suspend fun findNotCanceledByMakerAndByCounter(maker: Address, counter: Long): Flow<Word>

    suspend fun findByTake(token: Address, tokenId: EthUInt256): Order?

    fun findTakeTypesOfSellOrders(token: Address, tokenId: EthUInt256): Flow<AssetType>

    suspend fun createIndexes()

    suspend fun dropIndexes()

    fun findAllLiveBidsHashesLastUpdatedBefore(before: Instant): Flow<Word>

    fun findActiveSaleOrdersHashesByMakerAndToken(maker: Address, token: Address, platform: Platform): Flow<Order>

    fun findByMakeAndByCounters(platform: Platform, maker: Address, counters: List<Long>): Flow<Order>

    fun findNotCanceledByMakerAndCounterLtThen(platform: Platform, maker: Address, counter: Long): Flow<Word>

    fun findExpiredOrders(now: Instant): Flow<Order>

    fun findNotStartedOrders(now: Instant): Flow<Order>

    suspend fun findActiveSellCurrenciesByCollection(token: Address): List<Address>
}
