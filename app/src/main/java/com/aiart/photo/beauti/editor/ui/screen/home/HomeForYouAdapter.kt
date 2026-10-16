package com.aiart.photo.beauti.editor.ui.screen.home

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.aiart.photo.beauti.editor.R
import com.aiart.photo.beauti.editor.core.utils.ShimmerDrawable
import com.aiart.photo.beauti.editor.data.model.TemplateModel
import com.aiart.photo.beauti.editor.databinding.ItemHomeForYouCardBinding

class HomeForYouAdapter(
    private var items: List<TemplateModel>,
    private val onItemClick: (TemplateModel) -> Unit,
    private val onPlayClick: ((TemplateModel, Int) -> Unit)? = null
) : RecyclerView.Adapter<HomeForYouAdapter.ForYouViewHolder>() {

    private var playingPosition: Int = -1
    private var loadingPosition: Int = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForYouViewHolder {
        val binding = ItemHomeForYouCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ForYouViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ForYouViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newList: List<TemplateModel>) {
        items = newList
        notifyDataSetChanged()
    }

    fun setPlayingState(position: Int, isPlaying: Boolean) {
        val old = playingPosition
        playingPosition = if (isPlaying) position else -1
        loadingPosition = -1
        if (old != -1 && old < items.size) notifyItemChanged(old)
        if (position != -1 && position < items.size) notifyItemChanged(position)
    }

    fun setLoadingState(position: Int) {
        val old = loadingPosition
        loadingPosition = position
        if (old != -1 && old < items.size) notifyItemChanged(old)
        if (position != -1 && position < items.size) notifyItemChanged(position)
    }

    inner class ForYouViewHolder(private val binding: ItemHomeForYouCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TemplateModel, position: Int) {
            binding.tvTemplateTitle.text = item.title
            binding.tvViews.text = item.displayViews

            // Category tag & styling
            val categoryName = item.categoryDisplayName
            binding.tvCategoryTag.text = categoryName

            val (bgColor, textColor) = when (categoryName.uppercase()) {
                "HIP HOP", "HIPHOP" -> Color.parseColor("#FEF3C7") to Color.parseColor("#D97706")
                "EDM" -> Color.parseColor("#EDE9FE") to Color.parseColor("#7C3AED")
                "LOFI" -> Color.parseColor("#E0F2FE") to Color.parseColor("#0284C7")
                "POP" -> Color.parseColor("#FCE7F3") to Color.parseColor("#DB2777")
                "CHILL" -> Color.parseColor("#DCFCE7") to Color.parseColor("#16A34A")
                "CINEMATIC" -> Color.parseColor("#F3E8FF") to Color.parseColor("#9333EA")
                "BASS" -> Color.parseColor("#FEE2E2") to Color.parseColor("#DC2626")
                "ROCK" -> Color.parseColor("#FEE2E2") to Color.parseColor("#B91C1C")
                "AI DANCE" -> Color.parseColor("#DCFCE7") to Color.parseColor("#16A34A")
                "AI STYLE" -> Color.parseColor("#E0F2FE") to Color.parseColor("#0284C7")
                "AI EFFECT" -> Color.parseColor("#EDE9FE") to Color.parseColor("#7C3AED")
                "FANTASY" -> Color.parseColor("#EDE9FE") to Color.parseColor("#9333EA")
                "PORTRAIT" -> Color.parseColor("#E0F2FE") to Color.parseColor("#2563EB")
                else -> Color.parseColor("#F1F5F9") to Color.parseColor("#475569")
            }

            val pillDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 999f
                setColor(bgColor)
            }
            binding.tvCategoryTag.background = pillDrawable
            binding.tvCategoryTag.setTextColor(textColor)

            // Image Thumbnail with shimmer loading light beam (NO static template images as placeholder)
            val shimmerPlaceholder = ShimmerDrawable()
            val imageSource: Any = item.safeImageUrl?.takeIf { it.isNotBlank() }
                ?: item.localImageRes
                ?: ""

            binding.ivTemplateThumb.load(imageSource) {
                crossfade(true)
                placeholder(shimmerPlaceholder)
                error(shimmerPlaceholder)
            }

            // Audio preview play state
            val isPlaying = position == playingPosition
            val isLoading = position == loadingPosition

            if (isLoading) {
                binding.pbLoading.visibility = View.VISIBLE
                binding.ivPlayIcon.visibility = View.GONE
            } else {
                binding.pbLoading.visibility = View.GONE
                binding.ivPlayIcon.visibility = View.VISIBLE
                binding.ivPlayIcon.setImageResource(
                    if (isPlaying) R.drawable.ic_pause_small else R.drawable.ic_play_small
                )
            }

            binding.llDurationBadge.isClickable = false
            binding.llDurationBadge.isFocusable = false

            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
