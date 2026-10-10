package com.example.phortart_video_990.ui.screen.uninstall

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.phortart_video_990.MainActivity
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.utils.KeyboardUtils
import com.example.phortart_video_990.core.utils.NotificationPermissionManager

class UninstallActivity : AppCompatActivity() {

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        KeyboardUtils.handleTouchToDismissKeyboard(this, ev)
        return super.dispatchTouchEvent(ev)
    }

    private var selectedOption = -1
    private var currentStep = 1
    private var currentImeHeight = 0
    private var currentNavBarHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#FAFAFA"))

        setContentView(R.layout.activity_uninstall)

        hideSystemUI()
        initView()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
    }

    private fun initView() {
        val rootView = findViewById<View>(android.R.id.content)
        val header1 = findViewById<View?>(R.id.headerContainerStep1)
        val header2 = findViewById<View?>(R.id.headerContainerStep2)
        val bottomPanel1 = findViewById<View?>(R.id.llBottomPanelStep1)
        val bottomPanel2 = findViewById<View?>(R.id.llBottomPanelStep2)

        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val resStatusBarHeight = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        val density = resources.displayMetrics.density
        val minTopInset = maxOf(resStatusBarHeight, (32 * density).toInt())
        val initialTop = minTopInset + (8 * density).toInt()

        header1?.setPadding(0, initialTop, 0, 0)
        header2?.setPadding(0, initialTop, 0, 0)
        header1?.bringToFront()
        header2?.bringToFront()

        val nsvStep2 = findViewById<androidx.core.widget.NestedScrollView>(R.id.nsvStep2)
        val etOtherDetails = findViewById<EditText>(R.id.etOtherDetails)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val statusBar = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            val displayCutout = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout()).top
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val topInset = maxOf(statusBar, displayCutout, resStatusBarHeight, (32 * density).toInt())
            val topPadding = topInset + (8 * density).toInt()

            header1?.setPadding(0, topPadding, 0, 0)
            header2?.setPadding(0, topPadding, 0, 0)

            val baseBottomPadding = (28 * density).toInt()

            bottomPanel1?.setPadding(
                bottomPanel1.paddingLeft,
                bottomPanel1.paddingTop,
                bottomPanel1.paddingRight,
                baseBottomPadding + navBar
            )
            bottomPanel2?.setPadding(
                bottomPanel2.paddingLeft,
                bottomPanel2.paddingTop,
                bottomPanel2.paddingRight,
                baseBottomPadding + navBar
            )

            currentImeHeight = imeHeight
            currentNavBarHeight = navBar

            val nsvBottomPadding = if (imeHeight > navBar) imeHeight - navBar else 0
            nsvStep2?.setPadding(
                nsvStep2.paddingLeft,
                nsvStep2.paddingTop,
                nsvStep2.paddingRight,
                nsvBottomPadding
            )
            nsvStep2?.clipToPadding = false

            if (imeHeight > navBar && etOtherDetails.hasFocus()) {
                scrollToCardOption6()
            }

            insets
        }

        // Setup text from strings.xml
        findViewById<TextView>(R.id.tvTitleStep1)?.setText(R.string.uninstall_step1_title)
        findViewById<TextView>(R.id.tvTitleUnable)?.setText(R.string.uninstall_card1_title)
        findViewById<TextView>(R.id.tvDescUnable)?.setText(R.string.uninstall_card1_desc)
        findViewById<TextView>(R.id.btnTryAgain)?.setText(R.string.uninstall_btn_try_again)

        findViewById<TextView>(R.id.tvTitleNotCurrently)?.setText(R.string.uninstall_card2_title)
        findViewById<TextView>(R.id.tvDescNotCurrently)?.setText(R.string.uninstall_card2_desc)
        findViewById<TextView>(R.id.btnExplore)?.setText(R.string.uninstall_btn_explore)

        findViewById<TextView>(R.id.tvTitleStep2)?.setText(R.string.uninstall_step2_title)
        findViewById<TextView>(R.id.tvOption1)?.setText(R.string.uninstall_opt_1)
        findViewById<TextView>(R.id.tvOption2)?.setText(R.string.uninstall_opt_2)
        findViewById<TextView>(R.id.tvOption3)?.setText(R.string.uninstall_opt_3)
        findViewById<TextView>(R.id.tvOption4)?.setText(R.string.uninstall_opt_4)
        findViewById<TextView>(R.id.tvOption5)?.setText(R.string.uninstall_opt_5)
        findViewById<TextView>(R.id.tvOption6)?.setText(R.string.uninstall_opt_6)
        etOtherDetails.setHint(R.string.uninstall_hint_other)

        // Step 1 Click Handlers
        findViewById<View>(R.id.btnBackStep1).setOnClickListener {
            navigateToMain()
        }

        findViewById<View>(R.id.btnTryAgain).setOnClickListener { navigateToMain() }
        findViewById<View>(R.id.btnExplore).setOnClickListener { navigateToMain() }
        findViewById<View>(R.id.btnDontWantToUninstallStep1).setOnClickListener { navigateToMain() }

        findViewById<View>(R.id.btnStillWantToUninstallStep1).setOnClickListener {
            showStep(2)
        }

        // Step 2 Click Handlers
        findViewById<View>(R.id.btnBackStep2).setOnClickListener { showStep(1) }

        findViewById<View>(R.id.cardOption1).setOnClickListener { selectOption(1) }
        findViewById<View>(R.id.cardOption2).setOnClickListener { selectOption(2) }
        findViewById<View>(R.id.cardOption3).setOnClickListener { selectOption(3) }
        findViewById<View>(R.id.cardOption4).setOnClickListener { selectOption(4) }
        findViewById<View>(R.id.cardOption5).setOnClickListener { selectOption(5) }
        findViewById<View>(R.id.cardOption6).setOnClickListener { selectOption(6) }

        etOtherDetails.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateUninstallButtonState()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etOtherDetails.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                etOtherDetails.postDelayed({ scrollToCardOption6() }, 100)
                etOtherDetails.postDelayed({ scrollToCardOption6() }, 250)
            }
        }

        etOtherDetails.setOnClickListener {
            scrollToCardOption6()
        }

        findViewById<View>(R.id.btnDontWantToUninstallStep2).setOnClickListener { navigateToMain() }

        findViewById<View>(R.id.btnStillWantToUninstallStep2).setOnClickListener {
            if (selectedOption != -1) {
                openSystemUninstall()
            }
        }

        updateUninstallButtonState()
    }

    private fun showStep(step: Int) {
        currentStep = step
        val clStep1 = findViewById<ConstraintLayout>(R.id.clStep1)
        val clStep2 = findViewById<ConstraintLayout>(R.id.clStep2)
        if (step == 1) {
            clStep1.visibility = View.VISIBLE
            clStep2.visibility = View.GONE
            findViewById<View?>(R.id.headerContainerStep1)?.bringToFront()
        } else {
            clStep1.visibility = View.GONE
            clStep2.visibility = View.VISIBLE
            findViewById<View?>(R.id.headerContainerStep2)?.bringToFront()
            updateUninstallButtonState()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentStep == 2) {
            showStep(1)
        } else {
            navigateToMain()
        }
    }

    private fun selectOption(optionIndex: Int) {
        selectedOption = optionIndex

        val cards = listOf(
            findViewById<View>(R.id.cardOption1),
            findViewById<View>(R.id.cardOption2),
            findViewById<View>(R.id.cardOption3),
            findViewById<View>(R.id.cardOption4),
            findViewById<View>(R.id.cardOption5),
            findViewById<View>(R.id.cardOption6)
        )

        val radios = listOf(
            findViewById<ImageView>(R.id.ivRadio1),
            findViewById<ImageView>(R.id.ivRadio2),
            findViewById<ImageView>(R.id.ivRadio3),
            findViewById<ImageView>(R.id.ivRadio4),
            findViewById<ImageView>(R.id.ivRadio5),
            findViewById<ImageView>(R.id.ivRadio6)
        )

        val texts = listOf(
            findViewById<TextView>(R.id.tvOption1),
            findViewById<TextView>(R.id.tvOption2),
            findViewById<TextView>(R.id.tvOption3),
            findViewById<TextView>(R.id.tvOption4),
            findViewById<TextView>(R.id.tvOption5),
            findViewById<TextView>(R.id.tvOption6)
        )

        val unselectedBg = R.drawable.bg_uninstall_card_unselected
        val selectedBg = R.drawable.bg_uninstall_card_selected

        val density = resources.displayMetrics.density
        val boldTypeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.manrope_bold)
        val regularTypeface = androidx.core.content.res.ResourcesCompat.getFont(this, R.font.manrope_regular)

        for (i in cards.indices) {
            val isSelected = (i + 1 == optionIndex)
            val card = cards[i]
            card.setBackgroundResource(if (isSelected) selectedBg else unselectedBg)
            radios[i].setImageResource(
                if (isSelected) R.drawable.ic_uninstall_radio_selected else R.drawable.ic_uninstall_radio_unselected
            )
            texts[i]?.typeface = if (isSelected) boldTypeface else regularTypeface
            texts[i]?.setTextColor(android.graphics.Color.parseColor(if (isSelected) "#111827" else "#39403D"))
            card.elevation = 0f
        }

        val etOtherDetails = findViewById<EditText>(R.id.etOtherDetails)
        etOtherDetails.visibility = if (optionIndex == 6) View.VISIBLE else View.GONE
        if (optionIndex == 6) {
            etOtherDetails.requestFocus()
            findViewById<View>(R.id.cardOption6)?.requestLayout()
            scrollToCardOption6()
        } else {
            etOtherDetails.clearFocus()
            KeyboardUtils.hideSoftKeyboard(etOtherDetails)
        }

        updateUninstallButtonState()
    }

    private fun scrollToCardOption6() {
        val nsvStep2 = findViewById<androidx.core.widget.NestedScrollView>(R.id.nsvStep2) ?: return
        val cardOption6 = findViewById<View>(R.id.cardOption6) ?: return

        val performScroll = {
            val locNsv = IntArray(2)
            nsvStep2.getLocationOnScreen(locNsv)
            val nsvTopOnScreen = locNsv[1]
            val screenHeight = resources.displayMetrics.heightPixels
            val density = resources.displayMetrics.density

            val bottomPanel2 = findViewById<View>(R.id.llBottomPanelStep2)
            val bottomPanelTopOnScreen = if (bottomPanel2 != null && bottomPanel2.visibility == View.VISIBLE) {
                val locPanel = IntArray(2)
                bottomPanel2.getLocationOnScreen(locPanel)
                if (locPanel[1] > 0) locPanel[1] else (screenHeight - currentNavBarHeight - (180 * density).toInt())
            } else {
                screenHeight - currentNavBarHeight
            }

            val keyboardTopOnScreen = if (currentImeHeight > currentNavBarHeight) {
                screenHeight - currentImeHeight
            } else {
                screenHeight
            }

            val visibleBottomOnScreen = minOf(bottomPanelTopOnScreen, keyboardTopOnScreen)
            val visibleHeight = visibleBottomOnScreen - nsvTopOnScreen
            if (visibleHeight > 0 && cardOption6.bottom > 0) {
                val targetScrollY = maxOf(0, cardOption6.bottom - visibleHeight + (16 * density).toInt())
                nsvStep2.smoothScrollTo(0, targetScrollY)
            }
        }

        cardOption6.post { performScroll() }
        cardOption6.postDelayed({ performScroll() }, 100)
        cardOption6.postDelayed({ performScroll() }, 250)
    }

    private fun updateUninstallButtonState() {
        val btnStillWantToUninstallStep2 = findViewById<View>(R.id.btnStillWantToUninstallStep2)
        val etOtherDetails = findViewById<EditText>(R.id.etOtherDetails)

        val isOptionSelected = if (selectedOption == 6) {
            etOtherDetails.text.isNotBlank()
        } else {
            selectedOption != -1
        }

        btnStillWantToUninstallStep2.isEnabled = isOptionSelected
        btnStillWantToUninstallStep2.alpha = if (isOptionSelected) 1.0f else 0.4f
    }

    private fun navigateToMain() {
        NotificationPermissionManager.hasShownNotificationDialogThisSession = true
        NotificationPermissionManager.suppressNotificationDialog = true

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun openSystemUninstall() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(deleteIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationPermissionManager.hasShownNotificationDialogThisSession = true
        NotificationPermissionManager.suppressNotificationDialog = true
    }
}
