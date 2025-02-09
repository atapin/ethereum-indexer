package com.rarible.protocol.order.listener.service.descriptors.auction.v1

import com.rarible.ethereum.domain.EthUInt256
import com.rarible.protocol.contracts.auction.v1.event.AuctionCreatedEvent
import com.rarible.protocol.order.core.configuration.EnableAuction
import com.rarible.protocol.order.core.model.*
import com.rarible.protocol.order.core.service.ContractsProvider
import org.springframework.stereotype.Service
import scalether.domain.response.Log
import scalether.domain.response.Transaction
import java.time.Instant

@Service
@EnableAuction
class AuctionCreatedDescriptor(
    contractsProvider: ContractsProvider,
) : AbstractAuctionDescriptor<OnChainAuction>(
    name = "auction_created",
    topic = AuctionCreatedEvent.id(),
    contractsProvider = contractsProvider
) {

    override suspend fun convert(log: Log, transaction: Transaction, timestamp: Instant, index: Int, totalLogs: Int): List<OnChainAuction> {
        val event = AuctionCreatedEvent.apply(log)
        val contract = log.address()
        val auctionId = EthUInt256.of(event.auctionId())
        val auction = parseContractAuction(event.auction(), timestamp)
        return listOf(
            OnChainAuction(
                auctionType = AuctionType.RARIBLE_V1,
                seller = auction.seller,
                buyer = auction.buyer,
                sell = auction.sell,
                buy = auction.buy,
                lastBid = auction.lastBid,
                endTime = auction.endTime,
                minimalStep = auction.minimalStep,
                minimalPrice = auction.minimalPrice,
                data = auction.data,
                protocolFee = auction.protocolFee,
                createdAt = timestamp,
                date = timestamp,
                contract = contract,
                auctionId = auctionId,
                hash = Auction.raribleV1HashKey(contract, auctionId),
                source = HistorySource.RARIBLE
            )
        )
    }
}

