package com.rarible.protocol.order.listener.integration

import com.rarible.core.common.nowMillis
import com.rarible.ethereum.cache.CacheableMonoEthereum
import com.rarible.ethereum.sign.service.ERC1271SignService
import com.rarible.protocol.currency.api.client.CurrencyControllerApi
import com.rarible.protocol.currency.dto.CurrencyRateDto
import com.rarible.protocol.erc20.api.client.BalanceControllerApi
import com.rarible.protocol.order.core.service.asset.AssetBalanceProvider
import com.rarible.protocol.order.listener.data.createErc20BalanceDto
import com.rarible.x2y2.client.X2Y2ApiClient
import io.daonomic.rpc.mono.WebClientTransport
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import scalether.core.MonoEthereum
import scalether.domain.Address
import scalether.transaction.MonoTransactionPoller
import scalether.transaction.ReadOnlyMonoTransactionSender
import java.time.Duration

@TestConfiguration
class TestPropertiesConfiguration {
    @Bean
    fun testEthereum(@Value("\${parityUrls}") url: String): MonoEthereum {
        val transport = WebClientTransport(url, MonoEthereum.mapper(), 10000, 10000)
        return CacheableMonoEthereum(
            transport = transport,
            expireAfter = Duration.ofMinutes(1),
            cacheMaxSize = 100
        )
    }

    @Bean
    fun testReadOnlyMonoTransactionSender(ethereum: MonoEthereum): ReadOnlyMonoTransactionSender {
        return ReadOnlyMonoTransactionSender(ethereum, Address.ZERO())
    }

    @Bean
    fun poller(ethereum: MonoEthereum): MonoTransactionPoller {
        return MonoTransactionPoller(ethereum)
    }

    @Bean
    @Primary
    fun mockedErc20BalanceApiClient(): BalanceControllerApi {
        return mockk {
            every { getErc20Balance(any(), any()) } returns Mono.just(createErc20BalanceDto())
        }
    }

    @Bean
    @Primary
    fun mockedX2Y2ApiClient(): X2Y2ApiClient {
        return mockk()
    }

    @Bean
    @Primary
    fun mockedCurrencyApi(): CurrencyControllerApi {
        return mockk {
            every { getCurrencyRate(any(), any(), any()) } returns CurrencyRateDto(
                "test",
                "usd",
                ETH_CURRENCY_RATE,
                nowMillis()
            ).toMono()
        }
    }

    @Bean
    @Primary
    fun mockAssetBalanceProvider(): AssetBalanceProvider = mockk {
    }

    @Bean
    @Primary
    fun mockERC1271SignService(): ERC1271SignService = mockk {
    }

    companion object {
        val ETH_CURRENCY_RATE = 3000.toBigDecimal() // 3000$
    }
}
