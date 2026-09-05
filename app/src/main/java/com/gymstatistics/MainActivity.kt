package com.gymstatistics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.gymstatistics.ui.GymApp

class MainActivity : ComponentActivity() {

    private val viewModel: GymViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val seedDemo = intent.getBooleanExtra("extra_seed_demo", false)
        val autoSync = intent.getBooleanExtra("extra_sync", false)
        viewModel.onLaunch(seedDemo, autoSync)

        setContent {
            GymApp(viewModel)
        }
    }
}
