package com.example.phortart_video_990.core.base

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.example.phortart_video_990.core.utils.KeyboardUtils
import com.example.phortart_video_990.core.utils.LocaleHelper

abstract class BaseActivity<VB : ViewBinding>(
    private val bindingInflater: (LayoutInflater) -> VB
) : AppCompatActivity() {

    lateinit var binding: VB

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = if (LocaleHelper.isRtl(this)) {
            android.view.View.LAYOUT_DIRECTION_RTL
        } else {
            android.view.View.LAYOUT_DIRECTION_LTR
        }
        binding = bindingInflater(layoutInflater)
        setContentView(binding.root)
        initView()
        initData()
        initListener()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        KeyboardUtils.handleTouchToDismissKeyboard(this, ev)
        return super.dispatchTouchEvent(ev)
    }

    open fun initView() {}
    open fun initData() {}
    open fun initListener() {}
}
