package com.example.gifkeyboard.ime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.gifkeyboard.R
import com.example.gifkeyboard.data.MediaCategory

class CategoryChipAdapter(
    private val categories: List<MediaCategory>,
    private val onCategorySelected: (MediaCategory) -> Unit
) : RecyclerView.Adapter<CategoryChipAdapter.ViewHolder>() {

    private var selectedIndex = 0

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.categoryLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_chip, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.label.text = category.displayName
        holder.label.isSelected = position == selectedIndex
        holder.itemView.setOnClickListener {
            val previous = selectedIndex
            selectedIndex = position
            notifyItemChanged(previous)
            notifyItemChanged(selectedIndex)
            onCategorySelected(category)
        }
    }

    override fun getItemCount() = categories.size
}
