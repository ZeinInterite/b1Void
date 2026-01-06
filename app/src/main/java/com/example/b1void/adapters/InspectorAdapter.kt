package com.example.b1void.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.core.model.Inspector

// Stubbed class to fix build
class InspectorAdapter(
    private var inspectors: List<Inspector>,
    private val longClickListener: OnItemLongClickListener,
    private val shortClickListener: OnItemShortClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnItemLongClickListener {
        fun onItemLongClick(inspector: Inspector)
    }

    interface OnItemShortClickListener {
        fun onItemShortClick(inspector: Inspector)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        TODO("Not yet implemented")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int = inspectors.size

    fun updateList(newList: List<Inspector>) {
        inspectors = newList
        notifyDataSetChanged()
    }
}