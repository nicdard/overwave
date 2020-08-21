package it.unipi.di.sam.overwave

import android.os.Bundle
import android.view.*
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI

class TitleFragment : Fragment(), View.OnClickListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        // Inflate the layout for this fragment.
        val rootView = inflater.inflate(R.layout.title_fragment, container, false);
        // Set click listeners
        rootView.findViewById<Button>(R.id.to_transmit_activity_button).setOnClickListener(this)
        rootView.findViewById<Button>(R.id.to_receive_activity_button).setOnClickListener(this)
        rootView.findViewById<Button>(R.id.to_lab_activity_button).setOnClickListener(this)
        // Set option menu
        setHasOptionsMenu(true)
        return rootView
    }

    override fun onClick(v: View?) {
        if (v != null) {
            when (v.id) {
                R.id.to_transmit_activity_button -> v.findNavController().navigate(R.id.action_titleFragment_to_transmitterFragment)
                R.id.to_receive_activity_button -> v.findNavController().navigate(R.id.action_titleFragment_to_receiverFragment)
                R.id.to_lab_activity_button -> v.findNavController().navigate(R.id.action_titleFragment_to_laboratoryFragment)
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.options_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return NavigationUI.onNavDestinationSelected(item, requireView().findNavController())
                || super.onOptionsItemSelected(item)
    }
}