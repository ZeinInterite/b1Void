package com.example.b1void.adapters

import android.graphics.Color
import android.util.Size
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R

class ResolutionAdapter(
    private val resolutions: List<Size>,
    private val selectedResolution: Size?,
    private val onResolutionSelected: (Size) -> Unit
) : RecyclerView.Adapter<ResolutionAdapter.ResolutionViewHolder>() {

    class ResolutionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val resolutionText: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResolutionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ResolutionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResolutionViewHolder, position: Int) {
        val size = resolutions[position]
        val isSelected = size == selectedResolution

        holder.resolutionText.text = if (isSelected) {
            "${size.width} x ${size.height} ✓"
        } else {
            "${size.width} x ${size.height}"
        }

        if (isSelected) {
            holder.resolutionText.setTextColor(Color.YELLOW)
        } else {
            holder.resolutionText.setTextColor(Color.WHITE)
        }

        holder.itemView.setOnClickListener {
            onResolutionSelected(size)
        }
    }

    override fun getItemCount(): Int = resolutions.size
}