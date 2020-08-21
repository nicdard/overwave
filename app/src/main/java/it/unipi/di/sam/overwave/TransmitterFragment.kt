package it.unipi.di.sam.overwave

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment

private const val KEY_TEXT = "text_key"

class TransmitterFragment : Fragment(), View.OnClickListener {

    private lateinit var transmitButton: Button
    private lateinit var transmissionEditText: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment.
        val rootView = inflater.inflate(R.layout.transmitter_fragment, container, false)
        transmissionEditText = rootView.findViewById(R.id.transfer_edit_text)
        savedInstanceState?.run {
            transmissionEditText.setText(getString(KEY_TEXT))
        }
        transmitButton = rootView.findViewById(R.id.transfer_button)
        transmitButton.setOnClickListener(this)
        return rootView
    }

    // Avoid losing content to be send on orientation change.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TEXT, transmissionEditText.text?.toString() ?: "")
    }

    override fun onClick(v: View?) {
        Log.i("TransmitterFragment", "clicked")
        when (v?.id) {
            R.id.transfer_button -> {
                // Explicit intent: we do want to call exactly the transmission activity.
                val sendIntent = Intent(requireContext(), TransmissionActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "This is my text to send.")
                    type = "text/plain"
                }
                Log.i("TransmitterFragment", "clicked ${v.id} ${sendIntent}")
                startActivity(sendIntent)
            }
            else -> throw IllegalArgumentException("Unknown click listener implementation for ${v?.id ?: "null"}")
        }
    }
}