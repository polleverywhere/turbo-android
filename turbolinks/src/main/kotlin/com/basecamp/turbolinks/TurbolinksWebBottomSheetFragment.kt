package com.basecamp.turbolinks

import android.content.DialogInterface
import android.os.Bundle

@Suppress("unused")
abstract class TurbolinksWebBottomSheetFragment : TurbolinksNativeBottomSheetFragment(), TurbolinksWebFragmentCallback {
    private lateinit var delegate: TurbolinksWebFragmentDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        delegate = TurbolinksWebFragmentDelegate(this, this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        delegate.onActivityCreated()
    }

    override fun onStart() {
        super.onStart()
        delegate.onStart()
    }

    override fun onStop() {
        delegate.onDialogStop()
        dismissAllowingStateLoss()
        super.onStop()
    }

    override fun onCancel(dialog: DialogInterface) {
        delegate.onDialogCancel()
        super.onCancel(dialog)
    }

    override fun onBeforeNavigation() {
        // Allow subclasses to do state cleanup
    }

    // ----------------------------------------------------------------------------
    // TurbolinksWebFragmentCallback interface
    // ----------------------------------------------------------------------------

    override fun onUpdateView() {}

    override fun onWebViewAttached() {}

    override fun onWebViewDetached() {}

    override fun onColdBootPageStarted(location: String) {}

    override fun onColdBootPageCompleted(location: String) {}

    override fun onVisitStarted(location: String) {}

    override fun onVisitCompleted(location: String) {}

    override fun onVisitErrorReceived(location: String, errorCode: Int) {}
}