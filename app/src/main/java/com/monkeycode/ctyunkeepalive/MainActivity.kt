package com.monkeycode.ctyunkeepalive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.monkeycode.ctyunkeepalive.app.MainApplication
import com.monkeycode.ctyunkeepalive.ui.CtyunApp
import com.monkeycode.ctyunkeepalive.ui.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MainApplication).container
        val viewModel = MainViewModel(container)
        setContent {
            CtyunApp(viewModel = viewModel, initialTab = intent.getStringExtra("open_tab"))
        }
    }
}
