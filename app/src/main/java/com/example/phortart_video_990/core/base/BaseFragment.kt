package com.example.phortart_video_990.core.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseFragment<VB : ViewBinding>(
    private val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : Fragment() {

    private var _binding: VB? = null
    val binding get() = _binding!!
    val bindingOrNull get() = _binding

    private var appLoadingDialog: com.example.phortart_video_990.core.dialog.AppLoadingDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = bindingInflater(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initData()
        initView()
        initListener()
    }

    open fun initData() {}
    open fun initView() {}
    open fun initListener() {}

    /**
     * Hiển thị loading dialog chuẩn chung cho toàn bộ app
     */
    fun showLoading(
        title: String = "Đang xử lý...",
        subtitle: String? = "Vui lòng chờ trong giây lát",
        cancelable: Boolean = false
    ) {
        val ctx = context ?: return
        if (appLoadingDialog == null) {
            appLoadingDialog = com.example.phortart_video_990.core.dialog.AppLoadingDialog.show(
                context = ctx,
                title = title,
                subtitle = subtitle,
                cancelable = cancelable
            )
        } else {
            appLoadingDialog?.updateMessage(title, subtitle)
            if (appLoadingDialog?.isShowing != true) {
                try {
                    appLoadingDialog?.show()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Ẩn loading dialog chung
     */
    fun hideLoading() {
        try {
            appLoadingDialog?.dismiss()
        } catch (_: Exception) {}
        appLoadingDialog = null
    }

    override fun onDestroyView() {
        hideLoading()
        super.onDestroyView()
        _binding = null
    }
}
