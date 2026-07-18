/*
 * Copyright 2025 LanXin Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lanxin.android.builtin.pet.presentation

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * 全屏陪伴画中画相机预览（显式「正在看」，非后台偷拍）。
 *
 * - [active]=false 时不绑定相机
 * - 最新帧仅内存缓存，供提问时抓 1 帧
 */
@Composable
fun CompanionCameraPip(
    active: Boolean,
    onPreviewReady: (Boolean) -> Unit,
    onFrameHolder: (CompanionFrameHolder?) -> Unit,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val frameHolder = remember { CompanionFrameHolder() }
    var bindError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(active) {
        if (!active) {
            onPreviewReady(false)
            onFrameHolder(null)
            frameHolder.clear()
        } else {
            onFrameHolder(frameHolder)
        }
        onDispose {
            onPreviewReady(false)
            onFrameHolder(null)
            frameHolder.clear()
            // unbind 在 AndroidView factory 的 cameraProvider 侧处理
        }
    }

    if (!active) return

    Box(
        modifier = modifier
            .width(128.dp)
            .height(170.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.5.dp, Color(0xFFE85D8E).copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .background(Color(0xFF1A0A12).copy(alpha = 0.55f))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val mainExecutor = ContextCompat.getMainExecutor(ctx)
                val analysisExecutor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProvider.unbindAll()
                        val preview = Preview.Builder()
                            .setTargetResolution(Size(480, 640))
                            .build()
                            .also { it.surfaceProvider = previewView.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(480, 640))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(analysisExecutor) { image ->
                            frameHolder.offer(image)
                        }
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        previewView.tag = cameraProvider
                        bindError = null
                        onPreviewReady(true)
                    } catch (e: Exception) {
                        bindError = e.message ?: "camera_bind_failed"
                        onPreviewReady(false)
                    }
                }, mainExecutor)
                previewView
            },
            update = { /* lifecycle-driven */ },
            onRelease = { view ->
                (view.tag as? ProcessCameraProvider)?.unbindAll()
                onPreviewReady(false)
            }
        )
        Text(
            text = "正在看",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .background(Color(0xFFE85D8E).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )
        if (bindError != null) {
            Text(
                text = "相机打不开",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * 线程安全的最新帧缓存（仅内存）。
 */
class CompanionFrameHolder {
    private val latest = AtomicReference<Bitmap?>(null)

    fun offer(image: ImageProxy) {
        try {
            val bmp = imageProxyToBitmap(image) ?: return
            val old = latest.getAndSet(bmp)
            if (old != null && old !== bmp && !old.isRecycled) {
                old.recycle()
            }
        } finally {
            image.close()
        }
    }

    /** 复制一份当前帧（调用方负责 recycle）。 */
    fun snapshotCopy(): Bitmap? {
        val src = latest.get() ?: return null
        return try {
            src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        val old = latest.getAndSet(null)
        if (old != null && !old.isRecycled) old.recycle()
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
            val bytes = out.toByteArray()
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null
            val rotation = image.imageInfo.rotationDegrees
            if (rotation == 0) {
                decoded
            } else {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    decoded, 0, 0, decoded.width, decoded.height, m, true
                )
                if (rotated !== decoded && !decoded.isRecycled) decoded.recycle()
                rotated
            }
        } catch (_: Exception) {
            null
        }
    }
}


