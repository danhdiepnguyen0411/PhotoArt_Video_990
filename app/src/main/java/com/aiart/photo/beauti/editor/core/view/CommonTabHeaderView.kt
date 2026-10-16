package com.aiart.photo.beauti.editor.core.view

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePaddingRelative
import androidx.core.widget.TextViewCompat
import android.util.TypedValue
import androidx.navigation.findNavController
import com.aiart.photo.beauti.editor.R

typealias CommonHeaderView = CommonTabHeaderView

class CommonTabHeaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    val btnBack: ImageView
    val llLeftGroup: LinearLayout
    val llBrand: LinearLayout
    val ivBrandLogo: ImageView
    val tvBrandPhoto: TextView
    val tvBrandAi: TextView

    val llTitleGroup: LinearLayout
    val tvTitle: TextView
    val ivSparkle: ImageView

    val flRightAction: FrameLayout
    val btnPro: View

    private var onBackClickListener: (() -> Unit)? = null
    private var onProClickListener: (() -> Unit)? = null
    private var userSpecifiedCenterTitle: Boolean? = null

    val isTitleCentered: Boolean
        get() = userSpecifiedCenterTitle ?: (btnBack.visibility == View.VISIBLE)

    init {
        val density = resources.displayMetrics.density
        setPaddingRelative(
            (16 * density).toInt(),
            (48 * density).toInt(),
            (16 * density).toInt(),
            (12 * density).toInt()
        )

        LayoutInflater.from(context).inflate(R.layout.layout_common_tab_header, this, true)

        btnBack = findViewById(R.id.btnBack)
        llLeftGroup = findViewById(R.id.llHeaderLeftGroup)
        llBrand = findViewById(R.id.llHeaderBrand)
        ivBrandLogo = findViewById(R.id.ivHeaderBrandLogo)
        tvBrandPhoto = findViewById(R.id.tvHeaderBrandPhoto)
        tvBrandAi = findViewById(R.id.tvHeaderBrandAi)

        llTitleGroup = findViewById(R.id.llHeaderTitleGroup)
        tvTitle = findViewById(R.id.tvHeaderTitle)
        ivSparkle = findViewById(R.id.ivHeaderSparkle)
        flRightAction = findViewById(R.id.flHeaderRightAction)
        btnPro = findViewById(R.id.btnHeaderPro)

        btnBack.setOnClickListener {
            if (onBackClickListener != null) {
                onBackClickListener?.invoke()
            } else {
                handleDefaultBackAction()
            }
        }

        btnPro.setOnClickListener {
            onProClickListener?.invoke()
        }

        tvTitle.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if ((right - left) != (oldRight - oldLeft) && (right - left) > 0) {
                applyTitleGradient()
            }
        }

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CommonTabHeaderView)
            try {
                val showBack = typedArray.getBoolean(R.styleable.CommonTabHeaderView_showBackButton, false)
                setBackButtonVisible(showBack)

                val showBrand = typedArray.getBoolean(R.styleable.CommonTabHeaderView_showBrandLogo, false)
                setBrandLogoVisible(showBrand)

                val title = typedArray.getString(R.styleable.CommonTabHeaderView_headerTitle)
                if (!title.isNullOrEmpty()) {
                    setTitle(title)
                }

                val showSparkle = typedArray.getBoolean(R.styleable.CommonTabHeaderView_showSparkle, false)
                setSparkleVisible(showSparkle)

                val showPro = typedArray.getBoolean(R.styleable.CommonTabHeaderView_showProButton, false)
                setProButtonVisible(showPro)

                if (typedArray.hasValue(R.styleable.CommonTabHeaderView_centerTitle)) {
                    userSpecifiedCenterTitle = typedArray.getBoolean(R.styleable.CommonTabHeaderView_centerTitle, false)
                }
                updateTitleConstraints()
            } finally {
                typedArray.recycle()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            if (statusBarInset > 0) {
                val baseDp48 = (48 * resources.displayMetrics.density).toInt()
                val targetPaddingTop = maxOf(baseDp48, statusBarInset + (8 * resources.displayMetrics.density).toInt())
                if (paddingTop != targetPaddingTop) {
                    updatePaddingRelative(top = targetPaddingTop)
                }
            }
            insets
        }
    }

    val hasRightAction: Boolean
        get() {
            if (btnPro.visibility == View.VISIBLE) return true
            if (flRightAction.visibility == View.VISIBLE) {
                for (i in 0 until flRightAction.childCount) {
                    val child = flRightAction.getChildAt(i)
                    if (child !== btnPro && child.visibility == View.VISIBLE) {
                        return true
                    }
                }
            }
            return false
        }

    fun setTitleCentered(centered: Boolean) {
        userSpecifiedCenterTitle = centered
        updateTitleConstraints()
    }

    fun enableTitleMarquee(enable: Boolean = true) {
        tvTitle.isSingleLine = true
        tvTitle.ellipsize = if (enable) android.text.TextUtils.TruncateAt.MARQUEE else android.text.TextUtils.TruncateAt.END
        tvTitle.marqueeRepeatLimit = -1
        tvTitle.isFocusable = enable
        tvTitle.isFocusableInTouchMode = enable
        tvTitle.isSelected = enable
    }

    fun updateTitleConstraints() {
        val lp = llTitleGroup.layoutParams as? LayoutParams ?: return
        val density = resources.displayMetrics.density
        val hasBack = btnBack.visibility == View.VISIBLE
        val hasRight = hasRightAction

        if (isTitleCentered) {
            lp.startToStart = LayoutParams.PARENT_ID
            lp.endToEnd = LayoutParams.PARENT_ID
            lp.startToEnd = LayoutParams.UNSET
            lp.endToStart = LayoutParams.UNSET
            lp.horizontalBias = 0.5f

            // Left clearance for back button (btnBack is 38dp wide)
            val leftClearance = if (hasBack) (52 * density).toInt() else (16 * density).toInt()

            // Right clearance for right actions (btnPro or custom right view)
            val hasCustomRightChild = flRightAction.visibility == View.VISIBLE &&
                (0 until flRightAction.childCount).any { i ->
                    val child = flRightAction.getChildAt(i)
                    child !== btnPro && child.visibility == View.VISIBLE
                }
            val rightClearance = when {
                hasCustomRightChild -> (96 * density).toInt()
                btnPro.visibility == View.VISIBLE -> (68 * density).toInt()
                else -> (16 * density).toInt()
            }

            // Margin MUST be symmetric on both sides to guarantee true horizontal centering across the screen
            val symmetricMargin = maxOf(leftClearance, rightClearance)
            lp.marginStart = symmetricMargin
            lp.marginEnd = symmetricMargin
        } else {
            if (hasBack) {
                lp.startToStart = LayoutParams.UNSET
                lp.startToEnd = R.id.btnBack
                lp.endToEnd = if (hasRight) LayoutParams.UNSET else LayoutParams.PARENT_ID
                lp.endToStart = if (hasRight) R.id.flHeaderRightAction else LayoutParams.UNSET
                lp.horizontalBias = 0.0f
                lp.marginStart = (12 * density).toInt()
                lp.marginEnd = (12 * density).toInt()
            } else {
                lp.startToStart = LayoutParams.PARENT_ID
                lp.startToEnd = LayoutParams.UNSET
                lp.endToEnd = if (hasRight) LayoutParams.UNSET else LayoutParams.PARENT_ID
                lp.endToStart = if (hasRight) R.id.flHeaderRightAction else LayoutParams.UNSET
                lp.horizontalBias = 0.0f
                lp.marginStart = 0
                lp.marginEnd = (12 * density).toInt()
            }
        }
        llTitleGroup.layoutParams = lp
    }

    private fun handleDefaultBackAction() {
        val navController = runCatching { findNavController() }.getOrNull()
        if (navController != null && navController.currentDestination != null) {
            if (!navController.navigateUp()) {
                triggerActivityBack()
            }
        } else {
            triggerActivityBack()
        }
    }

    private fun triggerActivityBack() {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is ComponentActivity) {
                ctx.onBackPressedDispatcher.onBackPressed()
                return
            }
            ctx = ctx.baseContext
        }
    }

    fun setBackButtonVisible(visible: Boolean) {
        btnBack.visibility = if (visible) View.VISIBLE else View.GONE
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
            tvTitle, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE
        )
        if (visible) {
            tvTitle.textSize = 19f
        } else {
            tvTitle.textSize = 32f
        }
        updateTitleConstraints()
        applyTitleGradient()
    }

    fun setOnBackClickListener(listener: () -> Unit) {
        this.onBackClickListener = listener
    }

    fun setBrandLogoVisible(visible: Boolean) {
        if (visible) {
            llBrand.visibility = View.VISIBLE
            llTitleGroup.visibility = View.GONE
            applyBrandAiGradient()
        } else {
            llBrand.visibility = View.GONE
            llTitleGroup.visibility = View.VISIBLE
        }
    }

    fun setTitle(title: CharSequence?) {
        tvTitle.text = title
        tvTitle.isSelected = true
        applyTitleGradient()
    }

    fun setTitleRes(@StringRes resId: Int) {
        tvTitle.setText(resId)
        tvTitle.isSelected = true
        applyTitleGradient()
    }

    fun setSparkleVisible(visible: Boolean) {
        ivSparkle.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setProButtonVisible(visible: Boolean) {
        btnPro.visibility = if (visible) View.VISIBLE else View.GONE
        val hasCustomRightChild = (0 until flRightAction.childCount).any { i ->
            val child = flRightAction.getChildAt(i)
            child !== btnPro && child.visibility == View.VISIBLE
        }
        flRightAction.visibility = if (visible || hasCustomRightChild) View.VISIBLE else View.GONE
        updateTitleConstraints()
    }

    fun setRightCustomView(view: View) {
        btnPro.visibility = View.GONE
        flRightAction.removeAllViews()
        flRightAction.addView(view)
        flRightAction.visibility = View.VISIBLE
        updateTitleConstraints()
        post {
            updateTitleConstraints()
            applyTitleGradient()
        }
    }

    fun setOnProClickListener(listener: () -> Unit) {
        this.onProClickListener = listener
    }

    fun applyTitleGradient() {
        tvTitle.post {
            val paint = tvTitle.paint
            val text = tvTitle.text?.toString().orEmpty()
            val width = paint.measureText(text)
            if (width > 0) {
                val textShader = LinearGradient(
                    0f, 0f, width, 0f,
                    intArrayOf(Color.parseColor("#4A7BFE"), Color.parseColor("#9153EE")),
                    null,
                    Shader.TileMode.CLAMP
                )
                tvTitle.paint.shader = textShader
                tvTitle.isSelected = true
                tvTitle.invalidate()
            }
        }
    }

    fun applyBrandAiGradient() {
        tvBrandAi.post {
            val width = tvBrandAi.paint.measureText(tvBrandAi.text?.toString().orEmpty()).coerceAtLeast(1f)
            val shader = LinearGradient(
                0f, 0f, width, 0f,
                intArrayOf(
                    Color.parseColor("#3D86F4"),
                    Color.parseColor("#8A5CF0")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            tvBrandAi.paint.shader = shader
            tvBrandAi.invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        enableTitleMarquee(true)
        updateTitleConstraints()
        applyTitleGradient()
        tvTitle.isSelected = true
        if (llBrand.visibility == View.VISIBLE) {
            applyBrandAiGradient()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            tvTitle.isSelected = true
        }
    }
}
