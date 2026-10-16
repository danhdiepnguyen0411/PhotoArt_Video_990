package com.example.phortart_video_990.ui.screen.feature.createtemplate

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.utils.ShimmerDrawable
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.databinding.ItemDanceTemplatePickerBinding

class DanceTemplatePickerAdapter(
    private var items: List<TemplateModel>,
    private var selectedTemplateId: String? = null,
    private val onTemplateSelected: (TemplateModel) -> Unit
) : RecyclerView.Adapter<DanceTemplatePickerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDanceTemplatePickerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, item.id == selectedTemplateId)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<TemplateModel>) {
        items = newList
        if (selectedTemplateId == null || newList.none { it.id == selectedTemplateId }) {
            if (newList.isNotEmpty()) {
                selectedTemplateId = newList[0].id
            }
        }
        notifyDataSetChanged()
    }

    fun setSelectedId(id: String?) {
        val oldIndex = items.indexOfFirst { it.id == selectedTemplateId }
        selectedTemplateId = id
        val newIndex = items.indexOfFirst { it.id == selectedTemplateId }

        if (oldIndex != -1) notifyItemChanged(oldIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
        if (oldIndex == -1) notifyDataSetChanged()
    }

    fun getSelectedTemplate(): TemplateModel? {
        return items.find { it.id == selectedTemplateId } ?: items.firstOrNull()
    }

    fun getSelectedTemplateId(): String? = selectedTemplateId

    inner class ViewHolder(private val binding: ItemDanceTemplatePickerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateModel, isSelected: Boolean) {
            binding.tvTemplateTitle.text = item.title
            binding.tvViews.text = item.displayViews

            // Highlight border when selected
            if (isSelected) {
                binding.cardTemplate.strokeColor = Color.parseColor("#7A5AF8")
                binding.cardTemplate.strokeWidth = (2 * binding.root.resources.displayMetrics.density).toInt()
                binding.flSelectedBadge.visibility = View.VISIBLE
            } else {
                binding.cardTemplate.strokeColor = Color.parseColor("#EFF2F7")
                binding.cardTemplate.strokeWidth = (1.5f * binding.root.resources.displayMetrics.density).toInt()
                binding.flSelectedBadge.visibility = View.GONE
            }

            val shimmer = ShimmerDrawable(cornerRadius = 14f)
            val imgSource = item.safeImageUrl?.takeIf { it.isNotBlank() }
                ?: (if (item.localImageRes != 0) item.localImageRes else "") ?: ""

            binding.ivTemplateThumb.load(imgSource) {
                crossfade(true)
                placeholder(shimmer)
                error(shimmer)
            }

            binding.cardTemplate.setOnClickListener {
                setSelectedId(item.id)
                onTemplateSelected(item)
            }
        }
    }
}
