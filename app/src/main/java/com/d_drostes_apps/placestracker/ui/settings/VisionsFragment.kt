package com.d_drostes_apps.placestracker.ui.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.d_drostes_apps.placestracker.R
import com.google.android.material.appbar.MaterialToolbar

class VisionsFragment : Fragment(R.layout.fragment_visions) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }
}
