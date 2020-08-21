package it.unipi.di.sam.overwave.receivers

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

/**
 * A [Receiver] uses one or more sensors to retrieve data
 * emitted through a physical system by another device.
 *
 * A [Receiver] is a lifecycle-aware component as advised
 * in the android developers guidelines for network connection.
 */
internal abstract class Receiver(
    protected val context: Context,
    protected val onEnd: (data: List<Byte>, isSuccessful: Boolean) -> Unit,
    protected val frequency: Int = 60
) : LifecycleObserver {

    /**
     * Listens for data transmission and retrieves them.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    abstract fun start()

    /**
     * Stops listening for data.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    abstract fun stop()
}