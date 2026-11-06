package com.example.barakah

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.barakah.notify.AlarmScheduler
import com.example.barakah.notify.Notifier
import com.example.barakah.ui.AppViewModel
import com.example.barakah.ui.BarakahApp
import com.example.barakah.ui.theme.BarakahTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { AlarmScheduler.reschedule(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            BarakahTheme {
                BarakahApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Prayer times change every day: re-plan reminders whenever the app opens.
        AlarmScheduler.reschedule(this)
    }
}
