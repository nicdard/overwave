package it.unipi.di.sam.overwave.utils

import org.apache.commons.math3.stat.descriptive.SummaryStatistics


/**
 * Smoothed zero-score alogrithm shamelessly copied from https://stackoverflow.com/a/22640362/6029703
 * Uses a rolling mean and a rolling deviation (separate) to identify peaks in a vector
 *
 * @param y - The input vector to analyze
 * @param lag - The lag of the moving window (i.e. how big the window is)
 * @param threshold - The z-score at which the algorithm signals (i.e. how many standard deviations away from the moving mean a peak (or signal) is)
 * @param influence - The influence (between 0 and 1) of new signals on the mean and standard deviation (how much a peak (or signal) should affect other values near it)
 * @return - The calculated averages (avgFilter) and deviations (stdFilter), and the signals (signals)
 */
fun smoothedZScore(y: List<Double>, lag: Int, threshold: Double, influence: Double):
        Triple<List<Int>, List<Double>, List<Double>> {
    val stats = SummaryStatistics()

    // the results (peaks, 1 or -1) of our algorithm
    val signals = MutableList<Int>(y.size, { 0 })

    // filter out the signals (peaks) from our original list (using influence arg)
    val filteredY = ArrayList<Double>(y)

    // the current average of the rolling window
    val avgFilter = MutableList<Double>(y.size, { 0.0 })

    // the current standard deviation of the rolling window
    val stdFilter = MutableList<Double>(y.size, { 0.0 })

    // init avgFilter and stdFilter
    y.take(lag).forEach { s -> stats.addValue(s) }

    avgFilter[lag - 1] = stats.mean
    stdFilter[lag - 1] = Math.sqrt(stats.populationVariance) // getStandardDeviation() uses sample variance (not what we want)

    stats.clear()
    //loop input starting at end of rolling window
    (lag..y.size - 1).forEach { i ->
        //if the distance between the current value and average is enough standard deviations (threshold) away
        if (Math.abs(y[i] - avgFilter[i - 1]) > threshold * stdFilter[i - 1]) {
            //this is a signal (i.e. peak), determine if it is a positive or negative signal
            signals[i] = if (y[i] > avgFilter[i - 1]) 1 else -1
            //filter this signal out using influence
            filteredY[i] = (influence * y[i]) + ((1 - influence) * filteredY[i - 1])
        } else {
            //ensure this signal remains a zero
            signals[i] = 0
            //ensure this value is not filtered
            filteredY[i] = y[i]
        }
        //update rolling average and deviation
        (i - lag..i - 1).forEach { stats.addValue(filteredY[it]) }
        avgFilter[i] = stats.getMean()
        stdFilter[i] = Math.sqrt(stats.getPopulationVariance()) //getStandardDeviation() uses sample variance (not what we want)
        stats.clear()
    }
    return Triple(signals, avgFilter, stdFilter)
}