package com.rarible.protocol.nft.core.service.item.meta.properties

import com.rarible.core.test.data.randomString
import com.rarible.protocol.nft.core.data.createRandomItemId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ItemPropertiesParserTest {

    private val itemId = createRandomItemId()
    private val url = randomString()

    @Test
    fun `parsed - valid properties`() {
        val result = ItemPropertiesParser.parse(itemId, url, "{\"name\":\"test\"}")
        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("test")
    }

    @Test
    fun `failed - empty properties`() {
        val result = ItemPropertiesParser.parse(itemId, url, "{\"message\":\"error\"}")
        assertThat(result).isNull()

    }

    @Test
    fun `failed - broken json`() {
        val result = ItemPropertiesParser.parse(itemId, url, "{something}")
        assertThat(result).isNull()
    }
}