package com.d_drostes_apps.placestracker.ui.friends

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.*
import com.d_drostes_apps.placestracker.ui.feed.FeedAdapter
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FriendFeedFragment : Fragment(R.layout.fragment_friend_feed) {

    private lateinit var adapter: FeedAdapter
    private lateinit var supabaseManager: SupabaseManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        supabaseManager = SupabaseManager(requireContext())
        val friendId = arguments?.getString("friendId") ?: return
        val username = arguments?.getString("username") ?: "Freund"
        val isFromSupabase = arguments?.getBoolean("isFromSupabase", false) ?: false

        val app = (requireActivity().application as PlacesApplication)
        val database = app.database
        val userDao = app.userDao

        viewLifecycleOwner.lifecycleScope.launch {
            userDao.getUserProfile().collectLatest { profile ->
                profile?.themeColor?.let { color ->
                    ThemeHelper.applyThemeColor(view, color)
                }
            }
        }

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "Feed von $username"
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val ivProfile = view.findViewById<ShapeableImageView>(R.id.ivFriendProfile)
        val tvUsername = view.findViewById<TextView>(R.id.tvFriendUsername)
        val tvInfo = view.findViewById<TextView>(R.id.tvFriendInfo)
        val tvFlag = view.findViewById<TextView>(R.id.tvFriendFlagBig)

        val recycler = view.findViewById<RecyclerView>(R.id.friendFeedRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        
        adapter = FeedAdapter(
            onItemClick = { item, stopId -> navigateToDetail(item, stopId) },
            showDrafts = false
        )
        recycler.adapter = adapter

        if (isFromSupabase) {
            loadSupabaseFeed(friendId, tvUsername, tvInfo, ivProfile)
        } else {
            loadLocalFriendFeed(friendId, tvUsername, tvInfo, tvFlag, ivProfile, database)
        }
    }

    private fun loadSupabaseFeed(userId: String, tvUsername: TextView, tvInfo: TextView, ivProfile: ShapeableImageView) {
        tvUsername.text = "Live von Supabase"
        viewLifecycleOwner.lifecycleScope.launch {
            val trips = supabaseManager.getFriendTrips(userId)
            val entries = supabaseManager.getFriendEntries(userId)
            
            val feedItems = mutableListOf<FeedItem>()
            
            entries.forEach { sEntry ->
                feedItems.add(FeedItem.Experience(Entry(
                    id = 0, // Placeholder
                    title = sEntry.title,
                    date = sEntry.date,
                    notes = null,
                    location = sEntry.location,
                    media = emptyList(),
                    supabaseId = sEntry.id
                )))
            }
            
            trips.forEach { sTrip ->
                // Note: Stops would need another API call or combined select
                feedItems.add(FeedItem.TripItem(Trip(
                    id = 0, // Placeholder
                    title = sTrip.title,
                    date = sTrip.date,
                    supabaseId = sTrip.id
                ), emptyList()))
            }
            
            feedItems.sortByDescending { it.sortDate }
            adapter.updateItems(feedItems)
            tvInfo.text = "${feedItems.size} öffentliche Erlebnisse"
        }
    }

    private fun loadLocalFriendFeed(friendId: String, tvUsername: TextView, tvInfo: TextView, tvFlag: TextView, ivProfile: ShapeableImageView, database: AppDatabase) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                database.friendDao().getFriendById(friendId)?.let { friend ->
                    tvUsername.text = friend.username
                    tvFlag.text = friend.countryCode?.let { getFlagEmoji(it) } ?: ""
                    Glide.with(this@FriendFeedFragment)
                        .load(friend.profilePicturePath)
                        .placeholder(R.drawable.placeholder)
                        .into(ivProfile)
                }

                combine(
                    database.entryDao().getEntriesByFriend(friendId),
                    database.tripDao().getTripsByFriend(friendId),
                    database.tripDao().getAllTripStops()
                ) { entries, trips, allStops ->
                    val items = mutableListOf<FeedItem>()
                    entries.forEach { items.add(FeedItem.Experience(it)) }
                    trips.forEach { trip ->
                        val stops = allStops.filter { it.tripId == trip.id }
                        items.add(FeedItem.TripItem(trip, stops))
                    }
                    items.sortWith(compareByDescending<FeedItem> { it.isLive }.thenByDescending { it.sortDate })
                    items
                }.collectLatest { items ->
                    tvInfo.text = "${items.size} geteilte Erlebnisse"
                    adapter.updateItems(items)
                }
            }
        }
    }

    private fun navigateToDetail(item: FeedItem, stopId: Int? = null) {
        val bundle = Bundle().apply {
            if (item is FeedItem.Experience) {
                putInt("entryId", item.id)
                putString("supabaseId", item.supabaseId)
            } else {
                putInt("tripId", item.id)
                putString("supabaseId", item.supabaseId)
                stopId?.let { putInt("stopId", it) }
            }
        }
        val destination = if (item is FeedItem.Experience) R.id.action_friendFeedFragment_to_entryDetailFragment else R.id.action_friendFeedFragment_to_tripDetailFragment
        findNavController().navigate(destination, bundle)
    }

    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val firstLetter = Character.codePointAt(countryCode, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(countryCode, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}
