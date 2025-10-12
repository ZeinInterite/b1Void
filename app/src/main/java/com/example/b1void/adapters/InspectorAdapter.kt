package com.example.b1void.adapters

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.b1void.R
import com.example.b1void.models.Inspector
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class InspectorAdapter(private var inspectors: List<Inspector>, private val longClickListener: OnItemLongClickListener, private val shortClickListener: OnItemShortClickListener) :
    RecyclerView.Adapter<InspectorAdapter.InspectorViewHolder>() {

    interface OnItemLongClickListener {
        fun onItemLongClick(inspector: Inspector)
    }

    interface OnItemShortClickListener {
        fun onItemShortClick(inspector: Inspector)
    }

    class InspectorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPhoto: ImageView = itemView.findViewById(R.id.iv_inspector_photo)
        val tvName: TextView = itemView.findViewById(R.id.tv_inspector_name)
        val tvCode: TextView = itemView.findViewById(R.id.tv_inspector_code)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InspectorViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_inspector, parent, false)
        return InspectorViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: InspectorViewHolder, position: Int) {
        val currentInspector = inspectors[position]

        holder.tvName.text = currentInspector.name
        holder.tvCode.text = currentInspector.code

        if (currentInspector.localPhotoPath != null) {
            val file = File(currentInspector.localPhotoPath)
            Glide.with(holder.itemView.context)
                .load(file)
                .thumbnail(0.25f)
                .centerCrop()
                .placeholder(R.drawable.def_insp_img)
                .error(R.drawable.def_insp_img)
                .into(holder.ivPhoto)
        } else {
            holder.ivPhoto.setImageResource(R.drawable.def_insp_img)
        }

        holder.itemView.setOnLongClickListener {
            longClickListener.onItemLongClick(currentInspector)
            true
        }
        holder.itemView.setOnClickListener {
            shortClickListener.onItemShortClick(currentInspector)
        }
    }

    private fun loadBitmapFromPath(path: String): Bitmap? {
        // No longer used; keep stub to avoid API break if referenced elsewhere
        return null
    }


    override fun getItemCount() = inspectors.size

    fun updateList(newInspectors: List<Inspector>) {
        inspectors = newInspectors
        notifyDataSetChanged()
    }
}
