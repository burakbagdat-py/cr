package com.kriptoradar.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kriptoradar.BuildConfig
import com.kriptoradar.alerts.Notifier
import com.kriptoradar.data.Http
import com.kriptoradar.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Request
import java.io.File
import java.io.IOException

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String = "",
    val apkUrl: String = UpdateManager.APK_URL,
    val publishedAt: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Uygulama içi güncelleme. GitHub Actions her derlemede APK'yı ve version.json dosyasını
 * sabit bir "apk" sürümüne yükler; uygulama bunu kontrol eder, indirir ve Android kurulum ekranını açar.
 */
object UpdateManager {
    private const val BASE = "https://github.com/burakbagdat-py/cr/releases/download/apk"
    const val APK_URL = "$BASE/KriptoRadar.apk"
    private const val INFO_URL = "$BASE/version.json"
    private const val CHECK_EVERY_MS = 3 * 3_600_000L

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val currentVersionName: String get() = BuildConfig.VERSION_NAME
    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    /** Arka plan için: son kontrolden beri yeterli süre geçtiyse kontrol et, yeni sürümde bildirim gönder. */
    suspend fun checkInBackground(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - Prefs.lastNotified("update_check") < CHECK_EVERY_MS) return
        Prefs.markNotified("update_check", now)
        val info = check() ?: return
        // Aynı sürüm için tek bildirim
        if (Prefs.lastNotified("update_v${info.versionCode}") == 0L) {
            Prefs.markNotified("update_v${info.versionCode}", now)
            Notifier.post(
                ctx, Notifier.CH_UPDATE, 3001,
                "⬆️ Kripto Radar ${info.versionName} hazır",
                (info.notes.ifBlank { "Yeni sürüm yayınlandı." }) + "\nGüncellemek için dokunun.",
                null,
            )
        }
    }

    /** Yeni sürüm varsa bilgisini döndürür. */
    suspend fun check(): UpdateInfo? {
        if (_state.value is UpdateState.Downloading) return null
        _state.value = UpdateState.Checking
        return try {
            val info = Http.json.decodeFromString(UpdateInfo.serializer(), Http.get("$INFO_URL?t=${System.currentTimeMillis()}"))
            if (info.versionCode > currentVersionCode) {
                _state.value = UpdateState.Available(info); info
            } else {
                _state.value = UpdateState.UpToDate; null
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Güncelleme bilgisi alınamadı (${e.message})")
            null
        }
    }

    suspend fun download(ctx: Context, info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, 0f)
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "KriptoRadar-${info.versionCode}.apk")
                Http.client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                    val body = r.body ?: throw IOException("Boş yanıt")
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(32 * 1024)
                            var read = 0L
                            var lastEmit = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                read += n
                                if (total > 0 && read - lastEmit > 64 * 1024) {
                                    lastEmit = read
                                    _state.value = UpdateState.Downloading(info, read.toFloat() / total)
                                }
                            }
                        }
                    }
                }
                verify(ctx, out, info)
                out
            }
            _state.value = UpdateState.ReadyToInstall(info, file)
            install(ctx)
        } catch (e: Exception) {
            _state.value = UpdateState.Error("İndirme başarısız: ${e.message}", info)
        }
    }

    /** İndirilen dosyanın gerçekten bu uygulamanın daha yeni bir sürümü olduğunu doğrula. */
    private fun verify(ctx: Context, file: File, info: UpdateInfo) {
        @Suppress("DEPRECATION")
        val pi = ctx.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw IOException("Geçersiz APK dosyası")
        if (pi.packageName != ctx.packageName) throw IOException("Paket adı uyuşmuyor")
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
        if (code <= currentVersionCode) throw IOException("İndirilen sürüm daha yeni değil")
        if (code != info.versionCode) throw IOException("Sürüm bilgisi uyuşmuyor")
    }

    /** Bilinmeyen kaynak izni gerekiyorsa false döner (önce izin ekranı açılmalı). */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(ctx: Context) {
        val st = _state.value as? UpdateState.ReadyToInstall ?: return
        if (!canInstall(ctx)) { openInstallPermission(ctx); return }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", st.file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
