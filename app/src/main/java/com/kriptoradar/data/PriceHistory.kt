package com.kriptoradar.data

/**
 * Sembol başına hafif halka tampon: son ~2 saatin fiyatlarını 5 sn çözünürlükte tutar.
 * Kısa pencerelerdeki (1-60 dk) ani hareketleri yakalamak için kullanılır.
 */
class PriceHistory(private val capacity: Int = 1500, private val minStepMs: Long = 5_000) {
    private val times = LongArray(capacity)
    private val prices = DoubleArray(capacity)
    private var head = 0
    private var size = 0

    @Synchronized
    fun add(time: Long, price: Double) {
        if (size > 0) {
            val lastIdx = (head - 1 + capacity) % capacity
            if (time - times[lastIdx] < minStepMs) {
                prices[lastIdx] = price // aynı dilimde en son fiyatı tut
                return
            }
        }
        times[head] = time
        prices[head] = price
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    /** [windowMs] önceki (veya o zamana en yakın daha eski) fiyata göre yüzde değişim. */
    @Synchronized
    fun changePct(now: Long, windowMs: Long, current: Double): Double? {
        if (size < 2) return null
        val target = now - windowMs
        val oldestIdx = (head - size + capacity) % capacity
        if (times[oldestIdx] > target) return null // yeterli geçmiş yok
        var ref = prices[oldestIdx]
        for (i in 0 until size) {
            val idx = (oldestIdx + i) % capacity
            if (times[idx] <= target) ref = prices[idx] else break
        }
        return if (ref > 0) (current - ref) / ref * 100.0 else null
    }

    @Synchronized
    fun snapshot(): List<Pair<Long, Double>> {
        val oldestIdx = (head - size + capacity) % capacity
        return List(size) { i -> val idx = (oldestIdx + i) % capacity; times[idx] to prices[idx] }
    }
}
