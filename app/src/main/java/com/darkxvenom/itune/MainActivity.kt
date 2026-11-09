package com.darkxvenom.itune

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.darkxvenom.itune.backend.MusicPlayerState
import com.darkxvenom.itune.data.repository.AvatarManager
import com.darkxvenom.itune.ui.home.HomeScreen
import com.darkxvenom.itune.ui.theme.AppleOledBlack
import com.darkxvenom.itune.ui.theme.ITuneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AvatarManager.init(this)
        MusicPlayerState.init(this)
        enableEdgeToEdge()
        setContent {
            ITuneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppleOledBlack
                ) {
                    HomeScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MusicPlayerState.release()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun HomeScreenPreview() {
    ITuneTheme {
        HomeScreen()
    }
}
