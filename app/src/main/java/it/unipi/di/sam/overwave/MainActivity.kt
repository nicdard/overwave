package it.unipi.di.sam.overwave

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import it.unipi.di.sam.overwave.receiver.ReceiveActivity
import it.unipi.di.sam.overwave.transmitter.TransmitActivity

class MainActivity : BaseMenuActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.to_transmit_activity_button).setOnClickListener(this)
        findViewById<Button>(R.id.to_receive_activity_button).setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.to_transmit_activity_button -> {
                val nextPage = Intent(this@MainActivity, TransmitActivity::class.java)
                startActivity(nextPage)
            }
            R.id.to_receive_activity_button -> {
                val nextPage = Intent(this@MainActivity, ReceiveActivity::class.java)
                startActivity(nextPage)
            }
        }
    }
}