package com.d_drostes_apps.placestracker

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.d_drostes_apps.placestracker.data.BucketItem
import com.d_drostes_apps.placestracker.data.Entry
import com.d_drostes_apps.placestracker.data.Trip
import com.d_drostes_apps.placestracker.service.TrackingService
import com.d_drostes_apps.placestracker.utils.SharingManager
import com.d_drostes_apps.placestracker.utils.ThemeHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.view.WindowCompat
import androidx.navigation.NavOptions
import androidx.navigation.NavGraph.Companion.findStartDestination
import android.content.res.ColorStateList
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController


    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment

        navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        //    .setupWithNavController(navController)
        bottomNav.setOnItemSelectedListener { item ->
            val options = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.graph.findStartDestination().id, inclusive = false, saveState = true)
                // Einfach die weichen Fades nutzen
                .setEnterAnim(R.anim.fade_in)
                .setExitAnim(R.anim.fade_out)
                .setPopEnterAnim(R.anim.fade_in)
                .setPopExitAnim(R.anim.fade_out)
                .build()

            try {
                navController.navigate(item.itemId, null, options)
                true
            } catch (e: Exception) {
                false
            }
        }

        val app = application as PlacesApplication
        lifecycleScope.launch {
            app.userDao.getUserProfile().collectLatest { profile ->
                profile?.themeColor?.let { color ->
                    // Holt sich ein Standard-Grau für die nicht-ausgewählten Tabs
                    val unselectedColor = MaterialColors.getColor(bottomNav, com.google.android.material.R.attr.colorOnSurfaceVariant)

                    // Definiert die Zustände: Ausgewählt vs. Nicht ausgewählt
                    val states = arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf(-android.R.attr.state_checked)
                    )
                    val colors = intArrayOf(color, unselectedColor)
                    val colorStateList = ColorStateList(states, colors)

                    // Wendet deine Custom-Farbe auf Icons und Text der Navbar an!
                    bottomNav.itemIconTintList = colorStateList
                    bottomNav.itemTextColor = colorStateList
                }
            }
        }

        intent?.let { handleIntent(it) }

        checkAndResumeTracking()
        
        checkBucketList()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data: Uri? = intent.data

        if (Intent.ACTION_VIEW == action && data != null) {
            val app = application as PlacesApplication
            val sharingManager = SharingManager(this, app.database)
            
            lifecycleScope.launch {
                val friendId = sharingManager.handleImport(data)
                if (friendId != null) {
                    val friend = app.database.friendDao().getFriendById(friendId)
                    Toast.makeText(this@MainActivity, "Import von ${friend?.username} erfolgreich", Toast.LENGTH_LONG).show()
                    
                    val bundle = Bundle().apply {
                        putString("friendId", friendId)
                        putString("username", friend?.username)
                    }
                    navController.navigate(R.id.friendFeedFragment, bundle)
                } else {
                    Toast.makeText(this@MainActivity, "Fehler beim Import der Datei", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkAndResumeTracking() {
        lifecycleScope.launch {
            val app = application as PlacesApplication
            val activeTrip = app.database.tripDao().getActiveTrackingTrip()
            activeTrip?.let { trip ->
                val intent = Intent(this@MainActivity, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_START_TRACKING
                    putExtra(TrackingService.EXTRA_TRIP_ID, trip.id)
                }
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }
    }

    private fun checkBucketList() {
        lifecycleScope.launch {
            val app = application as PlacesApplication
            val bucketDao = app.database.bucketDao()
            val entryDao = app.database.entryDao()
            val tripDao = app.database.tripDao()
            
            val now = System.currentTimeMillis()
            val bucketItems = bucketDao.getAllBucketItems().first()
            
            bucketItems.forEach { item ->
                if (!item.isCompleted && item.date != null && item.date <= now) {
                    if (item.isTrip) {
                        val newTrip = Trip(
                            title = item.title,
                            date = item.date,
                            notes = item.description,
                            coverImage = item.media.firstOrNull()
                        )
                        tripDao.insertTrip(newTrip)
                    } else {
                        val newEntry = Entry(
                            title = item.title,
                            date = item.date,
                            notes = item.description,
                            location = null,
                            media = item.media,
                            coverImage = item.media.firstOrNull()
                        )
                        entryDao.insert(newEntry)
                    }
                    bucketDao.updateBucketItem(item.copy(isCompleted = true))
                    Toast.makeText(this@MainActivity, "'${item.title}' wurde zum Feed hinzugefügt!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
