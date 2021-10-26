package com.rarible.protocol.order.core.model

import com.rarible.ethereum.domain.EthUInt256

sealed class Bid {
    abstract val amount: EthUInt256
    abstract val data: BidData
}

data class BidV1(
    override val amount: EthUInt256,
    override val data: BidDataV1
) : Bid()
