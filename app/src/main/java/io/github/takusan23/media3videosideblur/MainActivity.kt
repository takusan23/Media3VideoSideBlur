package io.github.takusan23.media3videosideblur

import android.os.Bundle
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import io.github.takusan23.media3videosideblur.ui.theme.Media3VideoSideBlurTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Media3VideoSideBlurTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MainScreen() {
    val context = LocalContext.current

    // ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setVideoEffects(
                listOf(
                    // 縦動画に
                    Presentation.createForAspectRatio(9 / 16f, Presentation.LAYOUT_SCALE_TO_FIT),
                    // ぼかす
                    Media3VideoSideBlurEffect()
                )
            )
        }
    }

    // PhotoPicker
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            // 動画を選んだらセットして再生
            exoPlayer.setMediaItem(MediaItem.fromUri(uri ?: return@rememberLauncherForActivityResult))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Button(onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                Text(text = "動画を選ぶ")
            }

            // アスペクト比を縦動画に
            AndroidView(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .aspectRatio(9 / 16f),
                factory = { SurfaceView(it) },
                update = { surfaceView ->
                    // 出力先にする
                    exoPlayer.setVideoSurfaceView(surfaceView)
                }
            )
        }
    }
}