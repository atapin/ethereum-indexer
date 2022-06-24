package com.rarible.protocol.order.core.model

enum class SeaportOrderType(val value: Int) {
    FULL_OPEN(0),
    PARTIAL_OPEN(1), // Partial fills supported, anyone can execute
    FULL_RESTRICTED(2), // No partial fills, only offerer or zone can execute
    PARTIAL_RESTRICTED(3), // Partial fills supported, only offerer or zone can execute
}