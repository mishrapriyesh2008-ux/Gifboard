package com.example.gifkeyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gifkeyboard.R
import com.example.gifkeyboard.data.AppDatabase
import com.example.gifkeyboard.data.MediaHistoryDao
import com.example.gifkeyboard.data.MediaItem
import com.example.gifkeyboard.data.toHistoryEntity
import com.example.gifkeyboard.data.toMediaItem
import com.example.gifkeyboard.media.MediaRepository
import com.example.gifkeyboard.util.MediaSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The keyboard itself. Android binds this as a system input method the moment
 * the user enables it in Settings > Languages & input and switches to it from
 * any text field — same lifecycle Gboard, SwiftKey, etc. all use.
 */
class GifKeyboardService : InputMethodService(), KeyboardLayoutBuilder.KeyEventListener {

    private val repository = MediaRepository()
    private lateinit var historyDao: MediaHistoryDao
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var qwertyContainer: View? = null
    private var mediaContainer: View? = null
    private var gridAdapter: MediaGridAdapter? = null
    private var favoriteIds = mutableSetOf<String>()

    private var searchDebounceRunnable: Runnable? = null
    private var currentTab = Tab.KEYS

    private enum class Tab { KEYS, MEDIA, RECENT, FAVORITES }

    override fun onCreate() {
        super.onCreate()
        historyDao = AppDatabase.getInstance(this).mediaHistoryDao()
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)
        rootView = view
        qwertyContainer = view.findViewById(R.id.qwertyContainer)
        mediaContainer = view.findViewById(R.id.mediaContainer)

        setupTabStrip(view)
        setupQwerty(view)
        setupMediaTab(view)
        switchToTab(Tab.KEYS)

        return view
    }

    // ---------------------------------------------------------------------
    // Tab strip
    // ---------------------------------------------------------------------

    private fun setupTabStrip(root: View) {
        root.findViewById<TextView>(R.id.tabKeys).setOnClickListener { switchToTab(Tab.KEYS) }
        root.findViewById<TextView>(R.id.tabMedia).setOnClickListener { switchToTab(Tab.MEDIA) }
        root.findViewById<TextView>(R.id.tabRecent).setOnClickListener { switchToTab(Tab.RECENT) }
        root.findViewById<TextView>(R.id.tabFavorites).setOnClickListener { switchToTab(Tab.FAVORITES) }
    }

    private fun switchToTab(tab: Tab) {
        currentTab = tab
        qwertyContainer?.visibility = if (tab == Tab.KEYS) View.VISIBLE else View.GONE
        mediaContainer?.visibility = if (tab == Tab.KEYS) View.GONE else View.VISIBLE

        val root = rootView ?: return
        listOf(R.id.tabKeys, R.id.tabMedia, R.id.tabRecent, R.id.tabFavorites).forEach { id ->
            root.findViewById<TextView>(id).setTextColor(resources.getColor(R.color.key_text, theme))
        }
        val activeId = when (tab) {
            Tab.KEYS -> R.id.tabKeys
            Tab.MEDIA -> R.id.tabMedia
            Tab.RECENT -> R.id.tabRecent
            Tab.FAVORITES -> R.id.tabFavorites
        }
        root.findViewById<TextView>(activeId).setTextColor(resources.getColor(R.color.accent, theme))

        when (tab) {
            Tab.MEDIA -> loadFeatured()
            Tab.RECENT -> loadRecent()
            Tab.FAVORITES -> loadFavorites()
            Tab.KEYS -> { /* nothing to load */ }
        }
    }

    // ---------------------------------------------------------------------
    // QWERTY tab
    // ---------------------------------------------------------------------

    private fun setupQwerty(root: View) {
        val container = root.findViewById<View>(R.id.qwertyContainer)
        val row1 = container.findViewById<LinearLayout>(R.id.row1)
        val row2 = container.findViewById<LinearLayout>(R.id.row2)
        val row3 = container.findViewById<LinearLayout>(R.id.row3)
        val row4 = container.findViewById<LinearLayout>(R.id.row4)
        KeyboardLayoutBuilder(this, this).build(row1, row2, row3, row4)
    }

    override fun onCharacterKey(char: String) {
        currentInputConnection?.commitText(char, 1)
    }

    override fun onBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    override fun onSpace() {
        currentInputConnection?.commitText(" ", 1)
    }

    override fun onEnter() {
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
        )
    }

    // ---------------------------------------------------------------------
    // Media tab
    // ---------------------------------------------------------------------

    private fun setupMediaTab(root: View) {
        val container = root.findViewById<View>(R.id.mediaContainer)
        val grid = container.findViewById<RecyclerView>(R.id.resultsGrid)
        val categoryRecycler = container.findViewById<RecyclerView>(R.id.categoryRecycler)
        val searchInput = container.findViewById<EditText>(R.id.searchInput)

        grid.layoutManager = GridLayoutManager(this, 3)
        gridAdapter = MediaGridAdapter(
            onItemTapped = { item -> sendClip(item) },
            onItemLongPressed = { item -> toggleFavorite(item) },
            isFavorite = { id -> favoriteIds.contains(id) }
        )
        grid.adapter = gridAdapter

        categoryRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoryRecycler.adapter = CategoryChipAdapter(repository.categories) { category ->
            performSearch(category.searchQuery)
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(text: android.text.Editable?) {
                searchDebounceRunnable?.let { mainHandler.removeCallbacks(it) }
                val query = text?.toString().orEmpty()
                val runnable = Runnable { performSearch(query) }
                searchDebounceRunnable = runnable
                mainHandler.postDelayed(runnable, 350) // debounce while typing, like Gboard's GIF search
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Load favorite ids once up front so badges render correctly from the first paint
        serviceScope.launch {
            favoriteIds = historyDao.observeFavorites().first().map { it.id }.toMutableSet()
        }
    }

    private fun loadFeatured() {
        setLoading(true)
        serviceScope.launch {
            val result = repository.featured()
            setLoading(false)
            result.onSuccess { showResults(it) }.onFailure { showEmpty() }
        }
    }

    private fun performSearch(query: String) {
        setLoading(true)
        serviceScope.launch {
            val result = if (query.isBlank()) repository.featured() else repository.search(query)
            setLoading(false)
            result.onSuccess { showResults(it) }.onFailure { showEmpty() }
        }
    }

    private fun loadRecent() {
        setLoading(true)
        serviceScope.launch {
            val recent = historyDao.observeRecent().first().map { it.toMediaItem() }
            setLoading(false)
            if (recent.isEmpty()) showEmpty() else showResults(recent)
        }
    }

    private fun loadFavorites() {
        setLoading(true)
        serviceScope.launch {
            val favorites = historyDao.observeFavorites().first().map { it.toMediaItem() }
            setLoading(false)
            if (favorites.isEmpty()) showEmpty() else showResults(favorites)
        }
    }

    private fun showResults(items: List<MediaItem>) {
        val container = mediaContainer ?: return
        container.findViewById<RecyclerView>(R.id.resultsGrid).visibility = View.VISIBLE
        container.findViewById<TextView>(R.id.emptyState).visibility = View.GONE
        gridAdapter?.submitList(items)
    }

    private fun showEmpty() {
        val container = mediaContainer ?: return
        container.findViewById<RecyclerView>(R.id.resultsGrid).visibility = View.GONE
        container.findViewById<TextView>(R.id.emptyState).visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        val container = mediaContainer ?: return
        container.findViewById<ProgressBar>(R.id.loadingSpinner).visibility =
            if (loading) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------------
    // Send + favorite actions
    // ---------------------------------------------------------------------

    private fun sendClip(item: MediaItem) {
        val ic = currentInputConnection
        val info = currentInputEditorInfo

        serviceScope.launch {
            // Network download happens off the main thread...
            val downloadResult = withContext(Dispatchers.IO) {
                MediaSender.downloadOnly(this@GifKeyboardService, item)
            }
            // ...but committing into the InputConnection MUST happen on the main
            // thread: many keyboard/app IPC stacks silently drop commitContent
            // calls made from a background thread.
            val result = downloadResult.fold(
                onSuccess = { file -> MediaSender.deliver(this@GifKeyboardService, item, file, ic, info) },
                onFailure = { MediaSender.SendResult.Failed("Couldn't download clip") }
            )
            when (result) {
                is MediaSender.SendResult.SentInline -> { /* silent success, like Gboard */ }
                is MediaSender.SendResult.FellBackToShareSheet ->
                    Toast.makeText(this@GifKeyboardService, getString(R.string.send_unsupported_fallback), Toast.LENGTH_SHORT).show()
                is MediaSender.SendResult.Failed ->
                    Toast.makeText(this@GifKeyboardService, result.reason, Toast.LENGTH_SHORT).show()
            }
            // Record usage regardless of delivery path, so Recents reflects what the user picked
            withContext(Dispatchers.IO) {
                val wasFavorite = favoriteIds.contains(item.id)
                historyDao.upsert(item.toHistoryEntity(isFavorite = wasFavorite, usedAt = System.currentTimeMillis()))
            }
        }
    }

    private fun toggleFavorite(item: MediaItem) {
        serviceScope.launch {
            val nowFavorite = !favoriteIds.contains(item.id)
            if (nowFavorite) favoriteIds.add(item.id) else favoriteIds.remove(item.id)

            // Ensure a row exists (e.g. favoriting straight from search results, not yet in history)
            historyDao.upsert(item.toHistoryEntity(isFavorite = nowFavorite, usedAt = System.currentTimeMillis()))
            historyDao.setFavorite(item.id, nowFavorite)

            // Cheap local refresh of badges using whatever's already bound — no network round-trip needed
            gridAdapter?.let { it.submitList(it.currentItems()) }

            Toast.makeText(
                this@GifKeyboardService,
                if (nowFavorite) "Added to favorites" else "Removed from favorites",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        switchToTab(Tab.KEYS) // always open on the letter keyboard, same as Gboard's default
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[Job]?.cancel()
    }
}
