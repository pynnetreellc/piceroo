package com.piceroo.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.piceroo.app.data.AppUpdate
import com.piceroo.app.data.Clusterer
import com.piceroo.app.data.Deck
import com.piceroo.app.data.DeckKind
import com.piceroo.app.data.GameStore
import com.piceroo.app.data.MediaItem
import com.piceroo.app.data.MediaRepository
import com.piceroo.app.data.RoundResult
import com.piceroo.app.data.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Home : Screen
    data class Play(val deck: Deck) : Screen
    data class Celebrate(val result: RoundResult) : Screen
}

/** A round waiting on Android's "Delete these?" confirmation dialog. */
data class PendingRound(val trash: List<MediaItem>, val keep: List<MediaItem>)

/**
 * Holds the app's state and survives screen rotation. Compose screens read these
 * `mutableStateOf` values and redraw automatically whenever they change.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val store = GameStore(app)
    private var allItems: List<MediaItem> = emptyList()

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var loading by mutableStateOf(false)
        private set
    var decks by mutableStateOf<List<Deck>>(emptyList())
        private set
    var stats by mutableStateOf(store.stats())
        private set
    var pending: PendingRound? = null
        private set

    /** Photos earlier builds moved to Android's trash, which still use space until deleted. */
    var leftovers by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    /** Set when GitHub has a newer build than the one installed. */
    var update by mutableStateOf<AppUpdate?>(null)
        private set
    private var checkedForUpdate = false

    /** Checks GitHub once per app launch. */
    fun checkForUpdate() {
        if (checkedForUpdate) return
        checkedForUpdate = true
        val app = getApplication<Application>()
        val installed = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
        viewModelScope.launch {
            update = withContext(Dispatchers.IO) { UpdateChecker.check(installed) }
        }
    }

    fun dismissUpdate() {
        update = null
    }

    fun load() {
        loading = true
        viewModelScope.launch {
            // Reading thousands of rows is slow-ish, so do it off the UI thread.
            allItems = withContext(Dispatchers.IO) { repo.loadAll() }
            leftovers = withContext(Dispatchers.IO) { repo.loadTrashed(store.deletedKeys()) }
            rebuildDecks()
            loading = false
        }
    }

    private fun rebuildDecks() {
        decks = Clusterer.buildDecks(allItems, store.reviewedIds())
        stats = store.stats()
    }

    fun open(deck: Deck) {
        screen = Screen.Play(deck)
    }

    /** Picks a random deck, favoring the quick, satisfying kinds. */
    fun quickRound() {
        val quick = decks.filter { it.kind == DeckKind.BURST || it.kind == DeckKind.SCREENSHOTS }
        val deck = quick.ifEmpty { decks }.randomOrNull()
        if (deck != null) open(deck) else goHome()
    }

    fun goHome() {
        screen = Screen.Home
    }

    /** Called when a round ends. Returns true if the delete dialog needs to be shown. */
    fun finishRound(trash: List<MediaItem>, keep: List<MediaItem>): Boolean {
        if (trash.isEmpty()) {
            complete(trash, keep)
            return false
        }
        pending = PendingRound(trash, keep)
        return true
    }

    /** Android tells us whether you tapped "Allow" on the delete dialog. */
    fun onDeleteResult(confirmed: Boolean) {
        val round = pending ?: return
        pending = null
        if (confirmed) complete(round.trash, round.keep)
        // If cancelled, nothing was deleted and we stay on the round's summary.
    }

    private fun complete(trash: List<MediaItem>, keep: List<MediaItem>) {
        val result = store.completeRound(trash, keep)
        val gone = trash.map { it.id }.toSet()
        allItems = allItems.filter { it.id !in gone }
        rebuildDecks()
        screen = Screen.Celebrate(result)
    }

    fun cloudCleanupList(): String = store.cloudCleanupList()
}
