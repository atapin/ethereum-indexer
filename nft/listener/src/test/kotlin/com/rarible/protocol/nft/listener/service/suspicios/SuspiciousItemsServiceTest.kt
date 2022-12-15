package com.rarible.protocol.nft.listener.service.suspicios

import com.rarible.protocol.nft.core.data.randomItemExState
import com.rarible.protocol.nft.core.data.randomUpdateSuspiciousItemsStateAsset
import com.rarible.protocol.nft.core.model.ItemId
import com.rarible.protocol.nft.core.repository.data.createItem
import com.rarible.protocol.nft.core.repository.item.ItemExStateRepository
import com.rarible.protocol.nft.core.repository.item.ItemRepository
import com.rarible.protocol.nft.core.service.item.reduce.ItemUpdateService
import com.rarible.protocol.nft.listener.service.opensea.OpenSeaService
import com.rarible.protocol.nft.listener.test.data.randomOpenSeaAsset
import com.rarible.protocol.nft.listener.test.data.randomOpenSeaAssets
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import reactor.core.publisher.Mono
import java.util.stream.Stream

@Suppress("ReactiveStreamsUnusedPublisher")
internal class SuspiciousItemsServiceTest {
    private val itemRepository = mockk<ItemRepository>()
    private val itemStateRepository = mockk<ItemExStateRepository>()
    private val openSeaService = mockk<OpenSeaService>()
    private val itemUpdateService = mockk<ItemUpdateService>()
    private val service = SuspiciousItemsService(itemRepository, itemStateRepository, openSeaService, itemUpdateService)

    @BeforeEach
    fun cleanMocks() {
        clearMocks(itemRepository, itemStateRepository, openSeaService, itemUpdateService)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `update - with existed state`(exStateExist: Boolean) = runBlocking<Unit> {
        val asset = randomOpenSeaAsset()
        val itemId = ItemId(asset.assetContract.address, asset.tokenId)
        val item = createItem(itemId).copy(isSuspiciousOnOS = asset.supportsWyvern.not())
        val savedExState = if (exStateExist) randomItemExState(itemId) else null

        val openSeaItems = randomOpenSeaAssets(listOf(asset) )

        val stateAsset = randomUpdateSuspiciousItemsStateAsset()
        every { itemRepository.findById(itemId) } returns Mono.just(item)
        coEvery { openSeaService.getOpenSeaAssets(stateAsset.contract, stateAsset.cursor) } returns openSeaItems
        coEvery { itemUpdateService.update(any()) } returns item
        coEvery { itemStateRepository.getById(itemId) } returns savedExState
        coEvery { itemStateRepository.save(any()) } returns randomItemExState(itemId)

        service.update(stateAsset)

        coVerify(exactly = 1) {
            itemStateRepository.save(withArg {
                assertThat(it.id).isEqualTo(itemId)
                assertThat(it.isSuspiciousOnOS).isEqualTo(asset.supportsWyvern)
            })
            itemUpdateService.update(withArg {
                assertThat(it.id).isEqualTo(itemId)
                assertThat(it.isSuspiciousOnOS).isEqualTo(asset.supportsWyvern)
            })
        }
    }

    @Test
    fun `no update`() = runBlocking<Unit> {
        val asset = randomOpenSeaAsset()
        val itemId = ItemId(asset.assetContract.address, asset.tokenId)
        val item = createItem(itemId).copy(isSuspiciousOnOS = asset.supportsWyvern)

        val openSeaItems = randomOpenSeaAssets(listOf(asset) )

        val stateAsset = randomUpdateSuspiciousItemsStateAsset()
        every { itemRepository.findById(itemId) } returns Mono.just(item)
        coEvery { openSeaService.getOpenSeaAssets(stateAsset.contract, stateAsset.cursor) } returns openSeaItems

        service.update(stateAsset)

        coVerify(exactly = 0) {
            itemStateRepository.save(any())
            itemUpdateService.update(any())
        }
    }
}