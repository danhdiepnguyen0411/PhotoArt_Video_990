package com.example.phortart_video_990.ui.screen.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.data.model.HomeMediaItemModel
import com.example.phortart_video_990.databinding.ItemHomeMediaCardBinding

class HomeMediaAdapter(
    private val items: List<HomeMediaItemModel>,
    private val onItemClick: (HomeMediaItemModel) -> Unit
) : RecyclerView.Adapter<HomeMediaAdapter.MediaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemHomeMediaCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MediaViewHolder(private val binding: ItemHomeMediaCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeMediaItemModel) {
            binding.ivMediaThumb.setImageResource(item.imageRes)
            binding.tvMediaBadge.text = item.label
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
