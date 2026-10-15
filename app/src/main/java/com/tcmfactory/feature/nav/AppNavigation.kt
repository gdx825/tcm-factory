package com.tcmfactory.feature.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tcmfactory.core.datastore.LegalRepository
import com.tcmfactory.core.datastore.SettingsRepository
import com.tcmfactory.core.datastore.UserSettings
import com.tcmfactory.feature.common.PlaceholderScreen
import com.tcmfactory.feature.knowledge.KnowledgeScreen
import com.tcmfactory.feature.quiz.QuizHomeScreen
import com.tcmfactory.feature.quiz.QuizSessionScreen
import com.tcmfactory.feature.reader.LibraryScreen
import com.tcmfactory.feature.reader.ReaderScreen
import com.tcmfactory.feature.recite.CardManageScreen
import com.tcmfactory.feature.recite.CardReviewScreen
import com.tcmfactory.feature.recite.ReciteHomeScreen
import com.tcmfactory.feature.legal.LegalDetailScreen
import com.tcmfactory.feature.settings.SettingsScreen
import com.tcmfactory.feature.supporter.RewardScreen
import com.tcmfactory.feature.supporter.SupporterListScreen
import com.tcmfactory.feature.stats.StatsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val legalRepository: LegalRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /**
     * 是否已同意当前版本的免责条款。
     *
     * 初始值给 `null` 而不是 `false`，是为了区分「还在从磁盘读」与「确实没同意过」。
     * 若初始值给 false，已经同意过的老用户每次启动都会先闪一下条款页再跳进主界面 ——
     * 这一帧的闪烁足以让人以为"又要我同意一次"。
     *
     * 用 Eagerly 而非 WhileSubscribed：这个判断发生在界面绘制前，
     * 早一点开始读取就能少等一点。
     */
    val legalAccepted: StateFlow<Boolean?> = legalRepository.isCurrentVersionAccepted
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun acceptLegal() {
        viewModelScope.launch { legalRepository.accept() }
    }
}

object Routes {
    const val RECITE = "recite"
    const val REVIEW = "review"
    const val MANAGE = "manage"
    const val READER = "reader"
    const val READ = "read/{bookId}?offset={offset}"
    const val QUIZ = "quiz"
    const val QUIZ_SESSION = "quizSession/{mode}?subject={subject}&chapter={chapter}"
    const val PROFILE = "profile"
    const val KNOWLEDGE = "knowledge"
    const val STATS = "stats"
    /** 支持开发（打赏入口） */
    const val REWARD = "reward"
    /** 公开支持者名单 */
    const val SUPPORTERS = "supporters"
    /** 完整免责条款（设置页入口；首次启动的确认页直接复用同一界面） */
    const val LEGAL = "legal"

    fun read(bookId: Long, offset: Int? = null) = "read/$bookId?offset=${offset ?: -1}"

    /** 科目名与章节名可能含中文与标点，必须做 URL 编码后才能放进路由。 */
    fun quizSession(mode: String, subject: String?, chapter: String? = null): String {
        val s = subject?.let { android.net.Uri.encode(it) }.orEmpty()
        val c = chapter?.let { android.net.Uri.encode(it) }.orEmpty()
        return "quizSession/$mode?subject=$s&chapter=$c"
    }
}

enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    Recite(Routes.RECITE, "背诵", Icons.Outlined.Style),
    Reader(Routes.READER, "古籍", Icons.Outlined.AutoStories),
    Quiz(Routes.QUIZ, "刷题", Icons.Outlined.EditNote),
    Profile(Routes.PROFILE, "我的", Icons.Outlined.Person)
}

@Composable
fun AppNavigation(settings: UserSettings) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = BottomTab.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    BottomTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.RECITE,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.RECITE) {
                ReciteHomeScreen(
                    onStartReview = { navController.navigate(Routes.REVIEW) },
                    onManageCards = { navController.navigate(Routes.MANAGE) },
                    onOpenStats = { navController.navigate(Routes.STATS) }
                )
            }
            composable(Routes.REVIEW) {
                CardReviewScreen(
                    onExit = { navController.popBackStack() },
                    onOpenSource = { bookId, offset ->
                        navController.navigate(Routes.read(bookId, offset))
                    }
                )
            }
            composable(Routes.MANAGE) {
                CardManageScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.READER) {
                LibraryScreen(
                    onOpenBook = { bookId -> navController.navigate(Routes.read(bookId)) }
                )
            }
            composable(
                route = Routes.READ,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                    navArgument("offset") {
                        type = NavType.IntType
                        defaultValue = -1
                    }
                )
            ) {
                ReaderScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.QUIZ) {
                QuizHomeScreen(
                    onStartSession = { mode, subject, chapter ->
                        navController.navigate(Routes.quizSession(mode.name, subject, chapter))
                    }
                )
            }
            composable(
                route = Routes.QUIZ_SESSION,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("subject") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("chapter") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) {
                QuizSessionScreen(onExit = { navController.popBackStack() })
            }
            composable(Routes.PROFILE) {
                SettingsScreen(
                    onOpenKnowledge = { navController.navigate(Routes.KNOWLEDGE) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenLegal = { navController.navigate(Routes.LEGAL) },
                    onOpenReward = { navController.navigate(Routes.REWARD) },
                    onOpenSupporters = { navController.navigate(Routes.SUPPORTERS) }
                )
            }
            composable(Routes.LEGAL) {
                LegalDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.REWARD) {
                RewardScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSupporterList = { navController.navigate(Routes.SUPPORTERS) }
                )
            }
            composable(Routes.SUPPORTERS) {
                SupporterListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenReward = { navController.navigate(Routes.REWARD) }
                )
            }
            composable(Routes.KNOWLEDGE) {
                KnowledgeScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STATS) {
                StatsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
