package com.rarible.protocol.nft.listener.service.item

import com.rarible.core.common.nowMillis
import com.rarible.core.daemon.job.JobHandler
import com.rarible.protocol.nft.core.model.InconsistentItem
import com.rarible.protocol.nft.core.model.InconsistentItemStatus
import com.rarible.protocol.nft.core.model.Item
import com.rarible.protocol.nft.core.model.ItemContinuation
import com.rarible.protocol.nft.core.model.ItemFilter
import com.rarible.protocol.nft.core.model.ItemFilterAll
import com.rarible.protocol.nft.core.repository.InconsistentItemRepository
import com.rarible.protocol.nft.core.repository.JobStateRepository
import com.rarible.protocol.nft.core.repository.item.ItemFilterCriteria.toCriteria
import com.rarible.protocol.nft.core.repository.item.ItemRepository
import com.rarible.protocol.nft.core.service.item.ItemOwnershipConsistencyService
import com.rarible.protocol.nft.core.service.item.ItemOwnershipConsistencyService.CheckResult.Failure
import com.rarible.protocol.nft.core.service.item.ItemOwnershipConsistencyService.CheckResult.Success
import com.rarible.protocol.nft.listener.configuration.NftListenerProperties
import com.rarible.protocol.nft.listener.metrics.NftListenerMetricsFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

const val ITEM_OWNERSHIP_CONSISTENCY_JOB = "item-ownership-consistency"

@Component
class ItemOwnershipConsistencyJobHandler(
    private val jobStateRepository: JobStateRepository,
    private val itemRepository: ItemRepository,
    private val nftListenerProperties: NftListenerProperties,
    private val itemOwnershipConsistencyService: ItemOwnershipConsistencyService,
    private val inconsistentItemRepository: InconsistentItemRepository,
    metricsFactory: NftListenerMetricsFactory,
) : JobHandler {

    private val checkedCounter = metricsFactory.itemOwnershipConsistencyJobCheckedCounter()
    private val fixedCounter = metricsFactory.itemOwnershipConsistencyJobFixedCounter()
    private val unfixedCounter = metricsFactory.itemOwnershipConsistencyJobUnfixedCounter()
    private val delayMetric = metricsFactory.itemOwnershipConsistencyJobDelayGauge()

    private val properties = nftListenerProperties.itemOwnershipConsistency
    private val logger = LoggerFactory.getLogger(javaClass)
    private val filter = ItemFilterAll(
        sort = ItemFilter.Sort.LAST_UPDATE_ASC,
        showDeleted = false,
    )

    override suspend fun handle() = coroutineScope {
        logger.info("ItemOwnershipConsistencyHandler handle() called, properties $properties")
        val itemsChannel = Channel<Item>(properties.parallelism)
        val semaphore = Semaphore(properties.parallelism)
        repeat(properties.parallelism) { launchItemWorker(itemsChannel, semaphore) }
        val state = getState()
        var running = true
        lateinit var lastItem: Item
        try {
            do {
                val timeThreshold = Instant
                    .now().minus(properties.checkTimeOffset)
                val allItems = getItemsBatch(state.continuation)
                val inconsistentItemIds = inconsistentItemRepository.searchByIds(allItems.map { it.id }.toSet())
                    .map { it.id }
                    .toSet()

                val items = allItems.filterNot { inconsistentItemIds.contains(it.id) }
                logger.info("Got ${items.size} items to check (${inconsistentItemIds.size} skipped as already inconsistent)")
                if (items.isEmpty()) {
                    state.continuation = null
                    state.latestChecked = Instant.now()
                    break
                }

                for (item in items) {
                    lastItem = item
                    if (item.date > timeThreshold) {
                        running = false
                        break
                    } else {
                        itemsChannel.send(item)
                        checkedCounter.increment()
                        delayMetric.set((nowMillis().toEpochMilli() - item.date.toEpochMilli()))
                    }
                }

                // wait for workers to finish last items in batch, only then update & save state
                semaphore.waitUntilAvailable(properties.parallelism)
                updateState(state, lastItem)
                saveState(state)
            } while (running)
        } finally {
            saveState(state)
            itemsChannel.close()
        }
    }

    private suspend fun getItemsBatch(continuation: String?): List<Item> {
        return itemRepository.search(
            filter.toCriteria(
                ItemContinuation.parse(continuation),
                nftListenerProperties.elementsFetchJobSize
            )
        ).toList()
    }

    private suspend fun checkAndFixItem(item: Item) {
        if (inconsistentItemRepository.get(item.id) != null) {
            return
        }
        var checkResult = itemOwnershipConsistencyService.checkItem(item)
        when (checkResult) {
            is Failure -> {
                if (!properties.autofix) return

                val fixedItem = itemOwnershipConsistencyService.tryFix(item)
                checkResult = itemOwnershipConsistencyService.checkItem(fixedItem)
                when (checkResult) {
                    is Failure -> {
                        if (inconsistentItemRepository.save(
                                InconsistentItem(
                                    token = fixedItem.token,
                                    tokenId = fixedItem.tokenId,
                                    supply = checkResult.supply,
                                    ownerships = checkResult.ownerships,
                                    supplyValue = checkResult.supply.value,
                                    ownershipsValue = checkResult.ownerships.value,
                                    fixVersionApplied = 2,
                                    status = InconsistentItemStatus.UNFIXED,
                                    lastUpdatedAt = nowMillis(),
                                )
                            )
                        ) {
                            unfixedCounter.increment()
                        }
                    }

                    Success -> {
                        fixedCounter.increment()
                        logger.info("Item ${item.id} ownership consistency was fixed successfully")
                    }
                }
            }

            Success -> {
                // Do nothing, item<->ownerships is consistent, info logged
            }
        }
    }

    private suspend fun updateState(state: ItemOwnershipConsistencyJobState, item: Item) {
        state.latestChecked = item.date
        state.continuation = ItemContinuation(item.date, item.id).toString()
    }

    private suspend fun getState(): ItemOwnershipConsistencyJobState {
        return jobStateRepository.get(ITEM_OWNERSHIP_CONSISTENCY_JOB, ItemOwnershipConsistencyJobState::class.java)
            ?: ItemOwnershipConsistencyJobState()
    }

    private suspend fun saveState(state: ItemOwnershipConsistencyJobState) {
        jobStateRepository.save(ITEM_OWNERSHIP_CONSISTENCY_JOB, state)
    }

    private fun CoroutineScope.launchItemWorker(channel: ReceiveChannel<Item>, semaphore: Semaphore) {
        launch {
            for (item in channel) {
                semaphore.acquire()
                checkAndFixItem(item)
                semaphore.release()
            }
        }
    }

    private suspend fun Semaphore.waitUntilAvailable(permits: Int) {
        repeat(permits) { acquire() }
        repeat(permits) { release() }
    }

    data class ItemOwnershipConsistencyJobState(
        var continuation: String? = null,
        var latestChecked: Instant = Instant.EPOCH,
    )
}
