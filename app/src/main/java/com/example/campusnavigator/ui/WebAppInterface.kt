package com.example.campusnavigator.ui

import android.content.Context
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(private val context: Context, private val fragment: RouteFragment) {

    @JavascriptInterface
    fun displayFloor(floorNumber: Int) {
        fragment.loadFloorSvg(floorNumber)
    }

    @JavascriptInterface
    fun clearRoute() {
        fragment.clearRoute()
    }
}