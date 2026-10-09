package com.example.phortart_video_990.ui.screen.intro

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.phortart_video_990.data.model.IntroPageModel
import com.example.phortart_video_990.databinding.ItemIntroPageBinding

class IntroAdapter(
    private val pages: List<IntroPageModel>
) : RecyclerView.Adapter<IntroAdapter.IntroViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IntroViewHolder {
        val binding = ItemIntroPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IntroViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IntroViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    inner class IntroViewHolder(private val binding: ItemIntroPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(page: IntroPageModel) {
            binding.ivIntroIcon.setImageResource(page.iconRes)
            binding.tvIntroTitle.text = page.title
            binding.tvIntroDesc.text = page.description
            binding.ivIntroIllustration.setImageResource(page.illustrationRes)
        }
    }
}
