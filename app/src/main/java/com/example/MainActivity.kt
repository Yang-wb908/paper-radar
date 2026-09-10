package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Notifications
import com.example.data.AppGraph
import com.example.data.PaperRadarWork
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.PaperRadarTheme

class MainActivity : ComponentActivity() {

    /** 알림을 눌러 들어왔을 때 열 탭. null이면 기본(피드). */
    private val startRoute = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 저장소를 미리 만들어 디스크 복원 + 첫 동기화를 시작시키고,
        // 1시간 주기 백그라운드 동기화를 등록한다.
        AppGraph.repository(applicationContext)
        PaperRadarWork.schedule(applicationContext)
        requestNotificationPermissionIfNeeded()
        startRoute.value = routeFrom(intent)

        setContent {
            val repository = remember { AppGraph.repository(applicationContext) }
            val themeMode by repository.themeMode().collectAsStateWithLifecycle(initialValue = "system")
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            PaperRadarTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost(initialRoute = startRoute.value)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startRoute.value = routeFrom(intent)
    }

    private fun routeFrom(intent: Intent?): String? =
        if (intent?.getStringExtra(Notifications.EXTRA_TARGET_TAB) == "notifications") "notifications" else null

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
