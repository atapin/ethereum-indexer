package com.rarible.protocol.order.core.service.pool.listener

import com.rarible.blockchain.scanner.ethereum.model.EthereumLogRecord
import com.rarible.blockchain.scanner.ethereum.model.EthereumLogStatus
import com.rarible.blockchain.scanner.ethereum.model.ReversedEthereumLogRecord
import com.rarible.ethereum.domain.EthUInt256
import com.rarible.ethereum.listener.log.domain.LogEventStatus
import com.rarible.protocol.dto.AmmOrderNftUpdateEventDto
import com.rarible.protocol.order.core.model.ItemId
import com.rarible.protocol.order.core.model.PoolCreate
import com.rarible.protocol.order.core.model.PoolDataUpdate
import com.rarible.protocol.order.core.model.PoolHistory
import com.rarible.protocol.order.core.model.PoolNftDeposit
import com.rarible.protocol.order.core.model.PoolNftIn
import com.rarible.protocol.order.core.model.PoolNftOut
import com.rarible.protocol.order.core.model.PoolNftWithdraw
import com.rarible.protocol.order.core.model.token
import com.rarible.protocol.order.core.producer.ProtocolOrderPublisher
import com.rarible.protocol.order.core.repository.order.OrderRepository
import org.springframework.stereotype.Component
import scalether.domain.Address

@Component
class PoolOrderEventListener(
    private val orderRepository: OrderRepository,
    private val orderPublisher: ProtocolOrderPublisher,
) : PoolEventListener {

    override suspend fun onPoolEvent(event: ReversedEthereumLogRecord) {
        val reverted = event.status == EthereumLogStatus.REVERTED
        val poolHistory = event.data as PoolHistory
        val hash = poolHistory.hash
        val collection = orderRepository.findById(hash)
            ?.let {
                when {
                    it.make.type.nft -> it.make.type.token
                    it.take.type.nft -> it.take.type.token
                    else -> return
                }
            }
            ?: run {
                if (poolHistory is PoolCreate) poolHistory.collection else return
            }

        val nftDelta = when (poolHistory) {
            is PoolCreate -> {
                NftDelta(inNft = poolHistory.tokenIds)
            }
            is PoolNftDeposit -> {
                if (poolHistory.collection == collection) NftDelta(inNft = poolHistory.tokenIds) else NftDelta()
            }
            is PoolNftIn -> {
                NftDelta(inNft = poolHistory.tokenIds)
            }
            is PoolNftWithdraw -> {
                if (poolHistory.collection == collection) NftDelta(outNft = poolHistory.tokenIds) else NftDelta()
            }
            is PoolNftOut -> {
                NftDelta(outNft = poolHistory.tokenIds)
            }
            is PoolDataUpdate -> return
        }
        if (nftDelta.isNotEmpty) {
            orderPublisher.publish(
                AmmOrderNftUpdateEventDto(
                    eventId = event.id,
                    orderId = hash.toString(),
                    inNft = nftDelta.getInNft(collection, reverted),
                    outNft = nftDelta.getOutNft(collection, reverted)
                )
            )
        }
    }

    private class NftDelta(
        private val inNft: List<EthUInt256> = emptyList(),
        private val outNft: List<EthUInt256> = emptyList(),
    ) {
        val isNotEmpty = inNft.isNotEmpty() || outNft.isNotEmpty()

        fun getInNft(collection: Address, reverted: Boolean): List<String> {
            return (if (reverted.not()) inNft else outNft).map { convert(collection, it) }
        }

        fun getOutNft(collection: Address, reverted: Boolean): List<String> {
            return (if (reverted.not()) outNft else inNft).map { convert(collection, it) }
        }

        private fun convert(collection: Address, tokenId: EthUInt256): String {
            return ItemId(collection, tokenId.value).toString()
        }
    }
}