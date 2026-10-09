package com.example.phortart_video_990.ui.screen.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.data.model.HomeMediaItemModel
import com.example.phortart_video_990.databinding.ItemHomeMediaCardBinding

class HomeMediaAdapter(
    private var items: List<HomeMediaItemModel>,
    private val onItemClick: (HomeMediaItemModel) -> Unit
) : RecyclerView.Adapter<HomeMediaAdapter.MediaViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemHomeMediaCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<HomeMediaItemModel>) {
        items = newList
        notifyDataSetChanged()
    }

    inner class MediaViewHolder(private val binding: ItemHomeMediaCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HomeMediaItemModel) {
            val source: Any? = item.imageUri ?: item.imageUrl?.trim()?.ifBlank { null } ?: if (item.imageRes != 0) item.imageRes else null

            if (source != null) {
                binding.ivMediaThumb.load(source) {
                    crossfade(true)
                    placeholder(R.drawable.bg_card_white)
                    error(R.drawable.bg_card_white)
                }
            } else {
                binding.ivMediaThumb.setImageResource(R.drawable.bg_card_white)
            }

            binding.tvMediaBadge.text = item.label
            binding.tvMediaBadge.isSelected = true

            if (item.iconRes != 0) {
                binding.ivBadgeIcon.visibility = View.VISIBLE
                binding.ivBadgeIcon.setImageResource(item.iconRes)
                if (item.iconTint != null) {
                    binding.ivBadgeIcon.imageTintList = ColorStateList.valueOf(item.iconTint)
                } else {
                    binding.ivBadgeIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#1B2A6B"))
                }
            } else {
                binding.ivBadgeIcon.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
