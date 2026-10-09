package com.example.phortart_video_990.ui.screen.home

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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
            binding.tvBannerHighlight.text = item.highlight
            binding.tvBannerDesc.text = item.description
            binding.btnBannerAction.text = item.buttonText

            binding.ivBannerBg.scaleType = android.widget.ImageView.ScaleType.MATRIX
            val updateImageMatrix = {
                val d = binding.ivBannerBg.drawable
                if (d != null) {
                    val dw = d.intrinsicWidth.toFloat()
                    val dh = d.intrinsicHeight.toFloat()
                    val vw = binding.ivBannerBg.width.toFloat()
                    val vh = binding.ivBannerBg.height.toFloat()
                    if (dw > 0f && dh > 0f && vw > 0f && vh > 0f) {
                        val scale = maxOf(vw / dw, vh / dh)
                        val scaledW = dw * scale
                        val scaledH = dh * scale
                        val dx = vw - scaledW // right align
                        val dy = (vh - scaledH) / 2f

                        val matrix = android.graphics.Matrix()
                        matrix.setScale(scale, scale)
                        matrix.postTranslate(dx, dy)
                        binding.ivBannerBg.imageMatrix = matrix
                    }
                }
            }

            if (binding.ivBannerBg.width > 0) {
                updateImageMatrix()
            } else {
                binding.ivBannerBg.post { updateImageMatrix() }
            }

            val applyHighlightShader = {
                val text = binding.tvBannerHighlight.text.toString()
                val lines = text.split("\n")
                val maxWidth = lines.maxOfOrNull { line ->
                    binding.tvBannerHighlight.paint.measureText(line)
                } ?: binding.tvBannerHighlight.width.toFloat()

                val shader = LinearGradient(
                    0f, 0f, maxWidth.coerceAtLeast(1f), 0f,
                    intArrayOf(
                        Color.parseColor("#4A8CF2"),
                        Color.parseColor("#9A5CF0")
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                binding.tvBannerHighlight.paint.shader = shader
                binding.tvBannerHighlight.invalidate()
            }

            if (binding.tvBannerHighlight.width > 0) {
                applyHighlightShader()
            } else {
                binding.tvBannerHighlight.post { applyHighlightShader() }
            }

            val clickListener = { onItemClick(item) }
            binding.btnBannerAction.setOnClickListener { clickListener() }
            binding.root.setOnClickListener { clickListener() }
        }
    }
}
