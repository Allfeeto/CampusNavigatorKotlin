package com.example.campusnavigator

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.campusnavigator.ui.RouteFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RouteFragment())
                .commit()
        }
    }
}