package com.adsim.simulation

import com.adsim.model.AdPlacement
import com.adsim.model.PlacementPrior
import com.adsim.model.PlacementPriorRepository
import com.adsim.model.PlacementPriorSnapshot
import com.adsim.model.PlacementType
import com.adsim.model.Product
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PriorCalibrationService(
    private val placementPriorRepository: PlacementPriorRepository
) {

    fun loadPrior(platform: String, placementType: PlacementType, category: String): PlacementPrior? {
        return placementPriorRepository.findByPlatformAndPlacementTypeAndCategory(platform, placementType, category)
    }

    fun updatePrior(
        placement: AdPlacement,
        product: Product,
        simulatedAttention: Double?,
        simulatedCtr: Double?,
        simulatedCvr: Double?,
        actualCtr: Double?,
        actualCvr: Double?
    ): PlacementPrior? {
        if (actualCtr == null && actualCvr == null) return null

        val existing = loadPrior(placement.platform, placement.placementType, product.category)
        val nextCount = (existing?.calibrationCount ?: 0) + 1

        val nextAttention = blendProbability(
            existing = existing?.baseAttention,
            observed = null,
            fallback = simulatedAttention ?: existing?.baseAttention ?: DEFAULT_ATTENTION
        )
        val nextClick = blendProbability(
            existing = existing?.baseClick,
            observed = actualCtr,
            fallback = simulatedCtr ?: existing?.baseClick ?: DEFAULT_CLICK
        )
        val nextConversion = blendProbability(
            existing = existing?.baseConversion,
            observed = actualCvr,
            fallback = simulatedCvr ?: existing?.baseConversion ?: DEFAULT_CONVERSION
        )

        val updated = PlacementPrior(
            id = existing?.id,
            platform = placement.platform,
            placementType = placement.placementType,
            category = product.category,
            baseAttention = nextAttention,
            baseClick = nextClick,
            baseConversion = nextConversion,
            calibrationCount = nextCount,
            updatedAt = Instant.now()
        )
        return placementPriorRepository.save(updated)
    }

    fun toSnapshot(prior: PlacementPrior): PlacementPriorSnapshot {
        return PlacementPriorSnapshot(
            baseAttention = prior.baseAttention,
            baseClick = prior.baseClick,
            baseConversion = prior.baseConversion,
            calibrationCount = prior.calibrationCount
        )
    }

    fun applyPrior(probability: Double, priorBase: Double?): Double {
        val normalizedPrior = priorBase?.coerceIn(0.0, 1.0) ?: return probability.coerceIn(0.0, 1.0)
        return (probability * LLM_WEIGHT + normalizedPrior * PRIOR_WEIGHT).coerceIn(0.0, 1.0)
    }

    fun deliveryMixWeight(platform: String, placementType: PlacementType, category: String): Double {
        return deliveryMixWeight(loadPrior(platform, placementType, category))
    }

    fun deliveryMixWeight(prior: PlacementPrior?): Double {
        if (prior == null) return 1.0
        val score = (
            prior.baseAttention * 0.7 +
                prior.baseClick * 0.2 +
                prior.baseConversion * 0.1
            ).coerceIn(0.0, 1.0)
        return (0.85 + score * 0.3).coerceIn(0.85, 1.15)
    }

    private fun blendProbability(existing: Double?, observed: Double?, fallback: Double): Double {
        if (observed == null) return (existing ?: fallback).coerceIn(0.0, 1.0)
        if (existing == null) return observed.coerceIn(0.0, 1.0)
        return ((existing * HISTORY_WEIGHT) + (observed * OBSERVED_WEIGHT)).coerceIn(0.0, 1.0)
    }

    companion object {
        private const val DEFAULT_ATTENTION = 1.0
        private const val DEFAULT_CLICK = 1.0
        private const val DEFAULT_CONVERSION = 1.0
        private const val LLM_WEIGHT = 0.8
        private const val PRIOR_WEIGHT = 0.2
        private const val HISTORY_WEIGHT = 0.7
        private const val OBSERVED_WEIGHT = 0.3
    }
}
