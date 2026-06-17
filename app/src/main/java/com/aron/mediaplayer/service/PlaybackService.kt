@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.aron.mediaplayer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aron.mediaplayer.R
import com.aron.mediaplayer.data.AppDatabase
import com.aron.mediaplayer.data.ActivePlaylistStore
import com.aron.mediaplayer.data.PlaylistDao
import com.aron.mediaplayer.data.PlaylistTrack
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import androidx.media.app.NotificationCompat as MediaStyleCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class PlaybackService : MediaSessionService() {

    internal lateinit var player: ExoPlayer
        private set

    private var mediaSession: MediaSession? = null
    private var mediaSessionCompat: MediaSessionCompat? = null

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var playlistJob: Job? = null
    private lateinit var dao: PlaylistDao
    private lateinit var activeStore: ActivePlaylistStore
    private lateinit var prefs: SharedPreferences

    private var isForeground = false

    // Scrubber flows
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    // Queue (in-memory, cleared on restart)
    private val queueList = mutableListOf<PlaylistTrack>()

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "playback_channel"

        const val ACTION_PLAY = "com.aron.mediaplayer.PLAY"
        const val ACTION_PAUSE = "com.aron.mediaplayer.PAUSE"
        const val ACTION_NEXT = "com.aron.mediaplayer.NEXT"
        const val ACTION_PREVIOUS = "com.aron.mediaplayer.PREVIOUS"

        const val ACTION_ADD_TO_PLAYLIST = "com.aron.mediaplayer.ADD_TO_PLAYLIST"
        const val EXTRA_URI = "mediaUri"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_ARTWORK_URI = "artworkUri"
        const val EXTRA_PLAYLIST_ID = "playlistId"

        private const val PREFS_NAME = "playback_state"
        private const val KEY_PLAYLIST_ID = "playlist_id"
        private const val KEY_TRACK_INDEX = "track_index"
        private const val KEY_TRACK_POSITION = "track_position"
        private const val KEY_WAS_PLAYING = "was_playing"

        private val _currentUri = MutableStateFlow<String?>(null)
        val currentUri: StateFlow<String?> = _currentUri

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying

        private val _currentSong = MutableStateFlow<PlaylistTrack?>(null)
        val currentSong: StateFlow<PlaylistTrack?> = _currentSong

        // Queue state exposed to UI
        private val _queue = MutableStateFlow<List<PlaylistTrack>>(emptyList())
        val queue: StateFlow<List<PlaylistTrack>> = _queue

        @Volatile
        private var serviceInstance: PlaybackService? = null

        val player: ExoPlayer?
            get() = serviceInstance?.player

        internal val instance: PlaybackService?
            get() = serviceInstance
    }

    // Media session callback
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = player.play()
        override fun onPause() = player.pause()
        override fun onSkipToNext() = player.seekToNextMediaItem()
        override fun onSkipToPrevious() = player.seekToPreviousMediaItem()
        override fun onSeekTo(pos: Long) = player.seekTo(pos)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            handleForegroundState(isPlaying)
            updateMediaSessionState(isPlaying, player.currentPosition, player.duration)
            maybeUpdateNotification()
            savePlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentUri.value = mediaItem?.localConfiguration?.uri?.toString()
            mediaItem?.let { item ->
                val mm = item.mediaMetadata
                _currentSong.value = PlaylistTrack(
                    id = item.mediaId.toLongOrNull() ?: 0L,
                    playlistId = -1,
                    uri = item.localConfiguration?.uri?.toString() ?: "",
                    title = mm.title?.toString() ?: "Unknown Title",
                    artist = mm.artist?.toString() ?: "Unknown Artist",
                    duration = 0L,
                    artworkUri = mm.artworkUri?.toString()
                )
            }

            // If the new item is the head of the queue, pop it
            val currentId = mediaItem?.mediaId?.toLongOrNull()
            if (currentId != null && queueList.isNotEmpty() && queueList.first().id == currentId) {
                queueList.removeAt(0)
                _queue.value = queueList.toList()
            }

            updateMediaSessionState(player.isPlaying, player.currentPosition, player.duration)
            maybeUpdateNotification()
            savePlaybackState()
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        player = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
            repeatMode = Player.REPEAT_MODE_ALL
        }

        mediaSession = MediaSession.Builder(this, player).build()

        mediaSessionCompat = MediaSessionCompat(this, "PlaybackService").apply {
            setCallback(mediaSessionCallback)
            isActive = true
        }

        createNotificationChannel()

        dao = AppDatabase.getInstance(applicationContext).playlistDao()
        activeStore = ActivePlaylistStore(applicationContext, dao)

        // Scrubber update loop
        serviceScope.launch {
            while (isActive) {
                _position.value = player.currentPosition
                _duration.value = player.duration.takeIf { it > 0 } ?: 0L
                delay(200)
            }
        }

        playlistJob = serviceScope.launch {
            val initialId = activeStore.ensureDefaultPlaylistSelected()
            activeStore.activePlaylistId.collectLatest { pid ->
                val playlistId = if (pid > 0) pid else initialId
                dao.getTracksForPlaylist(playlistId).collectLatest { tracks ->
                    syncQueueWith(tracks)
                }
            }
        }

        restorePlaybackState()
    }

    override fun onDestroy() {
        savePlaybackState()
        playlistJob?.cancel()
        serviceJob.cancel()
        player.removeListener(playerListener)
        mediaSession?.release()
        mediaSessionCompat?.release()
        mediaSessionCompat = null
        player.release()
        serviceInstance = null
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                val pidFromIntent = intent.getLongExtra(EXTRA_PLAYLIST_ID, -1L)

                if (uri != null) {
                    serviceScope.launch {
                        val pid = if (pidFromIntent > 0) pidFromIntent
                        else activeStore.ensureDefaultPlaylistSelected()

                        val playlistTracks = dao.getTracksForPlaylist(pid).first()
                        val indexInPlaylist = playlistTracks.indexOfFirst { it.uri == uri }

                        if (indexInPlaylist >= 0) {
                            if (activeStore.activePlaylistId.first() != pid) {
                                activeStore.setActivePlaylistId(pid)
                            }
                            loadPlaylistIntoPlayer(playlistTracks, indexInPlaylist, 0, true)
                        } else {
                            getSongFromMediaStore(uri)?.let { track ->
                                _currentSong.value = track
                                _currentUri.value = track.uri
                                loadPlaylistIntoPlayer(listOf(track), 0, 0, true)
                            }
                        }
                    }
                } else player.play()
            }

            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> player.seekToNextMediaItem()
            ACTION_PREVIOUS -> handlePrevious()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleForegroundState(isPlaying: Boolean) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification()

        if (isPlaying) {
            if (!isForeground) {
                startForeground(NOTIFICATION_ID, notification)
                isForeground = true
            } else nm.notify(NOTIFICATION_ID, notification)
        } else {
            if (isForeground) {
                stopForeground(false)
                nm.notify(NOTIFICATION_ID, notification)
                isForeground = false
            }
        }
    }

    private fun savePlaybackState() {
        val pid = runBlocking { activeStore.activePlaylistId.first() }
        prefs.edit()
            .putLong(KEY_PLAYLIST_ID, pid)
            .putInt(KEY_TRACK_INDEX, player.currentMediaItemIndex)
            .putLong(KEY_TRACK_POSITION, player.currentPosition)
            .putBoolean(KEY_WAS_PLAYING, player.isPlaying)
            .apply()
    }

    private fun restorePlaybackState() {
        val pid = prefs.getLong(KEY_PLAYLIST_ID, -1L)
        val index = prefs.getInt(KEY_TRACK_INDEX, 0)
        val pos = prefs.getLong(KEY_TRACK_POSITION, 0L)
        val wasPlaying = prefs.getBoolean(KEY_WAS_PLAYING, false)

        if (pid > 0) {
            serviceScope.launch {
                val tracks = dao.getTracksForPlaylist(pid).first()
                if (tracks.isNotEmpty()) {
                    loadPlaylistIntoPlayer(
                        tracks,
                        index.coerceIn(tracks.indices),
                        pos,
                        wasPlaying
                    )
                }
            }
        }
    }

    private fun getSongFromMediaStore(uri: String): PlaylistTrack? {
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val contentUri = Uri.parse(uri)
        contentResolver.query(contentUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return PlaylistTrack(
                    playlistId = -1,
                    uri = uri,
                    title = cursor.getString(0) ?: "Unknown Title",
                    artist = cursor.getString(1) ?: "Unknown Artist",
                    duration = cursor.getLong(2),
                    artworkUri = null
                )
            }
        }
        return null
    }

    private fun buildMediaItems(tracks: List<PlaylistTrack>): List<MediaItem> =
        tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(track.artworkUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }

    private fun loadPlaylistIntoPlayer(
        playlistTracks: List<PlaylistTrack>,
        startIndex: Int,
        startPositionMs: Long,
        autoPlay: Boolean
    ) {
        val mediaItems = buildMediaItems(playlistTracks)
        player.setMediaItems(mediaItems)
        player.seekTo(startIndex, startPositionMs)

        mediaItems.getOrNull(startIndex)?.let { item ->
            _currentUri.value = item.localConfiguration?.uri?.toString()
            val mm = item.mediaMetadata
            _currentSong.value = PlaylistTrack(
                id = item.mediaId.toLongOrNull() ?: 0L,
                playlistId = playlistTracks.getOrNull(startIndex)?.playlistId ?: -1,
                uri = _currentUri.value ?: "",
                title = mm.title?.toString() ?: "Unknown Title",
                artist = mm.artist?.toString() ?: "Unknown Artist",
                duration = playlistTracks.getOrNull(startIndex)?.duration ?: 0L,
                artworkUri = mm.artworkUri?.toString()
            )
        }

        player.prepare()
        if (autoPlay) player.play()
    }

    private fun syncQueueWith(tracks: List<PlaylistTrack>) {
        val playlistItems = buildMediaItems(tracks)

        val wasPlaying = player.isPlaying
        val oldMediaId = player.currentMediaItem?.mediaId
        val oldPosition = player.currentPosition

        if (playlistItems.isEmpty()) {
            player.setMediaItems(emptyList())
            player.pause()
            _currentUri.value = null
            _currentSong.value = null
            return
        }

        // Reset to playlist items only
        player.setMediaItems(playlistItems, false)
        player.prepare()

        // Base index: where the current item sits in the new playlist, or fallback to 0
        val baseIndex = playlistItems.indexOfFirst { it.mediaId == oldMediaId }
            .let { if (it >= 0) it else 0 }

        // Reinsert queue items immediately after current item as a contiguous block
        if (queueList.isNotEmpty()) {
            val queueMediaItems = buildMediaItems(queueList)
            queueMediaItems.forEachIndexed { offset, item ->
                val insertIndex = (baseIndex + 1 + offset)
                    .coerceIn(0, player.mediaItemCount)
                player.addMediaItem(insertIndex, item)
            }
        }

        // Restore playback to the same mediaId if possible
        val newIndex = (0 until player.mediaItemCount)
            .indexOfFirst { player.getMediaItemAt(it).mediaId == oldMediaId }

        val targetIndex = if (newIndex >= 0) newIndex else baseIndex
        player.seekTo(targetIndex, oldPosition)

        if (wasPlaying) player.play()
    }

    private fun updateCurrentSong(
        items: List<MediaItem>,
        tracks: List<PlaylistTrack>,
        index: Int
    ) {
        _currentUri.value = items[index].localConfiguration?.uri.toString()
        val mm = items[index].mediaMetadata
        _currentSong.value = PlaylistTrack(
            id = items[index].mediaId.toLongOrNull() ?: 0L,
            playlistId = tracks.getOrNull(index)?.playlistId ?: -1,
            uri = _currentUri.value ?: "",
            title = mm.title?.toString() ?: "Unknown Title",
            artist = mm.artist?.toString() ?: "Unknown Artist",
            duration = tracks.getOrNull(index)?.duration ?: 0L,
            artworkUri = mm.artworkUri?.toString()
        )
    }

    private fun handlePrevious() {
        if (player.currentPosition > 3000) player.seekTo(0)
        else player.seekToPreviousMediaItem()
    }

    // Queue API

    // Add to queue (after current item + existing queue block)
    fun addToQueue(track: PlaylistTrack) {
        val mediaItem = buildMediaItems(listOf(track)).first()

        val baseIndex = player.currentMediaItemIndex
        val insertIndex = (baseIndex + 1 + queueList.size)
            .coerceIn(0, player.mediaItemCount)

        player.addMediaItem(insertIndex, mediaItem)

        queueList.add(track)
        _queue.value = queueList.toList()
    }

    // Play next (force to front of queue block)
    fun playNext(track: PlaylistTrack) {
        val mediaItem = buildMediaItems(listOf(track)).first()
        val insertIndex = (player.currentMediaItemIndex + 1)
            .coerceIn(0, player.mediaItemCount)
        player.addMediaItem(insertIndex, mediaItem)

        queueList.add(0, track)
        _queue.value = queueList.toList()
    }

    fun clearQueue() {
        val idsToRemove = queueList.map { it.id.toString() }.toSet()
        val indicesToRemove = mutableListOf<Int>()
        for (i in 0 until player.mediaItemCount) {
            val id = player.getMediaItemAt(i).mediaId
            if (id in idsToRemove) indicesToRemove.add(i)
        }
        indicesToRemove.sortedDescending().forEach { player.removeMediaItem(it) }

        queueList.clear()
        _queue.value = emptyList()
    }

    fun removeFromQueue(trackId: Long) {
        val idStr = trackId.toString()
        val indexInPlayer = (0 until player.mediaItemCount)
            .firstOrNull { player.getMediaItemAt(it).mediaId == idStr }

        if (indexInPlayer != null) {
            player.removeMediaItem(indexInPlayer)
        }

        queueList.removeAll { it.id == trackId }
        _queue.value = queueList.toList()
    }

    private fun buildLoadingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Loading…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private val artworkCache = mutableMapOf<Uri, Bitmap?>()

    private fun buildNotification(): Notification {
        val currentItem = player.currentMediaItem
        val mm = currentItem?.mediaMetadata

        val title = mm?.title?.toString()?.takeIf { it.isNotEmpty() } ?: "Sample Track"
        val artist = mm?.artist?.toString()?.takeIf { it.isNotEmpty() } ?: "Unknown Artist"
        val artUri: Uri = mm?.artworkUri
            ?: Uri.parse("https://via.placeholder.com/300.png?text=No+Artwork")

        updateMediaSessionMetadata(title, artist, artworkCache[artUri])

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(player.isPlaying)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                android.R.drawable.ic_media_previous, "Previous",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, PlaybackService::class.java).setAction(ACTION_PREVIOUS),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (player.isPlaying) "Pause" else "Play",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, PlaybackService::class.java).setAction(
                        if (player.isPlaying) ACTION_PAUSE else ACTION_PLAY
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                android.R.drawable.ic_media_next, "Next",
                PendingIntent.getService(
                    this, 2,
                    Intent(this, PlaybackService::class.java).setAction(ACTION_NEXT),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setStyle(
                MediaStyleCompat.MediaStyle()
                    .setMediaSession(mediaSessionCompat?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        artworkCache[artUri]?.let { cachedBmp ->
            updateMediaSessionMetadata(title, artist, cachedBmp)
            return builder.setLargeIcon(cachedBmp).build()
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val bmp = Glide.with(this@PlaybackService)
                    .asBitmap()
                    .load(artUri)
                    .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                    .get()
                artworkCache[artUri] = bmp
                withContext(Dispatchers.Main) {
                    updateMediaSessionMetadata(title, artist, bmp)
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, builder.setLargeIcon(bmp).build())
                }
            } catch (_: Exception) {
                artworkCache[artUri] = null
                withContext(Dispatchers.Main) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, builder.build())
                }
            }
        }

        return builder.build()
    }

    private fun maybeUpdateNotification() {
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_HIGH
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateMediaSessionMetadata(title: String, artist: String, art: Bitmap?) {
        val duration = player.duration.takeIf { it > 0 } ?: 0L

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .apply {
                if (art != null) {
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                }
            }
            .build()

        mediaSessionCompat?.setMetadata(metadata)
    }

    private fun updateMediaSessionState(isPlaying: Boolean, position: Long, duration: Long) {
        val state = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                position,
                1.0f
            )
            .setBufferedPosition(duration)
            .build()

        mediaSessionCompat?.setPlaybackState(state)
    }
}
