package com.d_drostes_apps.placestracker.ui.feed

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import java.io.File

class FullscreenMediaAdapter(private val mediaPaths: List<String>) :
    RecyclerView.Adapter<FullscreenMediaAdapter.ViewHolder>() {

    private val players = mutableMapOf<Int, ExoPlayer>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivFullscreen)
        val playerView: PlayerView = view.findViewById(R.id.exoPlayerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fullscreen_media, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val path = mediaPaths[position]
        val isVideo = path.lowercase().let { 
            it.endsWith(".mp4") || it.endsWith(".mov") || it.endsWith(".3gp") || it.endsWith(".mkv") 
        }

        if (isVideo) {
            holder.imageView.visibility = View.GONE
            holder.playerView.visibility = View.VISIBLE
            
            // Release existing player for this position if it exists
            players[position]?.release()
            
            val player = ExoPlayer.Builder(holder.itemView.context).build()
            holder.playerView.player = player
            
            val mediaItem = MediaItem.fromUri(File(path).absolutePath)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = false // User can press play in the custom UI
            
            players[position] = player
        } else {
            holder.imageView.visibility = View.VISIBLE
            holder.playerView.visibility = View.GONE
            Glide.with(holder.itemView.context).load(File(path)).into(holder.imageView)
            
            // Cleanup player if this position was a video before
            players[position]?.release()
            players.remove(position)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.bindingAdapterPosition
        players[position]?.release()
        players.remove(position)
    }

    override fun getItemCount(): Int = mediaPaths.size

    fun releaseAllPlayers() {
        players.values.forEach { it.release() }
        players.clear()
    }
}
