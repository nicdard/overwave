package it.unipi.di.sam.overwave.connectors

import android.content.Context

object ImageConnector: Connector {

    override val frequency: Int
        get() = TODO("Not yet implemented")

    override val context: Context
        get() = TODO("Not yet implemented")
    override val onEnd: (data: List<Byte>, isSuccessful: Boolean) -> Unit
        get() = TODO("Not yet implemented")

    override fun transmit(data: List<Byte>) {
        TODO("Not yet implemented")
    }

    override fun receive() {
        TODO("Not yet implemented")
    }

    fun registerOnEnd(onEnd: (data: List<Byte>) -> Unit) {
        TODO("Not yet implemented")
    }

    fun onConnectionEnd(onEnd: (data: ByteArray) -> Unit) {
        TODO("Not yet implemented")
    }

}