package dz.mawashi.app

import java.time.LocalDate

/**
 * Values medicine sales using the same FEFO order used to consume stock.
 * Legacy lots without a stored unit cost use the historical weighted average.
 * Returns null when the requested quantity cannot be covered by usable lots.
 */
internal fun medicineLotCostForSale(
    item: String,
    quantity: Double,
    lots: List<MedicineLot>,
    today: LocalDate,
    fallbackUnitCost: Double
): Double? {
    if (!quantity.isFinite() || quantity <= 0.0) return null
    if (!fallbackUnitCost.isFinite() || fallbackUnitCost < 0.0) return null

    val eligible = lots.asSequence()
        .filter { it.item == item && it.quantity.isFinite() && it.quantity > 0.0 }
        .filter { isMedicineLotUsable(it.expiry, today) }
        .sortedBy { LocalDate.parse(it.expiry) }
        .toList()

    var remaining = quantity
    var totalCost = 0.0
    for (lot in eligible) {
        if (remaining <= 1e-8) break
        val used = minOf(lot.quantity, remaining)
        val unitCost = if (lot.unitCost.isFinite() && lot.unitCost > 0.0) {
            lot.unitCost
        } else {
            fallbackUnitCost
        }
        totalCost += used * unitCost
        remaining -= used
    }

    if (remaining > 1e-8 || !totalCost.isFinite() || totalCost < 0.0) return null
    return totalCost
}
