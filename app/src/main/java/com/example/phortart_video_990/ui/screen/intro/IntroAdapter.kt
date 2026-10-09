package com.example.phortart_video_990.ui.screen.intro

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
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

    override fun onViewDetachedFromWindow(holder: IntroViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.stopAnimation()
    }

    override fun onViewAttachedToWindow(holder: IntroViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.startFloatingAnimation()
    }

    override fun getItemCount(): Int = pages.size

    inner class IntroViewHolder(private val binding: ItemIntroPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var iconAnimator: ObjectAnimator? = null
        private var illustrationAnimator: ObjectAnimator? = null

        fun bind(page: IntroPageModel) {
            binding.ivIntroIcon.setImageResource(page.iconRes)
            binding.tvIntroTitle.text = page.title
            binding.tvIntroDesc.text = page.description
            binding.ivIntroIllustration.setImageResource(page.illustrationRes)

            startFloatingAnimation()
        }

        fun startFloatingAnimation() {
            stopAnimation()

            val density = binding.root.resources.displayMetrics.density
            val iconFloatPx = -7f * density
            val illFloatPx = -9f * density

            // Floating animation for top icon
            iconAnimator = ObjectAnimator.ofFloat(binding.ivIntroIcon, "translationY", 0f, iconFloatPx).apply {
                duration = 2200L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }

            // Floating animation for main illustration
            illustrationAnimator = ObjectAnimator.ofFloat(binding.ivIntroIllustration, "translationY", 0f, illFloatPx).apply {
                duration = 2600L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }

        fun stopAnimation() {
            iconAnimator?.cancel()
            iconAnimator = null
            illustrationAnimator?.cancel()
            illustrationAnimator = null
        }
    }
}
