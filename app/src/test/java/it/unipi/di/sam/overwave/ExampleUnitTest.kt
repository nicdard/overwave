package it.unipi.di.sam.overwave

import it.unipi.di.sam.overwave.utils.dataToBinaryString
import it.unipi.di.sam.overwave.utils.decode
import org.junit.Assert
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {

    @Test
    fun encodeAndDecode_areInverses() {
        val binaryString = dataToBinaryString("ciao".toByteArray())
        println(binaryString)
        val data = decode(binaryString)
        assertEquals("ciao", data)
    }
}
