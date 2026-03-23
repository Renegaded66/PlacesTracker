package com.d_drostes_apps.placestracker.ui.friends

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.d_drostes_apps.placestracker.PlacesApplication
import com.d_drostes_apps.placestracker.R
import com.d_drostes_apps.placestracker.utils.SharingManager
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FriendsFragment : Fragment(R.layout.fragment_friends) {

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleManualImport(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val recycler = view.findViewById<RecyclerView>(R.id.friendsRecycler)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        
        val adapter = FriendsAdapter { friend ->
            val bundle = Bundle().apply {
                putString("friendId", friend.id)
                putString("username", friend.username)
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

        viewLifecycleOwner.lifecycleScope.launch {
            friendDao.getAllFriends().collectLatest { friends ->
                adapter.submitList(friends)
            }
        }

        view.findViewById<ExtendedFloatingActionButton>(R.id.fabImport).setOnClickListener {
            importLauncher.launch("*/*")
        }
    }

    private fun handleManualImport(uri: Uri) {
        val app = (requireActivity().application as PlacesApplication)
        val sharingManager = SharingManager(requireContext(), app.database)
        
        lifecycleScope.launch {
            val friendId = sharingManager.handleImport(uri)
            if (friendId != null) {
                val friend = app.database.friendDao().getFriendById(friendId)
                Toast.makeText(requireContext(), "Import von ${friend?.username} erfolgreich", Toast.LENGTH_LONG).show()
                
                // Refresh list or navigate
                val bundle = Bundle().apply {
                    putString("friendId", friendId)
                    putString("username", friend?.username)
                }
                findNavController().navigate(R.id.friendFeedFragment, bundle)
            } else {
                Toast.makeText(requireContext(), "Fehler beim Import: Ungültige Datei", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
