package com.william.astuterepo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.william.astuterepo.ui.applist.AppListScreen
import com.william.astuterepo.ui.theme.AstuteRepoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AstuteRepoTheme {
                AppListScreen()
            }
        }
    }
}
