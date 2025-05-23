package com.xelabooks.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.xelabooks.app.databinding.ActivityMainBinding

private const val TAG = "MainActivity"
private const val PERMISSION_REQUEST_CODE = 1001

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide default title

        val navView: BottomNavigationView = binding.navView

        // Get the NavHostFragment and NavController
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_player, R.id.navigation_import
            )
        )
        
        // Connect the bottom navigation view with the navigation controller
        setupActionBarWithNavController(navController, appBarConfiguration)
        
        // Special handling for player navigation to prevent duplicate player instances
        // Store current book ID for syncing navigation
        var currentBookId: String? = null
        
        navView.setOnItemSelectedListener { item ->
            if (item.itemId == R.id.navigation_player) {
                // When player button is selected, check if we're already in player screen
                if (navController.currentDestination?.id == R.id.navigation_player) {
                    // We're already on player screen, do nothing
                    return@setOnItemSelectedListener true
                }
                
                // Check if we have a specific book ID to navigate to
                if (currentBookId != null) {
                    val bundle = Bundle().apply {
                        putString("bookId", currentBookId)
                    }
                    navController.navigate(R.id.navigation_player, bundle)
                    return@setOnItemSelectedListener true
                }
            }
            
            navController.navigate(item.itemId)
            true
        }
        
        // Track navigation changes to sync player state
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            // Set toolbar logo size based on destination  
            when (destination.id) {
                R.id.navigation_player -> {
                    // Slightly smaller for player screen 
                    val sizeDp = 44 // Fits nicely in toolbar
                    val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
                    binding.toolbarLogo.layoutParams.width = sizePx
                    binding.toolbarLogo.layoutParams.height = sizePx
                    binding.toolbarLogo.requestLayout()
                    
                    // Save the current book ID when navigating to player
                    currentBookId = arguments?.getString("bookId")
                }
                else -> {
                    // Full height - matches the blue toolbar height
                    val sizeDp = 48 // Matches toolbar height nicely
                    val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
                    binding.toolbarLogo.layoutParams.width = sizePx
                    binding.toolbarLogo.layoutParams.height = sizePx
                    binding.toolbarLogo.requestLayout()
                }
            }
        }
        
        // Request permissions
        checkAndRequestPermissions()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        return when (item.itemId) {
            R.id.action_settings -> {
                navController.navigate(R.id.navigation_settings)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, 
                permissionsNeeded.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all permissions were granted
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (!allGranted) {
                Log.w(TAG, "Some permissions were denied")
                // We'll handle this case in the fragment when needed
            }
        }
    }
}