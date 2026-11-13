package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.GatewayPreferencesRepository
import com.example.ui.GatewayScreen
import com.example.ui.theme.LearningGatewayTheme
import com.example.viewmodel.GatewayViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = GatewayPreferencesRepository(applicationContext)

        setContent {
            LearningGatewayTheme {
                val gatewayViewModel: GatewayViewModel = viewModel(
                    factory = GatewayViewModel.Factory(repository, applicationContext)
                )
                GatewayScreen(viewModel = gatewayViewModel)
            }
        }
    }
}
