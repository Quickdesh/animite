package eu.kanade.presentation.updates.anime

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.EmptyScreen
import eu.kanade.presentation.components.EntryBottomActionMenu
import eu.kanade.presentation.components.EpisodeDownloadAction
import eu.kanade.presentation.components.FastScrollLazyColumn
import eu.kanade.presentation.components.LoadingScreen
import eu.kanade.presentation.components.PullRefresh
import eu.kanade.presentation.components.Scaffold
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesItem
import eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

@Composable
fun AnimeUpdateScreen(
    state: AnimeUpdatesState,
    snackbarHostState: SnackbarHostState,
    lastUpdated: Long,
    relativeTime: Int,
    onClickCover: (AnimeUpdatesItem) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onUpdateLibrary: () -> Boolean,
    onDownloadEpisode: (List<AnimeUpdatesItem>, EpisodeDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<AnimeUpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<AnimeUpdatesItem>, fillermark: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<AnimeUpdatesItem>, seen: Boolean) -> Unit,
    onMultiDeleteClicked: (List<AnimeUpdatesItem>) -> Unit,
    onUpdateSelected: (AnimeUpdatesItem, Boolean, Boolean, Boolean) -> Unit,
    onOpenEpisode: (AnimeUpdatesItem, altPlayer: Boolean) -> Unit,
    // AM (UH) -->
    navigateUp: (() -> Unit)?,
    // <-- AM (UH)
) {
    BackHandler(enabled = state.selectionMode, onBack = { onSelectAll(false) })

    val context = LocalContext.current

    Scaffold(
        topBar = { scrollBehavior ->
            UpdatesAppBar(
                onUpdateLibrary = { onUpdateLibrary() },
                actionModeCounter = state.selected.size,
                onSelectAll = { onSelectAll(true) },
                onInvertSelection = { onInvertSelection() },
                onCancelActionMode = { onSelectAll(false) },
                scrollBehavior = scrollBehavior,
                // AM (UH) -->
                navigateUp = navigateUp,
                // <-- AM (UH)
            )
        },
        bottomBar = {
            AnimeUpdatesBottomBar(
                selected = state.selected,
                onDownloadEpisode = onDownloadEpisode,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiFillermarkClicked = onMultiFillermarkClicked,
                onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                onMultiDeleteClicked = onMultiDeleteClicked,
                onOpenEpisode = onOpenEpisode,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(contentPadding))
            state.items.isEmpty() -> EmptyScreen(
                textResource = R.string.information_no_recent,
                modifier = Modifier.padding(contentPadding),
            )
            else -> {
                val scope = rememberCoroutineScope()
                var isRefreshing by remember { mutableStateOf(false) }

                PullRefresh(
                    refreshing = isRefreshing,
                    onRefresh = {
                        val started = onUpdateLibrary()
                        if (!started) return@PullRefresh
                        scope.launch {
                            // Fake refresh status but hide it after a second as it's a long running task
                            isRefreshing = true
                            delay(1.seconds)
                            isRefreshing = false
                        }
                    },
                    enabled = !state.selectionMode,
                    indicatorPadding = contentPadding,
                ) {
                    FastScrollLazyColumn(
                        contentPadding = contentPadding,
                    ) {
                        if (lastUpdated > 0L) {
                            animeUpdatesLastUpdatedItem(lastUpdated)
                        }
                        animeUpdatesUiItems(
                            uiModels = state.getUiModel(context, relativeTime),
                            selectionMode = state.selectionMode,
                            onUpdateSelected = onUpdateSelected,
                            onClickCover = onClickCover,
                            onClickUpdate = onOpenEpisode,
                            onDownloadEpisode = onDownloadEpisode,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdatesAppBar(
    modifier: Modifier = Modifier,
    onUpdateLibrary: () -> Unit,
    // For action mode
    actionModeCounter: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancelActionMode: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    navigateUp: (() -> Unit)? = null,
) {
    AppBar(
        modifier = modifier,
        title = stringResource(R.string.label_recent_updates),
        actions = {
            IconButton(onClick = onUpdateLibrary) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.action_update_library),
                )
            }
        },
        actionModeCounter = actionModeCounter,
        onCancelActionMode = onCancelActionMode,
        actionModeActions = {
            IconButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = stringResource(R.string.action_select_all),
                )
            }
            IconButton(onClick = onInvertSelection) {
                Icon(
                    imageVector = Icons.Outlined.FlipToBack,
                    contentDescription = stringResource(R.string.action_select_inverse),
                )
            }
        },
        scrollBehavior = scrollBehavior,
        navigateUp = navigateUp,
    )
}

@Composable
private fun AnimeUpdatesBottomBar(
    selected: List<AnimeUpdatesItem>,
    onDownloadEpisode: (List<AnimeUpdatesItem>, EpisodeDownloadAction) -> Unit,
    onMultiBookmarkClicked: (List<AnimeUpdatesItem>, bookmark: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<AnimeUpdatesItem>, fillermark: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<AnimeUpdatesItem>, seen: Boolean) -> Unit,
    onMultiDeleteClicked: (List<AnimeUpdatesItem>) -> Unit,
    onOpenEpisode: (AnimeUpdatesItem, altPlayer: Boolean) -> Unit,
) {
    val playerPreferences: PlayerPreferences = Injekt.get()
    EntryBottomActionMenu(
        visible = selected.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        onBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, true)
        }.takeIf { selected.fastAny { !it.update.bookmark } },
        onRemoveBookmarkClicked = {
            onMultiBookmarkClicked.invoke(selected, false)
        }.takeIf { selected.fastAll { it.update.bookmark } },
        // AM (FM) -->
        onFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected, true)
        }.takeIf { selected.fastAny { !it.update.fillermark } },
        onRemoveFillermarkClicked = {
            onMultiFillermarkClicked.invoke(selected, false)
        }.takeIf { selected.fastAll { it.update.fillermark } },
        // <-- AM (FM)
        onMarkAsViewedClicked = {
            onMultiMarkAsSeenClicked(selected, true)
        }.takeIf { selected.fastAny { !it.update.seen } },
        onMarkAsUnviewedClicked = {
            onMultiMarkAsSeenClicked(selected, false)
        }.takeIf { selected.fastAny { it.update.seen } },
        onDownloadClicked = {
            onDownloadEpisode(selected, EpisodeDownloadAction.START)
        }.takeIf {
            selected.fastAny { it.downloadStateProvider() != AnimeDownload.State.DOWNLOADED }
        },
        onDeleteClicked = {
            onMultiDeleteClicked(selected)
        }.takeIf { selected.fastAny { it.downloadStateProvider() == AnimeDownload.State.DOWNLOADED } },
        onExternalClicked = {
            onOpenEpisode(selected[0], true)
        }.takeIf { !playerPreferences.alwaysUseExternalPlayer().get() && selected.size == 1 },
        onInternalClicked = {
            onOpenEpisode(selected[0], true)
        }.takeIf { playerPreferences.alwaysUseExternalPlayer().get() && selected.size == 1 },
        isManga = false,
    )
}

sealed class AnimeUpdatesUiModel {
    data class Header(val date: String) : AnimeUpdatesUiModel()
    data class Item(val item: AnimeUpdatesItem) : AnimeUpdatesUiModel()
}