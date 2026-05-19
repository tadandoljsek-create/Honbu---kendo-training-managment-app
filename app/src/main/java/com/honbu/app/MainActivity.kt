package com.honbu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.honbu.app.ui.navigation.HonbuNavGraph
import com.honbu.app.ui.theme.HonbuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HonbuTheme {
                HonbuNavGraph()
            }
        }
    }
}
