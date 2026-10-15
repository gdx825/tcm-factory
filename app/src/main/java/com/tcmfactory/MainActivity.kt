package com.tcmfactory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcmfactory.core.designsystem.TcmFactoryTheme
import com.tcmfactory.core.datastore.UserSettings
import com.tcmfactory.feature.legal.LegalConsentScreen
import com.tcmfactory.feature.nav.AppNavigation
import com.tcmfactory.feature.nav.AppViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val settings by appViewModel.settings.collectAsStateWithLifecycle(
                initialValue = UserSettings()
            )
            val legalAccepted by appViewModel.legalAccepted.collectAsStateWithLifecycle()

            TcmFactoryTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 三态判断：null = 仍在从磁盘读取同意状态。
                    // 这一帧刻意留空而不默认进入主界面 —— 否则未同意的用户会先看到主界面
                    // 再被弹回条款页，观感像"应用崩了一下"。
                    when (legalAccepted) {
                        null -> Unit
                        false -> LegalConsentScreen(onAccept = appViewModel::acceptLegal)
                        true -> AppNavigation(settings = settings)
                    }
                }
            }
        }
    }
}
