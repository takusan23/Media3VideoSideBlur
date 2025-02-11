package io.github.takusan23.media3videosideblur

import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.SurfaceView
import android.widget.Toast
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.contentValuesOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
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

            // media3-transformer 用
            val scope = rememberCoroutineScope()
            val transformVideoPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.PickVisualMedia(),
                onResult = { uri ->
                    uri ?: return@rememberLauncherForActivityResult
                    // 動画の一時保存先
                    val tempVideoFile = context.getExternalFilesDir(null)!!.resolve("VideoSideBlur_${System.currentTimeMillis()}.mp4")
                    val inputMediaItem = MediaItem.fromUri(uri)
                    val editedMediaItem = EditedMediaItem.Builder(inputMediaItem).apply {
                        setEffects(
                            Effects(
                                /* audioProcessors = */ emptyList(),
                                /* videoEffects = */ listOf(
                                    // 縦動画に
                                    Presentation.createForAspectRatio(9 / 16f, Presentation.LAYOUT_SCALE_TO_FIT),
                                    // ぼかす
                                    Media3VideoSideBlurEffect()
                                )
                            )
                        )
                    }.build()
                    val transformer = Transformer.Builder(context).apply {
                        setVideoMimeType(MimeTypes.VIDEO_H264)
                        addListener(object : Transformer.Listener {
                            // 完了した
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                super.onCompleted(composition, exportResult)
                                // 端末の動画フォルダに移動させる
                                val contentValues = contentValuesOf(
                                    MediaStore.Video.Media.DISPLAY_NAME to tempVideoFile.name,
                                    MediaStore.Video.Media.RELATIVE_PATH to "${Environment.DIRECTORY_MOVIES}/VideoSideBlur"
                                )
                                val copyToUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)!!
                                context.contentResolver.openOutputStream(copyToUri)?.use { outputStream ->
                                    tempVideoFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                tempVideoFile.delete()
                                Toast.makeText(context, "おわり", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }.build()
                    transformer.start(editedMediaItem, tempVideoFile.path)
                }
            )
            Button(onClick = { transformVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                Text(text = "ぼかした動画を動画ファイルにする")
            }
        }
    }
}