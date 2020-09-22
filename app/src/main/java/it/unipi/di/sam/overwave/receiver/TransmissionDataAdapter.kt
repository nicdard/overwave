package it.unipi.di.sam.overwave.receiver

import android.content.ClipData
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.unipi.di.sam.overwave.database.Transmission
import it.unipi.di.sam.overwave.databinding.ListItemTransmissionBindingBinding

class TransmissionDataAdapter : ListAdapter<Transmission, ViewHolder>(TransmissionDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder.from(parent)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }
}

class TransmissionDiffCallback : DiffUtil.ItemCallback<Transmission>() {
    override fun areItemsTheSame(oldItem: Transmission, newItem: Transmission): Boolean {
        return oldItem.id == newItem.id
    }
    override fun areContentsTheSame(oldItem: Transmission, newItem: Transmission): Boolean {
        return oldItem == newItem
    }
}

class ViewHolder private constructor(
    private val binding: ListItemTransmissionBindingBinding
) : RecyclerView.ViewHolder(binding.root),
    View.OnLongClickListener
{

    init {
        binding.root.setOnLongClickListener(this)
    }

    fun bind(item: Transmission) {
        binding.transmission = item
        binding.executePendingBindings()
    }

    companion object {
        fun from(parent: ViewGroup): ViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = ListItemTransmissionBindingBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(binding)
        }
    }

    override fun onLongClick(view: View): Boolean {
        setClipboard(view, binding.decodedText.text.toString())
        Toast.makeText(view.context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        // Return true to indicate the click was handled
        return true
    }

    private fun setClipboard(view: View, text: String) {
        val clipboard =
            view.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = ClipData.newPlainText("Copied Text", text)
        clipboard.setPrimaryClip(clip)
    }
}