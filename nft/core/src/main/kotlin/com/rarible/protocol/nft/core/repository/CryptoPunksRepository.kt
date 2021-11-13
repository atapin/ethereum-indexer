package com.rarible.protocol.nft.core.repository

import com.rarible.core.apm.CaptureSpan
import com.rarible.core.apm.SpanType
import com.rarible.protocol.nft.core.model.CryptoPunksMeta
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigInteger

@Component
@CaptureSpan(type = SpanType.DB, subtype = "item")
class CryptoPunksRepository(private val mongo: ReactiveMongoOperations) {

    fun findById(id: BigInteger): Mono<CryptoPunksMeta> {
        return mongo.findById(id)
    }

    fun save(punk: CryptoPunksMeta): Mono<CryptoPunksMeta> {
        return mongo.save(punk)
    }
}
