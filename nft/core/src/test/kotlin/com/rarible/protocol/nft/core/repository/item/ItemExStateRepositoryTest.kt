package com.rarible.protocol.nft.core.repository.item

import com.rarible.protocol.nft.core.data.randomItemExState
import com.rarible.protocol.nft.core.integration.AbstractIntegrationTest
import com.rarible.protocol.nft.core.integration.IntegrationTest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
internal class ItemExStateRepositoryIt : AbstractIntegrationTest() {
    @Autowired
    private lateinit var stateRepository: ItemExStateRepository

    @Test
    fun `save and get state`() = runBlocking<Unit> {
        val state = randomItemExState()

        val notExistedState = stateRepository.getById(state.id)
        assertThat(notExistedState).isNull()

        stateRepository.save(state)
        val saved = stateRepository.getById(state.id)
        assertThat(saved).isNotNull
    }
}
