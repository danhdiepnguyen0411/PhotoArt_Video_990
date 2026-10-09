package com.example.phortart_video_990.ui.screen.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.data.model.BannerItemModel
import com.example.phortart_video_990.databinding.ItemBannerSlideBinding

class BannerAdapter(
    private val items: List<BannerItemModel>,
    private val onItemClick: (BannerItemModel) -> Unit
) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val binding = ItemBannerSlideBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BannerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class BannerViewHolder(private val binding: ItemBannerSlideBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BannerItemModel) {
            binding.ivBannerBg.setImageResource(item.imageRes)
            binding.tvBannerChip.text = item.chip
            binding.tvBannerTitle.text = item.title
            binding.tvBannerDesc.text = item.description
            binding.btnBannerAction.text = item.buttonText

            val clickListener = { onItemClick(item) }
            binding.btnBannerAction.setOnClickListener { clickListener() }
            binding.root.setOnClickListener { clickListener() }
        }
    }
}
