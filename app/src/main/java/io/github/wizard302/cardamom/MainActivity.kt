package io.github.wizard302.cardamom

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.wizard302.cardamom.ui.CardamomApp
import io.github.wizard302.cardamom.ui.theme.CardamomTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CardamomTheme {
                CardamomApp()
            }
        }
    }
}
