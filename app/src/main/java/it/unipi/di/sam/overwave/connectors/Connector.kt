package it.unipi.di.sam.overwave.connectors

import android.content.Context
import androidx.annotation.RequiresApi

/**
 * A Connector provides the base functionality to transmit and receive some raw data.
 */
interface Connector {

    val frequency: Int

    val context: Context

    val onEnd: (data: List<Byte>, isSuccessful: Boolean) -> Unit

    /**
     * Transmits [data].
     */
    fun transmit(data: List<Byte>)

    /**
     * Listens for data transmission and retrieves them.
     */
    fun receive()
}