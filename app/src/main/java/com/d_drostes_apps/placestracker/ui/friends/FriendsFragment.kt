package com.d_drostes_apps.placestracker.ui.friends

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.data.Friend
import com.d_drostes_apps.placestracker.utils.SharingManager
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FriendsFragment : Fragment(R.layout.fragment_friends) {

    private var allLocalFriends: List<Friend> = emptyList()

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleManualImport(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val etSearchUser = view.findViewById<TextInputEditText>(R.id.etSearchUser)
        val recycler = view.findViewById<RecyclerView>(R.id.friendsRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        
        val adapter = FriendsAdapter { friend ->
            val bundle = Bundle().apply {
                putString("friendId", friend.id)
                putString("username", friend.username)
                putBoolean("isFromSupabase", friend.profilePicturePath == "SUPABASE")
            }
            findNavController().navigate(R.id.action_friendsFragment_to_friendFeedFragment, bundle)
        }
        recycler.adapter = adapter

        val app = (requireActivity().application as PlacesApplication)
        val friendDao = app.database.friendDao()
        val userDao = app.userDao
        
        viewLifecycleOwner.lifecycleScope.launch {
            userDao.getUserProfile().collectLatest { profile ->
                profile?.themeColor?.let { color ->
                    ThemeHelper.applyThemeColor(view, color)
                }
            }
        }

        // Lokale Freunde anzeigen und für Suche speichern
        viewLifecycleOwner.lifecycleScope.launch {
            friendDao.getAllFriends().collectLatest { friends ->
                allLocalFriends = friends
                if (etSearchUser.text.isNullOrBlank()) {
                    adapter.submitList(friends)
                } else {
                    // Falls während des Ladens schon Text eingegeben wurde
                    filterFriends(etSearchUser.text.toString(), adapter)
                }
            }
        }

        // Suchfunktion (nur lokal)
        etSearchUser.addTextChangedListener { text ->
            filterFriends(text.toString(), adapter)
        }

        view.findViewById<ExtendedFloatingActionButton>(R.id.fabImport).setOnClickListener {
            importLauncher.launch("*/*")
        }
    }

    private fun filterFriends(query: String, adapter: FriendsAdapter) {
        val filtered = if (query.isBlank()) {
            allLocalFriends
        } else {
            allLocalFriends.filter { 
                it.username.contains(query, ignoreCase = true) 
            }
        }
        adapter.submitList(filtered)
    }

    private fun handleManualImport(uri: Uri) {
        val app = (requireActivity().application as PlacesApplication)
        val sharingManager = SharingManager(requireContext(), app.database)
        
        lifecycleScope.launch {
            val friendId = sharingManager.handleImport(uri)
            if (friendId != null) {
                val friend = app.database.friendDao().getFriendById(friendId)
                Toast.makeText(requireContext(), getString(R.string.import_success, friend?.username), Toast.LENGTH_LONG).show()
                
                val bundle = Bundle().apply {
                    putString("friendId", friendId)
                    putString("username", friend?.username)
                }
                findNavController().navigate(R.id.friendFeedFragment, bundle)
            } else {
                Toast.makeText(requireContext(), getString(R.string.import_error_invalid), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
