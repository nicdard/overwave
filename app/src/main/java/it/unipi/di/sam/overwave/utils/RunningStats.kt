package it.unipi.di.sam.overwave.utils

import java.math.BigDecimal
import kotlin.math.sqrt

/**
 * Online variance and Mean calculation through Welford algorithms.
 */
class RunningStats {

    private var mN = 0
    private var mOldM = BigDecimal.ZERO
    private var mNewM = BigDecimal.ZERO
    private var mOldS = BigDecimal.ZERO
    private var mNewS = BigDecimal.ZERO

    fun clear() {
        mN = 0
    }

    fun push(x: BigDecimal) {
        ++mN
        if (mN == 1) {
            mOldM = x
            mNewM = x
            mOldS = BigDecimal.ZERO
        } else {
            mNewM = mOldM + (x - mOldM) / mN.toBigDecimal()
            mNewS = mOldS + (x - mOldM) * (x - mNewM)

            mOldM = mNewM
            mOldS = mNewS
        }
    }

    fun mean() = if (mN > 0) mNewM else BigDecimal.ZERO

    fun variance() = if (mN > 1) mNewS / (mN - 1).toBigDecimal() else BigDecimal.ZERO

    fun standardDeviation() = sqrt(this.variance().toDouble())
}