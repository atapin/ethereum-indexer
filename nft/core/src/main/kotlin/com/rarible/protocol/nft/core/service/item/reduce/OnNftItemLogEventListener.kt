package com.rarible.protocol.nft.core.service.item.reduce

import com.rarible.blockchain.scanner.ethereum.model.EthereumLogStatus
import com.rarible.blockchain.scanner.framework.data.LogRecordEvent
import com.rarible.protocol.nft.core.converters.dto.NftActivityConverter
import com.rarible.protocol.nft.core.misc.asEthereumLogRecord
import com.rarible.protocol.nft.core.model.ItemType
import com.rarible.protocol.nft.core.producer.ProtocolNftEventPublisher
import io.daonomic.rpc.domain.Word
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class OnNftItemLogEventListener(
    private val eventPublisher: ProtocolNftEventPublisher,
    private val nftActivityConverter: NftActivityConverter
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val topics: Set<Word> = ItemType.TRANSFER.topic.toSet()

    suspend fun onLogEvent(logEvent: LogRecordEvent) {
        try {
            val record = logEvent.record.asEthereumLogRecord()
            if (record.status == EthereumLogStatus.CONFIRMED &&
                record.log.topic in topics
            ) {
                val activity = nftActivityConverter.convert(record, logEvent.reverted)
                if (activity != null) eventPublisher.publish(activity)
            }
        } catch (ex: Exception) {
            logger.error("Error on log event $logEvent", ex)
            throw ex
        }
    }
}