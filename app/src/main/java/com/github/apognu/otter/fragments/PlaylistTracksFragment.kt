package com.github.apognu.otter.fragments

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import com.github.apognu.otter.R
import com.github.apognu.otter.adapters.PlaylistTracksAdapter
import com.github.apognu.otter.repositories.PlaylistTracksRepository
import com.github.apognu.otter.utils.*
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_tracks.*
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class PlaylistTracksFragment : FunkwhaleFragment<PlaylistTrack, PlaylistTracksAdapter>() {
  override val viewRes = R.layout.fragment_tracks
  override val recycler: RecyclerView get() = tracks

  var albumId = 0
  var albumArtist = ""
  var albumTitle = ""
  var albumCover = ""

  companion object {
    fun new(playlist: Playlist): PlaylistTracksFragment {
      return PlaylistTracksFragment().apply {
        arguments = bundleOf(
          "albumId" to playlist.id,
          "albumArtist" to "N/A",
          "albumTitle" to playlist.name,
          "albumCover" to ""
        )
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    arguments?.apply {
      albumId = getInt("albumId")
      albumArtist = getString("albumArtist") ?: ""
      albumTitle = getString("albumTitle") ?: ""
      albumCover = getString("albumCover") ?: ""
    }

    adapter = PlaylistTracksAdapter(context)
    repository = PlaylistTracksRepository(context, albumId)

    watchEventBus()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    cover.visibility = View.INVISIBLE
    covers.visibility = View.VISIBLE

    artist.text = "Playlist"
    title.text = albumTitle
  }

  override fun onResume() {
    super.onResume()

    GlobalScope.launch(Main) {
      RequestBus.send(Request.GetCurrentTrack).wait<Response.CurrentTrack>()?.let { response ->
        adapter.currentTrack = response.track
        adapter.notifyDataSetChanged()
      }
    }

    play.setOnClickListener {
      CommandBus.send(Command.ReplaceQueue(adapter.data.map { it.track }.shuffled()))

      context.toast("All tracks were added to your queue")
    }

    queue.setOnClickListener {
      CommandBus.send(Command.AddToQueue(adapter.data.map { it.track }))

      context.toast("All tracks were added to your queue")
    }
  }

  override fun onDataFetched(data: List<PlaylistTrack>) {
    data.map { it.track.album }.toSet().map { it.cover.original }.take(4).forEachIndexed { index, url ->
      val imageView = when (index) {
        0 -> cover_top_left
        1 -> cover_top_right
        2 -> cover_bottom_left
        3 -> cover_bottom_right
        else -> cover_top_left
      }

      Picasso.get()
        .load(normalizeUrl(url))
        .into(imageView)
    }
  }

  private fun watchEventBus() {
    GlobalScope.launch(Main) {
      for (message in EventBus.asChannel<Event>()) {
        when (message) {
          is Event.TrackPlayed -> {
            GlobalScope.launch(Main) {
              RequestBus.send(Request.GetCurrentTrack).wait<Response.CurrentTrack>()?.let { response ->
                adapter.currentTrack = response.track
                adapter.notifyDataSetChanged()
              }
            }
          }
        }
      }
    }
  }
}