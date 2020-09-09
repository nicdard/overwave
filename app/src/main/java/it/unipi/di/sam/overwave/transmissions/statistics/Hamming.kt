package it.unipi.di.sam.overwave.transmissions.statistics

data class EncodedString(val value: String) {
    operator fun get(index: Int): Char = value[index]
    val length get() = value.length
}

private fun EncodedString.withBitFlippedAt(index: Int) = this[index].toString().toInt()
    .let { this.value.replaceRange(index, index + 1, ((it + 1) % 2).toString()) }
    .let(::EncodedString)

data class BinaryString(val value: String) {
    operator fun get(index: Int): Char = value[index]
    val length get() = value.length
}

fun codewordSize(msgLength: Int) = generateSequence(2) { it + 1 }
    .first { r -> msgLength + r + 1 <= (1 shl r) } + msgLength

fun parityIndicesSequence(start: Int, endEx: Int) = generateSequence(start) { it + 1 }
    .take(endEx - start)
    .filterIndexed { i, _ -> i % ((2 * (start + 1))) < start + 1 }
    .drop(1) // ignore the initial parity bit

fun getParityBit(codeWordIndex: Int, msg: BinaryString) =
    parityIndicesSequence(codeWordIndex, codewordSize(msg.value.length))
        .map { getDataBit(it, msg).toInt() }
        .reduce { a, b -> a xor b }
        .toString()

fun getHammingCodewordIndices(msgLength: Int) = generateSequence(0, Int::inc)
    .take(codewordSize(msgLength))

fun getDataBit(ind: Int, input: BinaryString) = input
    .value[ind - Integer.toBinaryString(ind).length].toString()

internal fun Int.isPowerOfTwo() = this != 0 && this and this - 1 == 0

fun encode(input: BinaryString): EncodedString {
    fun toHammingCodeValue(it: Int, input: BinaryString) =
        when ((it + 1).isPowerOfTwo()) {
            true -> getParityBit(it, input)
            false -> getDataBit(it, input)
        }

    return getHammingCodewordIndices(input.value.length)
        .map { toHammingCodeValue(it, input) }
        .joinToString("")
        .let(::EncodedString)
}


fun stripHammingMetadata(input: EncodedString): BinaryString {
    return input.value.asSequence()
        .filterIndexed { i, _ -> (i + 1).isPowerOfTwo().not() }
        .joinToString("")
        .let(::BinaryString)
}

private fun Char.toBinaryInt() = this.toString().toInt()

private fun indexesOfInvalidParityBits(input: EncodedString): List<Int> {
    fun toValidationResult(it: Int, input: EncodedString): Pair<Int, Boolean> =
        parityIndicesSequence(it - 1, input.length)
            .map { v -> input[v].toBinaryInt() }
            .fold(input[it - 1].toBinaryInt()) { a, b -> a xor b }
            .let { r -> it to (r == 0) }

    return generateSequence(1) { it * 2 }
        .takeWhile { it < input.length }
        .map { toValidationResult(it, input) }
        .filter { !it.second } // take only failed
        .map { it.first }  // extract only value
        .toList()
}

fun isValid(codeWord: EncodedString) =
    indexesOfInvalidParityBits(codeWord).isEmpty()

fun decode(codeWord: EncodedString): BinaryString =
    indexesOfInvalidParityBits(codeWord).let { result ->
        when (result.isEmpty()) {
            true -> codeWord
            false -> codeWord.withBitFlippedAt(result.sum() - 1)
        }.let { stripHammingMetadata(it) }
    }