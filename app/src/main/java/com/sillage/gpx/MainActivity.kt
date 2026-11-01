package com.sillage.gpx

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.sillage.gpx.record.RecordingService
import com.sillage.gpx.ui.AppViewModel
import com.sillage.gpx.ui.SillageApp
import com.sillage.gpx.ui.map.MapConfig
import com.sillage.gpx.ui.theme.SillageTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var onLocationGranted: (() -> Unit)? = null
    private var exportId: String? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importUris(uris)
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val id = exportId
        if (uri != null && id != null) vm.exportTo(id, uri)
        exportId = null
    }

    private val recordingPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when {
            fine -> vm.startRecording()
            coarse -> {
                vm.message("Position approximative seulement : activez la « position exacte » pour un tracé précis")
                vm.startRecording()
            }
            else -> vm.message("La localisation est nécessaire pour enregistrer un parcours")
        }
    }

    private val locationLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) onLocationGranted?.invoke() else vm.message("Localisation refusée")
        onLocationGranted = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        MapConfig.init(applicationContext)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            SillageTheme {
                SillageApp(
                    vm = vm,
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = { id ->
                        exportId = id
                        exportLauncher.launch(vm.displayName(id) + ".gpx")
                    },
                    onShare = { uri, name ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/gpx+xml"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(send, "Partager « $name »"))
                    },
                    onRecord = {
                        val perms = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        recordingPermissionLauncher.launch(perms.toTypedArray())
                    },
                    hasLocationPermission = {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    },
                    requestLocation = { granted ->
                        onLocationGranted = granted
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Fichier GPX ouvert ou partagé depuis une autre application. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(RecordingService.EXTRA_OPEN_RECORDING, false)) {
            vm.openRecording()
            return
        }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            else -> emptyList()
        }
        if (uris.isNotEmpty()) vm.importUris(uris)
    }

}
