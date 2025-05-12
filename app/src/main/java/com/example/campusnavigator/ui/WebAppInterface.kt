package com.example.campusnavigator.ui

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val context: Context, private val fragment: RouteFragment) {

    @JavascriptInterface
    fun displayFloor(floor: Int) {
        fragment.loadFloorSvg(floor)
    }

    @JavascriptInterface
    fun clearRoute() {
        fragment.clearRoute()
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}