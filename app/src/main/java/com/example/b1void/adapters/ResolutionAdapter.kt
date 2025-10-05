package com.example.b1void.adapters

import android.graphics.Color
import android.util.Size
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R

class ResolutionAdapter(
    private val resolutions: List<Size>,
    private var selectedResolution: Size?,
    private val onResolutionSelected: (Size) -> Unit
) : RecyclerView.Adapter<ResolutionAdapter.ResolutionViewHolder>() {

    class ResolutionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val resolutionText: TextView = itemView.findViewById(R.id.resolution_text)
        val checkmarkImage: ImageView = itemView.findViewById(R.id.checkmark_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResolutionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_resolution, parent, false)
        return ResolutionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResolutionViewHolder, position: Int) {
        val size = resolutions[position]
        val isSelected = size == selectedResolution

        holder.resolutionText.text = "${size.width} x ${size.height}"
        
        // Show checkmark for selected resolution
        holder.checkmarkImage.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onResolutionSelected(size)
        }
    }

    override fun getItemCount(): Int = resolutions.size
    
    fun updateSelectedResolution(newSelectedResolution: Size?) {
        selectedResolution = newSelectedResolution
        notifyDataSetChanged()
    }
}