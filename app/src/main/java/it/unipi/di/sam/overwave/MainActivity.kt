package it.unipi.di.sam.overwave

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.to_transmit_activity_button).setOnClickListener(this)
        findViewById<Button>(R.id.to_receive_activity_button).setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when(v?.id) {
            R.id.to_transmit_activity_button -> {
                val transmitterIntent = Intent(this, TransmitterActivity::class.java)
                startActivity(transmitterIntent)
            }
            R.id.to_receive_activity_button -> {
                val receiverIntent = Intent(this, ReceiverActivity::class.java)
                startActivity(receiverIntent)
            }
        }
    }
}