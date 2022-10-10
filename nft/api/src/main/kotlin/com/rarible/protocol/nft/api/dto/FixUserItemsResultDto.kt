package com.rarible.protocol.nft.api.dto

import com.rarible.protocol.nft.api.model.ItemProblemType

data class FixUserItemsResultDto(
    val valid: List<String>,
    val fixed: Map<String, ItemProblemType>,
    val unfixed: Map<String, ItemProblemType>,
)
