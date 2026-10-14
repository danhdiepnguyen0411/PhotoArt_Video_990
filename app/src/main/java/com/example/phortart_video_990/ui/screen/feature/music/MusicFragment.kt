package com.example.phortart_video_990.ui.screen.feature.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.phortart_video_990.R
import com.example.phortart_video_990.core.base.BaseFragment
import com.example.phortart_video_990.core.utils.AudioCacheManager
import com.example.phortart_video_990.core.utils.navigateSafe
import com.example.phortart_video_990.core.utils.safePopBackStack
import com.example.phortart_video_990.data.model.CategoryModel
import com.example.phortart_video_990.data.model.TemplateModel
import com.example.phortart_video_990.data.repository.TemplateRepository
import com.example.phortart_video_990.databinding.FragmentMusicBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MusicFragment : BaseFragment<FragmentMusicBinding>(FragmentMusicBinding::inflate) {

    private val repository = TemplateRepository()
    private lateinit var categoryAdapter: MusicCategoryAdapter
    private lateinit var musicAdapter: MusicAdapter

    private var allMusicTracks: List<TemplateModel> = emptyList()
    private var musicCategories: List<CategoryModel> = listOf(CategoryModel.ALL)
    private var selectedCategoryCode: String = "ALL"
    private var currentSearchQuery: String = ""
    private var selectedTrackId: String? = null
    private var savedScrollPosition: Int = -1
    private var savedScrollOffset: Int = 0

    private var mediaPlayer: MediaPlayer? = null
    private var audioActionJob: Job? = null
    private var loadJob: Job? = null
    private var isStartMergeFlow: Boolean = false

    private val isTabMode: Boolean
        get() = (parentFragment is com.example.phortart_video_990.ui.screen.main.MainFragment) ||
                (parentFragment?.parentFragment is com.example.phortart_video_990.ui.screen.main.MainFragment)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("saved_music_track_id")?.let {
            selectedTrackId = it
        }
        savedInstanceState?.getString("saved_category_code")?.let {
            selectedCategoryCode = it
        }
        savedInstanceState?.getInt("saved_scroll_pos", -1)?.let {
            if (it >= 0) savedScrollPosition = it
        }
        savedInstanceState?.getInt("saved_scroll_offset", 0)?.let {
            savedScrollOffset = it
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        captureScrollPosition()
        outState.putString("saved_music_track_id", selectedTrackId)
        outState.putString("saved_category_code", selectedCategoryCode)
        outState.putBoolean("is_start_merge_flow", isStartMergeFlow)
        outState.putInt("saved_scroll_pos", savedScrollPosition)
        outState.putInt("saved_scroll_offset", savedScrollOffset)
    }

    override fun initView() {
        isStartMergeFlow = arguments?.getBoolean("is_start_merge_flow", false) ?: false

        // Restore selected track ID from arguments, nav savedStateHandle, or persistent preferences
        val navStateHandle = findNavController().currentBackStackEntry?.savedStateHandle
        val passedTrackId = arguments?.getString("selected_track_id")?.takeIf { it.isNotBlank() }
            ?: navStateHandle?.get<String>("selected_track_id")?.takeIf { it.isNotBlank() }
        val passedTitle = arguments?.getString("selected_music_title")?.takeIf { it.isNotBlank() }
            ?: navStateHandle?.get<String>("selected_music_title")?.takeIf { it.isNotBlank() }
        val passedAudioUrl = arguments?.getString("selected_music_audio_url")?.takeIf { it.isNotBlank() }
            ?: navStateHandle?.get<String>("selected_music_audio_url")?.takeIf { it.isNotBlank() }

        if (!passedTrackId.isNullOrBlank()) {
            selectedTrackId = passedTrackId
        } else if (passedTitle.isNullOrBlank() && passedAudioUrl.isNullOrBlank() && selectedTrackId.isNullOrBlank()) {
            // ONLY fall back to persistent preferences if the caller did NOT pass any track ID, title, or audio URL
            selectedTrackId = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(requireContext()).lastSelectedMusicTrackId
        }

        if (savedScrollPosition == -1) {
            val pref = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(requireContext())
            savedScrollPosition = pref.lastMusicScrollPosition
            savedScrollOffset = pref.lastMusicScrollOffset
        }

        binding.header.setBackButtonVisible(!isTabMode)
        binding.header.setTitleRes(R.string.func_04_music)

        // Setup horizontal category chips
        categoryAdapter = MusicCategoryAdapter(
            categories = musicCategories,
            selectedCategoryCode = selectedCategoryCode
        ) { selectedCategory ->
            if (!selectedCategoryCode.equals(selectedCategory.code, ignoreCase = true)) {
                selectedCategoryCode = selectedCategory.code
                savedScrollPosition = 0
                savedScrollOffset = 0
                context?.let { ctx ->
                    val p = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(ctx)
                    p.lastMusicScrollPosition = 0
                    p.lastMusicScrollOffset = 0
                    p.recordSelectedCategory(selectedCategory.code)
                }
                applyFilter()
            }
        }
        binding.rvMusicCategories.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMusicCategories.adapter = categoryAdapter

        // Setup vertical music tracks list with remembered selection
        musicAdapter = MusicAdapter(
            tracks = emptyList(),
            initialSelectedTrackId = selectedTrackId,
            onTrackSelected = { track, _ ->
                selectAndPlayTrack(track, autoPlay = true)
            },
            onPreviewClick = { track, _ ->
                if (track.id == selectedTrackId) {
                    toggleMiniPlayerPlayback()
                } else {
                    selectAndPlayTrack(track, autoPlay = true)
                }
            }
        )
        musicAdapter.stateRestorationPolicy = androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.rvMusicTracks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMusicTracks.adapter = musicAdapter

        binding.rvMusicTracks.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val pos = lm.findFirstVisibleItemPosition()
                if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    val v = lm.findViewByPosition(pos)
                    val offset = v?.top ?: 0
                    savedScrollPosition = pos
                    savedScrollOffset = offset
                    context?.let { ctx ->
                        val p = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(ctx)
                        p.lastMusicScrollPosition = pos
                        p.lastMusicScrollOffset = offset
                    }
                }
            }
        })

        loadCategoriesAndTracks()
        setupNetworkObserver()
    }

    private fun setupNetworkObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                var wasOffline = false
                com.example.phortart_video_990.core.utils.NetworkMonitor.observeNetwork(requireContext()).collect { isOnline ->
                    if (isOnline) {
                        val needReload = allMusicTracks.isEmpty()
                        if (wasOffline || needReload) {
                            loadCategoriesAndTracks()
                        }
                        wasOffline = false
                    } else {
                        wasOffline = true
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (allMusicTracks.isEmpty() && isAdded && context != null &&
            com.example.phortart_video_990.core.utils.NetworkMonitor.isOnline(requireContext())) {
            loadCategoriesAndTracks()
        }
    }

    override fun initListener() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopAudio()
                findNavController().safePopBackStack()
            }
        })

        val b = bindingOrNull ?: return

        b.swipeRefreshLayout.setColorSchemeColors(
            android.graphics.Color.parseColor("#4A7BFE"),
            android.graphics.Color.parseColor("#9153EE")
        )
        b.swipeRefreshLayout.setOnChildScrollUpCallback { _, _ ->
            bindingOrNull?.rvMusicTracks?.canScrollVertically(-1) ?: false
        }
        b.swipeRefreshLayout.setOnRefreshListener {
            loadCategoriesAndTracks()
        }


        b.llEmptyState.setOnClickListener {
            loadCategoriesAndTracks()
        }

        b.header.setOnBackClickListener {
            stopAudio()
            findNavController().safePopBackStack()
        }

        // Mini player play/pause toggle button
        b.btnMiniPlayPause.setOnClickListener {
            toggleMiniPlayerPlayback()
        }

        // Tap mini player card to locate selected song in list
        b.cardMiniPlayer.setOnClickListener {
            val curId = selectedTrackId ?: return@setOnClickListener
            val targetIndex = (b.rvMusicTracks.adapter as? MusicAdapter)?.getTrackPosition(curId) ?: -1
            if (targetIndex >= 0) {
                (b.rvMusicTracks.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(targetIndex, 40)
            }
        }

        // Search text watcher
        b.etSearchMusic.addTextChangedListener { text ->
            val newQuery = text?.toString()?.trim() ?: ""
            if (newQuery != currentSearchQuery) {
                currentSearchQuery = newQuery
                savedScrollPosition = 0
                savedScrollOffset = 0
                bindingOrNull?.ivClearSearch?.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilter()
            }
        }

        b.ivClearSearch.setOnClickListener {
            savedScrollPosition = 0
            savedScrollOffset = 0
            bindingOrNull?.etSearchMusic?.setText("")
        }

        b.etSearchMusic.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                bindingOrNull?.let { safeBinding ->
                    imm?.hideSoftInputFromWindow(safeBinding.etSearchMusic.windowToken, 0)
                }
                true
            } else {
                false
            }
        }

        // Bottom Continue button
        b.btnConfirmMusic.setOnClickListener {
            onContinueClicked()
        }
    }

    private fun captureScrollPosition() {
        val lm = bindingOrNull?.rvMusicTracks?.layoutManager as? LinearLayoutManager ?: return
        val pos = lm.findFirstVisibleItemPosition()
        if (pos != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
            val v = lm.findViewByPosition(pos)
            val offset = v?.top ?: 0
            savedScrollPosition = pos
            savedScrollOffset = offset
            context?.let { ctx ->
                val p = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(ctx)
                p.lastMusicScrollPosition = pos
                p.lastMusicScrollOffset = offset
            }
        }
    }

    private fun loadCategoriesAndTracks() {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val b = bindingOrNull ?: return@launch
            if (!b.swipeRefreshLayout.isRefreshing) {
                b.pbLoading.visibility = View.VISIBLE
            }
            b.llEmptyState.visibility = View.GONE
            try {
                // Load music categories from API
                val apiMusicCategories = repository.getMusicCategories()
                if (!isAdded || view == null || bindingOrNull == null) return@launch
                musicCategories = apiMusicCategories
                if (::categoryAdapter.isInitialized) {
                    categoryAdapter.submitList(musicCategories)
                }

                // Load all music tracks initially
                val tracks = repository.getMusicTracks(categoryCode = "ALL")
                if (!isAdded || view == null || bindingOrNull == null) return@launch
                allMusicTracks = tracks

                // Dynamically discover extra categories from tracks
                discoverExtraMusicCategories(tracks)

                // Match currently selected track with high precision
                val navState = findNavController().currentBackStackEntry?.savedStateHandle
                val explicitTrackId = arguments?.getString("selected_track_id")?.takeIf { it.isNotBlank() }
                    ?: navState?.get<String>("selected_track_id")?.takeIf { it.isNotBlank() }
                    ?: selectedTrackId?.takeIf { it.isNotBlank() }
                val explicitTitle = arguments?.getString("selected_music_title")?.takeIf { it.isNotBlank() }
                    ?: navState?.get<String>("selected_music_title")?.takeIf { it.isNotBlank() }
                val explicitAudioUrl = arguments?.getString("selected_music_audio_url")?.takeIf { it.isNotBlank() }
                    ?: navState?.get<String>("selected_music_audio_url")?.takeIf { it.isNotBlank() }

                var matchedTrack: TemplateModel? = null

                // 1. Match by Track ID
                if (!explicitTrackId.isNullOrBlank()) {
                    matchedTrack = tracks.find { it.id == explicitTrackId }
                }

                // 2. Match by exact Title
                if (matchedTrack == null && !explicitTitle.isNullOrBlank()) {
                    matchedTrack = tracks.find { it.title.equals(explicitTitle, ignoreCase = true) }
                }

                // 3. Match by Audio URL
                if (matchedTrack == null && !explicitAudioUrl.isNullOrBlank()) {
                    val cleanAudioUrl = explicitAudioUrl.substringBefore("?")
                    matchedTrack = tracks.find {
                        val trackCleanUrl = it.safeAudioUrl?.substringBefore("?")
                        trackCleanUrl?.equals(cleanAudioUrl, ignoreCase = true) == true ||
                                it.safeAudioUrl?.equals(explicitAudioUrl, ignoreCase = true) == true
                    }
                }

                // 4. Match by fuzzy Title (contains)
                if (matchedTrack == null && !explicitTitle.isNullOrBlank()) {
                    matchedTrack = tracks.find {
                        it.title.contains(explicitTitle, ignoreCase = true) ||
                                explicitTitle.contains(it.title, ignoreCase = true)
                    }
                }

                // 5. Fallback to Preferences if caller did not provide any track info
                if (matchedTrack == null && explicitTrackId.isNullOrBlank() && explicitTitle.isNullOrBlank() && explicitAudioUrl.isNullOrBlank()) {
                    val lastPrefId = com.example.phortart_video_990.data.local.PreferencesManager.getInstance(requireContext()).lastSelectedMusicTrackId
                    if (!lastPrefId.isNullOrBlank()) {
                        matchedTrack = tracks.find { it.id == lastPrefId }
                    }
                }

                // 6. Final fallback to first track
                if (matchedTrack == null) {
                    matchedTrack = tracks.firstOrNull()
                }

                if (matchedTrack != null) {
                    selectedTrackId = matchedTrack.id
                    musicAdapter.setSelectedTrackId(matchedTrack.id)
                    context?.let { ctx ->
                        com.example.phortart_video_990.data.local.PreferencesManager.getInstance(ctx).lastSelectedMusicTrackId = matchedTrack.id
                    }

                    // If user was filtering another category, reset to ALL so the selected track is guaranteed visible
                    val hasExplicitTrack = !explicitTrackId.isNullOrBlank() || !explicitTitle.isNullOrBlank() || !explicitAudioUrl.isNullOrBlank()
                    if (hasExplicitTrack && selectedCategoryCode != "ALL" && !matchedTrack.category.equals(selectedCategoryCode, ignoreCase = true)) {
                        selectedCategoryCode = "ALL"
                        if (::categoryAdapter.isInitialized) {
                            categoryAdapter.setSelected("ALL")
                        }
                    }
                }

                // Apply initial category & search filter
                applyFilter()

                // Display initial selected track in mini player if available
                val targetTrackToDisplay = matchedTrack ?: allMusicTracks.firstOrNull()
                if (targetTrackToDisplay != null) {
                    val isPlayingNow = mediaPlayer?.isPlaying == true
                    if (!isPlayingNow) {
                        selectAndPlayTrack(targetTrackToDisplay, autoPlay = false)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    throw e
                }
                bindingOrNull?.llEmptyState?.visibility = View.VISIBLE
            } finally {
                bindingOrNull?.pbLoading?.visibility = View.GONE
                bindingOrNull?.swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    private fun applyFilter() {
        val b = bindingOrNull ?: return
        if (!isAdded || view == null) return

        var filtered = allMusicTracks

        // Category filter
        val isAll = selectedCategoryCode.isBlank() ||
                selectedCategoryCode.equals("ALL", ignoreCase = true) ||
                selectedCategoryCode.equals("Tất cả", ignoreCase = true)

        if (!isAll) {
            val target = selectedCategoryCode.trim()
            val cleanTarget = target.removePrefix("MUSIC_").lowercase()
            filtered = filtered.filter {
                it.category.equals(target, ignoreCase = true) ||
                        it.category.removePrefix("MUSIC_").lowercase() == cleanTarget
            }
        }

        // Search query filter
        if (currentSearchQuery.isNotBlank()) {
            val q = currentSearchQuery.lowercase()
            filtered = filtered.filter { track ->
                track.title.lowercase().contains(q) ||
                        track.category.lowercase().contains(q) ||
                        track.category.removePrefix("MUSIC_").lowercase().contains(q)
            }
        }

        if (::musicAdapter.isInitialized) {
            if (!selectedTrackId.isNullOrBlank()) {
                musicAdapter.setSelectedTrackId(selectedTrackId)
            }
            musicAdapter.submitList(filtered)
        }
        b.llEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        // Update mini player with currently selected track
        val currentSelectedTrack = allMusicTracks.find { it.id == selectedTrackId }
        if (currentSelectedTrack != null) {
            b.cardMiniPlayer.visibility = View.VISIBLE
            b.tvMiniTrackTitle.text = currentSelectedTrack.title
            b.tvMiniTrackDuration.text = currentSelectedTrack.displayDuration
            if (!currentSelectedTrack.safeImageUrl.isNullOrBlank()) {
                b.ivMiniCover.load(currentSelectedTrack.safeImageUrl) {
                    crossfade(true)
                    placeholder(R.drawable.ic_func_music)
                    error(R.drawable.ic_func_music)
                }
            } else {
                b.ivMiniCover.setImageResource(R.drawable.ic_func_music)
            }
        }

        // Keep mini-player play/pause state in sync with audio playback
        val isCurrentlyPlaying = mediaPlayer?.isPlaying == true
        val isLoadingAudio = audioActionJob?.isActive == true
        if (isCurrentlyPlaying) {
            b.waveView.setPlaying(true)
            b.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_pause_small)
            b.ivMiniPlayPauseIcon.visibility = View.VISIBLE
            b.pbMiniLoading.visibility = View.GONE
        } else if (isLoadingAudio) {
            b.waveView.setPlaying(false)
            b.ivMiniPlayPauseIcon.visibility = View.GONE
            b.pbMiniLoading.visibility = View.VISIBLE
        } else {
            b.waveView.setPlaying(false)
            b.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
            b.ivMiniPlayPauseIcon.visibility = View.VISIBLE
            b.pbMiniLoading.visibility = View.GONE
        }

        val selectedIndex = filtered.indexOfFirst { it.id == selectedTrackId }
        val navState = findNavController().currentBackStackEntry?.savedStateHandle
        val hasExplicitPassed = !arguments?.getString("selected_track_id").isNullOrBlank() ||
                !arguments?.getString("selected_music_title").isNullOrBlank() ||
                !arguments?.getString("selected_music_audio_url").isNullOrBlank() ||
                !navState?.get<String>("selected_track_id").isNullOrBlank() ||
                !navState?.get<String>("selected_music_title").isNullOrBlank()

        b.rvMusicTracks.post {
            if (isAdded && view != null && bindingOrNull != null) {
                bindingOrNull?.rvMusicTracks?.stopScroll()
                val lm = bindingOrNull?.rvMusicTracks?.layoutManager as? LinearLayoutManager
                if (hasExplicitPassed && selectedIndex >= 0) {
                    lm?.scrollToPositionWithOffset(selectedIndex, 40)
                } else if (savedScrollPosition in 0 until filtered.size) {
                    lm?.scrollToPositionWithOffset(savedScrollPosition, savedScrollOffset)
                } else if (selectedIndex >= 0) {
                    lm?.scrollToPositionWithOffset(selectedIndex, 40)
                } else {
                    lm?.scrollToPositionWithOffset(0, 0)
                }
            }
        }
    }

    private fun discoverExtraMusicCategories(tracks: List<TemplateModel>) {
        if (!isAdded || view == null || bindingOrNull == null) return
        val currentCats = musicCategories.toMutableList()
        var updated = false
        val existingCodes = currentCats.map { it.code.lowercase() }.toSet()

        tracks.forEach { tpl ->
            val code = tpl.category.trim()
            val isMusic = code.startsWith("MUSIC_", ignoreCase = true) || tpl.isMusic
            if (code.isNotBlank() && isMusic && !existingCodes.contains(code.lowercase())) {
                currentCats.add(CategoryModel(code = code, name = code, active = true))
                updated = true
            }
        }
        if (updated) {
            musicCategories = currentCats
            if (::categoryAdapter.isInitialized) {
                categoryAdapter.submitList(currentCats)
            }
        }
    }

    private fun selectAndPlayTrack(track: TemplateModel, autoPlay: Boolean = true) {
        selectedTrackId = track.id
        context?.let { ctx ->
            com.example.phortart_video_990.data.local.PreferencesManager.getInstance(ctx).lastSelectedMusicTrackId = track.id
        }
        findNavController().currentBackStackEntry?.savedStateHandle?.set("selected_track_id", track.id)
        if (::musicAdapter.isInitialized) {
            musicAdapter.setSelectedTrackId(track.id)
        }

        val b = bindingOrNull ?: return
        b.cardMiniPlayer.visibility = View.VISIBLE
        b.tvMiniTrackTitle.text = track.title
        b.tvMiniTrackDuration.text = track.displayDuration

        if (!track.safeImageUrl.isNullOrBlank()) {
            b.ivMiniCover.load(track.safeImageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_func_music)
                error(R.drawable.ic_func_music)
            }
        } else {
            b.ivMiniCover.setImageResource(R.drawable.ic_func_music)
        }

        if (autoPlay) {
            playTrackAudio(track)
        } else {
            b.waveView.setPlaying(false)
            b.pbMiniLoading.visibility = View.GONE
            b.ivMiniPlayPauseIcon.visibility = View.VISIBLE
            b.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
        }
    }

    private fun toggleMiniPlayerPlayback() {
        val b = bindingOrNull ?: return
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            b.waveView.setPlaying(false)
            b.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
            b.ivMiniPlayPauseIcon.visibility = View.VISIBLE
            b.pbMiniLoading.visibility = View.GONE
        } else if (mediaPlayer != null) {
            mediaPlayer?.start()
            b.waveView.setPlaying(true)
            b.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_pause_small)
            b.ivMiniPlayPauseIcon.visibility = View.VISIBLE
            b.pbMiniLoading.visibility = View.GONE
        } else {
            val track = allMusicTracks.find { it.id == selectedTrackId }
                ?: (if (::musicAdapter.isInitialized) musicAdapter.getSelectedTrack() else null)
            if (track != null) {
                playTrackAudio(track)
            }
        }
    }

    private fun playTrackAudio(track: TemplateModel) {
        val audioUrl = track.safeAudioUrl
        if (audioUrl.isNullOrBlank()) {
            context?.let {
                Toast.makeText(it, getString(R.string.toast_music_preview_unavailable), Toast.LENGTH_SHORT).show()
            }
            bindingOrNull?.waveView?.setPlaying(false)
            bindingOrNull?.ivMiniPlayPauseIcon?.setImageResource(R.drawable.ic_play_small)
            bindingOrNull?.ivMiniPlayPauseIcon?.visibility = View.VISIBLE
            bindingOrNull?.pbMiniLoading?.visibility = View.GONE
            return
        }

        stopAudioKeepMiniPlayer()

        val b = bindingOrNull ?: return
        b.ivMiniPlayPauseIcon.visibility = View.GONE
        b.pbMiniLoading.visibility = View.VISIBLE
        b.waveView.setPlaying(false)

        audioActionJob = viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            val cachedFile = AudioCacheManager.getOrDownloadAudio(
                ctx,
                audioUrl
            )

            if (!isAdded || view == null || bindingOrNull == null) return@launch

            if (cachedFile == null || !cachedFile.exists() || cachedFile.length() == 0L) {
                bindingOrNull?.let { safeBinding ->
                    safeBinding.pbMiniLoading.visibility = View.GONE
                    safeBinding.ivMiniPlayPauseIcon.visibility = View.VISIBLE
                    safeBinding.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
                    safeBinding.waveView.setPlaying(false)
                }
                context?.let {
                    Toast.makeText(it, getString(R.string.toast_cannot_download_preview), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(cachedFile.absolutePath)
                    setOnPreparedListener { mp ->
                        if (!isAdded || view == null || bindingOrNull == null) {
                            mp.release()
                            return@setOnPreparedListener
                        }
                        mp.start()
                        bindingOrNull?.let { safeBinding ->
                            safeBinding.pbMiniLoading.visibility = View.GONE
                            safeBinding.ivMiniPlayPauseIcon.visibility = View.VISIBLE
                            safeBinding.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_pause_small)
                            safeBinding.waveView.setPlaying(true)
                        }
                    }
                    setOnCompletionListener {
                        bindingOrNull?.let { safeBinding ->
                            safeBinding.ivMiniPlayPauseIcon.visibility = View.VISIBLE
                            safeBinding.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
                            safeBinding.waveView.setPlaying(false)
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        bindingOrNull?.let { safeBinding ->
                            safeBinding.pbMiniLoading.visibility = View.GONE
                            safeBinding.ivMiniPlayPauseIcon.visibility = View.VISIBLE
                            safeBinding.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
                            safeBinding.waveView.setPlaying(false)
                        }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                bindingOrNull?.let { safeBinding ->
                    safeBinding.pbMiniLoading.visibility = View.GONE
                    safeBinding.ivMiniPlayPauseIcon.visibility = View.VISIBLE
                    safeBinding.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
                    safeBinding.waveView.setPlaying(false)
                }
            }
        }
    }

    private fun stopAudioKeepMiniPlayer() {
        audioActionJob?.cancel()
        audioActionJob = null
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {
            // Ignored
        } finally {
            mediaPlayer = null
        }
    }

    private fun onContinueClicked() {
        captureScrollPosition()
        val selectedTrack = allMusicTracks.find { it.id == selectedTrackId }
            ?: (if (::musicAdapter.isInitialized) musicAdapter.getSelectedTrack() else null)
        if (selectedTrack == null) {
            context?.let {
                Toast.makeText(it, getString(R.string.toast_please_select_music), Toast.LENGTH_SHORT).show()
            }
            return
        }

        stopAudio()

        val audioUrl = selectedTrack.safeAudioUrl
        if (audioUrl.isNullOrBlank()) {
            returnTrackResult(selectedTrack, null)
            return
        }

        val b = bindingOrNull ?: return
        // Show loading state on bottom Continue button
        b.btnConfirmMusic.isEnabled = false
        b.ivConfirmArrow.visibility = View.GONE
        b.pbConfirmLoading.visibility = View.VISIBLE

        audioActionJob?.cancel()
        audioActionJob = viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            val cachedFile = AudioCacheManager.getOrDownloadAudio(
                ctx,
                audioUrl
            )

            if (!isAdded || view == null || bindingOrNull == null) return@launch

            bindingOrNull?.btnConfirmMusic?.isEnabled = true
            bindingOrNull?.pbConfirmLoading?.visibility = View.GONE
            bindingOrNull?.ivConfirmArrow?.visibility = View.VISIBLE

            returnTrackResult(selectedTrack, cachedFile?.absolutePath)
        }
    }

    private fun returnTrackResult(track: TemplateModel, cachedFilePath: String?) {
        captureScrollPosition()
        val ctx = context ?: return
        com.example.phortart_video_990.data.local.PreferencesManager.getInstance(ctx)
            .recordSelectedCategory(track.category)
        com.example.phortart_video_990.data.local.PreferencesManager.getInstance(ctx)
            .lastSelectedMusicTrackId = track.id
        selectedTrackId = track.id
        findNavController().currentBackStackEntry?.savedStateHandle?.set("selected_track_id", track.id)

        val musicSubtitle = track.displayDuration

        if (isTabMode) {
            val bundle = android.os.Bundle().apply {
                putString("selected_track_id", track.id)
                putString("selected_music_title", track.title)
                putString("selected_music_subtitle", musicSubtitle)
                putString("selected_music_audio_url", track.safeAudioUrl)
                putString("selected_music_file_path", cachedFilePath)
                putString("selected_music_image_url", track.safeImageUrl)
                putFloat("selected_template_duration_sec", track.durationSeconds.toFloat())
                putInt("selected_template_photo_count", track.targetPhotoCount)
            }
            val navController = parentFragment?.parentFragment?.findNavController()
                ?: parentFragment?.findNavController()
                ?: findNavController()
            navController.navigateSafe(R.id.action_mainFragment_to_memoriesFragment, bundle)
        } else if (isStartMergeFlow) {
            val bundle = android.os.Bundle().apply {
                putString("selected_track_id", track.id)
                putString("selected_music_title", track.title)
                putString("selected_music_subtitle", musicSubtitle)
                putString("selected_music_audio_url", track.safeAudioUrl)
                putString("selected_music_file_path", cachedFilePath)
                putString("selected_music_image_url", track.safeImageUrl)
                putFloat("selected_template_duration_sec", track.durationSeconds.toFloat())
                putInt("selected_template_photo_count", track.targetPhotoCount)
            }
            findNavController().navigateSafe(R.id.action_musicFragment_to_memoriesFragment, bundle)
        } else {
            // Return selected music title, audio URL, cached local path, and cover image to parent screen
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_track_id",
                track.id
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_music_title",
                track.title
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_music_subtitle",
                musicSubtitle
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_music_audio_url",
                track.safeAudioUrl
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_music_file_path",
                cachedFilePath
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_music_image_url",
                track.safeImageUrl
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_template_duration_sec",
                track.durationSeconds.toFloat()
            )
            findNavController().previousBackStackEntry?.savedStateHandle?.set(
                "selected_template_photo_count",
                track.targetPhotoCount
            )

            findNavController().safePopBackStack()
        }
    }

    private fun stopAudio() {
        stopAudioKeepMiniPlayer()
        bindingOrNull?.let { safeBinding ->
            safeBinding.waveView.setPlaying(false)
            safeBinding.ivMiniPlayPauseIcon.setImageResource(R.drawable.ic_play_small)
            safeBinding.ivMiniPlayPauseIcon.visibility = View.VISIBLE
            safeBinding.pbMiniLoading.visibility = View.GONE
        }
        if (isAdded && bindingOrNull != null && ::musicAdapter.isInitialized) {
            musicAdapter.clearAllLoading()
        }
    }

    override fun onPause() {
        captureScrollPosition()
        stopAudio()
        super.onPause()
    }

    override fun onDestroyView() {
        captureScrollPosition()
        loadJob?.cancel()
        loadJob = null
        audioActionJob?.cancel()
        audioActionJob = null
        stopAudio()
        super.onDestroyView()
    }
}
