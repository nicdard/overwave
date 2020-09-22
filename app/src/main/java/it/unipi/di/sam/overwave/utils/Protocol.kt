package it.unipi.di.sam.overwave.utils

const val START = "11110101"
private const val END = "10"

fun dataToBinaryString(data: ByteArray): String = data.joinToString(prefix = START, separator = "", postfix = END) {
    // Get the binary string representation of the byte
    Integer.toBinaryString(it.toInt())
        // 8-bit 0-padded string.
        .padStart(8, '0')
        // 16-bit: encode each bit in a sequence of 2 equal bits
        .replace("0", "00").replace("1", "11")
}

fun decode(signal: String) = signal
    .dropWhile { it == '0' }
    .dropWhile { it == '1' }
    .drop(4)
    .dropLastWhile { it == '0' }
    .dropLast(1)
    .replace("00", "0").replace("11", "1")
    .chunked(8)
    .map { it.toInt(2).toChar() }
    .joinToString("")

/**
 * The default frequency in milliseconds.
 */
const val DEFAULT_FREQUENCY = 50