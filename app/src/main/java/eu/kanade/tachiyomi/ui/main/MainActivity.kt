package eu.kanade.tachiyomi.ui.main

import android.animation.ValueAnimator
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.category.model.Category
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.domain.library.service.LibraryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.DownloadedOnlyBannerBackgroundColor
import eu.kanade.presentation.components.IncognitoModeBannerBackgroundColor
import eu.kanade.presentation.components.IndexingBannerBackgroundColor
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.Migrations
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.EpisodeCache
import eu.kanade.tachiyomi.data.connections.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.data.updater.RELEASE_URL
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibrarySettingsSheet
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.player.ExternalIntents
import eu.kanade.tachiyomi.util.Constants
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isNavigationBarNeedsScrim
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.graphics.Color.Companion as ComposeColor

class MainActivity : BaseActivity() {

    private val sourcePreferences: SourcePreferences by injectLazy()
    private val libraryPreferences: LibraryPreferences by injectLazy()
    private val uiPreferences: UiPreferences by injectLazy()
    private val preferences: BasePreferences by injectLazy()

    private val episodeCache: EpisodeCache by injectLazy()

    private val animeDownloadCache: AnimeDownloadCache by injectLazy()

    private val externalIntents: ExternalIntents by injectLazy()

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    /**
     * Sheet containing filter/sort/display items.
     */
    private var animeSettingsSheet: AnimeLibrarySettingsSheet? = null

    private var isHandlingShortcut: Boolean = false
    private lateinit var navigator: Navigator

    // AM (CN) -->
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()
    // <-- AM (CN)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (savedInstanceState == null) installSplashScreen() else null

        super.onCreate(savedInstanceState)

        val didMigration = if (savedInstanceState == null) {
            Migrations.upgrade(
                context = applicationContext,
                basePreferences = preferences,
                uiPreferences = uiPreferences,
                preferenceStore = Injekt.get(),
                networkPreferences = Injekt.get(),
                sourcePreferences = sourcePreferences,
                securityPreferences = Injekt.get(),
                libraryPreferences = libraryPreferences,
                playerPreferences = Injekt.get(),
                backupPreferences = Injekt.get(),
                // AM (CN) -->
                connectionsPreferences = connectionsPreferences,
                connectionsManager = Injekt.get(),
                // <-- AM (CN)
            )
        } else {
            false
        }

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        // AM (DC) -->
        DiscordRPCService.resources = resources
        // <-- AM (DC)

        // Draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        animeSettingsSheet = AnimeLibrarySettingsSheet(this)
        AnimeLibraryTab.openSettingsSheetEvent
            .onEach(::showAnimeSettingsSheet)
            .launchIn(lifecycleScope)

        setComposeContent {
            val incognito by preferences.incognitoMode().collectAsState()
            val downloadOnly by preferences.downloadedOnly().collectAsState()
            val indexingAnime by animeDownloadCache.isRenewing.collectAsState()

            // Set statusbar color considering the top app state banner
            val systemUiController = rememberSystemUiController()
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val statusBarBackgroundColor = when {
                indexingAnime -> IndexingBannerBackgroundColor
                downloadOnly -> DownloadedOnlyBannerBackgroundColor
                incognito -> IncognitoModeBannerBackgroundColor
                else -> MaterialTheme.colorScheme.surface
            }
            LaunchedEffect(systemUiController, statusBarBackgroundColor) {
                systemUiController.setStatusBarColor(
                    color = ComposeColor.Transparent,
                    darkIcons = statusBarBackgroundColor.luminance() > 0.5,
                    transformColorForLightContent = { ComposeColor.Black },
                )
            }

            // Set navigation bar color
            val context = LocalContext.current
            val navbarScrimColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            LaunchedEffect(systemUiController, isSystemInDarkTheme, navbarScrimColor) {
                systemUiController.setNavigationBarColor(
                    color = if (context.isNavigationBarNeedsScrim()) {
                        navbarScrimColor.copy(alpha = 0.7f)
                    } else {
                        ComposeColor.Transparent
                    },
                    darkIcons = !isSystemInDarkTheme,
                    navigationBarContrastEnforced = false,
                    transformColorForLightContent = { ComposeColor.Black },
                )
            }

            Navigator(
                screen = HomeScreen,
                disposeBehavior = NavigatorDisposeBehavior(disposeNestedNavigators = false, disposeSteps = true),
            ) { navigator ->
                if (navigator.size == 1) {
                    ConfirmExit()
                }

                LaunchedEffect(navigator) {
                    this@MainActivity.navigator = navigator

                    if (savedInstanceState == null) {
                        // Set start screen
                        handleIntentAction(intent)

                        // Reset Incognito Mode on relaunch
                        preferences.incognitoMode().set(false)
                    }
                }

                val scaffoldInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                Scaffold(
                    topBar = {
                        AppStateBanners(
                            downloadedOnlyMode = downloadOnly,
                            incognitoMode = incognito,
                            indexing = indexingAnime,
                            modifier = Modifier.windowInsetsPadding(scaffoldInsets),
                        )
                    },
                    contentWindowInsets = scaffoldInsets,
                ) { contentPadding ->
                    // Consume insets already used by app state banners
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .consumeWindowInsets(contentPadding),
                    ) {
                        // Shows current screen
                        DefaultNavigatorScreenTransition(navigator = navigator)
                    }
                }

                // Pop source-related screens when incognito mode is turned off
                LaunchedEffect(Unit) {
                    preferences.incognitoMode().changes()
                        .drop(1)
                        .onEach {
                            if (!it) {
                                val currentScreen = navigator.lastItem
                                if (currentScreen is BrowseAnimeSourceScreen ||
                                    (currentScreen is AnimeScreen && currentScreen.fromSource)
                                ) {
                                    navigator.popUntilRoot()
                                }
                            }
                        }
                        .launchIn(this)

                    // AM (DC) -->
                    connectionsPreferences.enableDiscordRPC().changes()
                        .drop(1)
                        .onEach {
                            if (it) {
                                startService(Intent(this@MainActivity, DiscordRPCService::class.java))
                            } else {
                                stopService(Intent(this@MainActivity, DiscordRPCService::class.java))
                            }
                        }.launchIn(this)

                    connectionsPreferences.discordRPCStatus().changes()
                        .drop(1)
                        .onEach {
                            stopService(Intent(this@MainActivity, DiscordRPCService::class.java))
                            startService(Intent(this@MainActivity, DiscordRPCService::class.java))
                        }.launchIn(this)
                    // <-- AM (DC)
                }

                CheckForUpdate()
            }

            var showChangelog by remember { mutableStateOf(didMigration && !BuildConfig.DEBUG) }
            if (showChangelog) {
                AlertDialog(
                    onDismissRequest = { showChangelog = false },
                    title = { Text(text = stringResource(R.string.updated_version, BuildConfig.VERSION_NAME)) },
                    dismissButton = {
                        TextButton(onClick = { openInBrowser(RELEASE_URL) }) {
                            Text(text = stringResource(R.string.whats_new))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showChangelog = false }) {
                            Text(text = stringResource(android.R.string.ok))
                        }
                    },
                )
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepVisibleCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || !ready && elapsed <= SPLASH_MAX_DURATION
        }
        setSplashScreenExitAnimation(splashScreen)

        // AM (DC) -->
        if (DiscordRPCService.rpc == null && connectionsPreferences.enableDiscordRPC().get()) {
            startService(Intent(this@MainActivity, DiscordRPCService::class.java))
        }
        // <-- AM (DC)
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (val screen = navigator.lastItem) {
            is AssistContentScreen -> {
                screen.onProvideAssistUrl()?.let { outContent.webUri = it.toUri() }
            }
        }
    }

    private fun showAnimeSettingsSheet(category: Category? = null) {
        if (category != null) {
            animeSettingsSheet?.show(category)
        } else {
            lifecycleScope.launch {
                AnimeLibraryTab.requestOpenSettingsSheet()
            }
        }
    }

    @Composable
    private fun ConfirmExit() {
        val scope = rememberCoroutineScope()
        val confirmExit by preferences.confirmExit().collectAsState()
        var waitingConfirmation by remember { mutableStateOf(false) }
        BackHandler(enabled = !waitingConfirmation && confirmExit) {
            scope.launch {
                waitingConfirmation = true
                val toast = toast(R.string.confirm_exit, Toast.LENGTH_LONG)
                delay(2.seconds)
                toast.cancel()
                waitingConfirmation = false
            }
        }
    }

    @Composable
    private fun CheckForUpdate() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        LaunchedEffect(Unit) {
            // App updates
            if (BuildConfig.INCLUDE_UPDATER) {
                try {
                    val result = AppUpdateChecker().checkForUpdate(context)
                    if (result is AppUpdateResult.NewUpdate) {
                        val updateScreen = NewUpdateScreen(
                            versionName = result.release.version,
                            changelogInfo = result.release.info,
                            releaseLink = result.release.releaseLink,
                            downloadLink = result.release.getDownloadLink(),
                        )
                        navigator.push(updateScreen)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }
    }

    /**
     * Sets custom splash screen exit animation on devices prior to Android 12.
     *
     * When custom animation is used, status and navigation bar color will be set to transparent and will be restored
     * after the animation is finished.
     */
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        val root = findViewById<View>(android.R.id.content)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && splashScreen != null) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            splashScreen.setOnExitAnimationListener { splashProvider ->
                // For some reason the SplashScreen applies (incorrect) Y translation to the iconView
                splashProvider.iconView.translationY = 0F

                val activityAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = LinearOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        root.translationY = value * 16.dpToPx
                    }
                }

                val splashAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = FastOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        splashProvider.view.alpha = value
                    }
                    doOnEnd {
                        splashProvider.remove()
                    }
                }

                activityAnim.start()
                splashAnim.start()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        lifecycleScope.launch {
            val handle = handleIntentAction(intent)
            if (!handle) {
                super.onNewIntent(intent)
            }
        }
    }

    private suspend fun handleIntentAction(intent: Intent): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(applicationContext, notificationId, intent.getIntExtra("groupId", 0))
        }

        isHandlingShortcut = true

        when (intent.action) {
            SHORTCUT_ANIMELIB -> HomeScreen.openTab(HomeScreen.Tab.Animelib())
            SHORTCUT_ANIME -> {
                val idToOpen = intent.extras?.getLong(Constants.ANIME_EXTRA) ?: return false
                navigator.popUntilRoot()
                HomeScreen.openTab(HomeScreen.Tab.Animelib(idToOpen))
            }
            SHORTCUT_UPDATES -> HomeScreen.openTab(HomeScreen.Tab.Updates)
            SHORTCUT_HISTORY -> HomeScreen.openTab(HomeScreen.Tab.History)
            SHORTCUT_SOURCES -> HomeScreen.openTab(HomeScreen.Tab.Browse)
            SHORTCUT_ANIME_DOWNLOADS -> {
                navigator.popUntilRoot()
                HomeScreen.openTab(HomeScreen.Tab.More(toDownloads = true))
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (query != null && query.isNotEmpty()) {
                    navigator.popUntilRoot()
                    navigator.push(GlobalAnimeSearchScreen(query))
                }
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (query != null && query.isNotEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER) ?: ""
                    navigator.popUntilRoot()
                    navigator.push(GlobalAnimeSearchScreen(query, filter))
                }
            }
            INTENT_ANIMESEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (query != null && query.isNotEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER) ?: ""
                    navigator.popUntilRoot()
                    navigator.push(GlobalAnimeSearchScreen(query, filter))
                }
            }
            else -> {
                isHandlingShortcut = false
                return false
            }
        }

        ready = true
        isHandlingShortcut = false
        return true
    }

    override fun onDestroy() {
        animeSettingsSheet?.sheetScope?.cancel()
        animeSettingsSheet = null
        // AM (DC) -->
        if (connectionsPreferences.enableDiscordRPC().get()) {
            stopService(Intent(this@MainActivity, DiscordRPCService::class.java))
        }
        // <-- AM (DC)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (navigator.size == 1 &&
            !onBackPressedDispatcher.hasEnabledCallbacks() &&
            libraryPreferences.autoClearItemCache().get()
        ) {
            episodeCache.clear()
        }
        super.onBackPressed()
    }

    init {
        registerSecureActivity(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        launchIO { externalIntents.onActivityResult(requestCode, resultCode, data) }
        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        // Splash screen
        private const val SPLASH_MIN_DURATION = 500 // ms
        private const val SPLASH_MAX_DURATION = 5000 // ms
        private const val SPLASH_EXIT_ANIM_DURATION = 400L // ms

        // Shortcut actions
        const val SHORTCUT_ANIMELIB = "eu.kanade.tachiyomi.SHOW_ANIMELIB"
        const val SHORTCUT_ANIME = "eu.kanade.tachiyomi.SHOW_ANIME"
        const val SHORTCUT_UPDATES = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
        const val SHORTCUT_HISTORY = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
        const val SHORTCUT_SOURCES = "eu.kanade.tachiyomi.SHOW_CATALOGUES"
        const val SHORTCUT_ANIMEEXTENSIONS = "eu.kanade.tachiyomi.ANIMEEXTENSIONS"
        const val SHORTCUT_ANIME_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_ANIME_DOWNLOADS"

        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_ANIMESEARCH = "eu.kanade.tachiyomi.ANIMESEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }
}
