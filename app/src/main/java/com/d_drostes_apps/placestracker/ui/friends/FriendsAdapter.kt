package com.d_drostes_apps.placestracker.ui.friends

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Friend
import com.google.android.material.imageview.ShapeableImageView

class FriendsAdapter(private val onFriendClick: (Friend) -> Unit) :
    ListAdapter<Friend, FriendsAdapter.FriendViewHolder>(FriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = getItem(position)
        holder.bind(friend)
    }

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar = itemView.findViewById<ShapeableImageView>(R.id.ivFriendAvatar)
        private val tvName = itemView.findViewById<TextView>(R.id.tvFriendName)
        private val tvFlag = itemView.findViewById<TextView>(R.id.tvFriendFlag)

        fun bind(friend: Friend) {
            tvName.text = friend.username
            tvFlag.text = friend.countryCode ?: ""
            
            Glide.with(itemView.context)
                .load(friend.profilePicturePath)
                .placeholder(R.drawable.placeholder)
                .into(ivAvatar)

            itemView.setOnClickListener { onFriendClick(friend) }
        }
    }

    class FriendDiffCallback : DiffUtil.ItemCallback<Friend>() {
        override fun areItemsTheSame(oldItem: Friend, newItem: Friend): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Friend, newItem: Friend): Boolean = oldItem == newItem
    }
}
