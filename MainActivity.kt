package com.example.systemrecovery

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvHeader: TextView
    private lateinit var tvMenu: TextView
    private lateinit var tvLogs: TextView
    private lateinit var scrollLogs: ScrollView

    private val menuItems = listOf(
        "Reboot system now",
        "Reboot to bootloader",
        "Apply update from ADB",
        "Apply update from SD card",
        "Wipe data/factory reset",
        "Wipe cache partition",
        "Mount /system",
        "View recovery logs",
        "Power off"
    )

    private var selectedIndex = 4 // Empieza seleccionado en "Wipe data/factory reset"
    private val handler = Handler(Looper.getMainLooper())
    private var isWiping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recovery)
        hideSystemUI()

        tvHeader = findViewById(R.id.tvHeader)
        tvMenu = findViewById(R.id.tvMenu)
        tvLogs = findViewById(R.id.tvLogs)
        scrollLogs = findViewById(R.id.scrollLogs)

        loadRealDeviceInfo()
        renderMenu()

        // Inicia la simulación automática tras 2.5 segundos
        handler.postDelayed({ startFakeWipe() }, 2500)
    }

    private fun loadRealDeviceInfo() {
        val headerText = """
            Android Recovery
            ${Build.BRAND}/${Build.PRODUCT}/${Build.DEVICE}
            ${Build.VERSION.RELEASE}/${Build.ID}/${Build.VERSION.INCREMENTAL}
            user/release-keys
            Bootloader: ${Build.BOOTLOADER}
            Security Patch: ${Build.VERSION.SECURITY_PATCH}
        """.trimIndent()
        tvHeader.text = headerText
    }

    private fun renderMenu() {
        val sb = StringBuilder()
        menuItems.forEachIndexed { index, item ->
            if (index == selectedIndex) {
                sb.append("> $item <\n")
            } else {
                sb.append("  $item\n")
            }
        }
        tvMenu.text = sb.toString()
    }

    // Permite moverse con los botones físicos de volumen
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isWiping) return true // Bloquea botones mientras "borra"
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else menuItems.size - 1
                renderMenu()
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                selectedIndex = if (selectedIndex < menuItems.size - 1) selectedIndex + 1 else 0
                renderMenu()
                true
            }
            KeyEvent.KEYCODE_POWER -> {
                startFakeWipe()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun getRealPartitions(): List<String> {
        val blocks = mutableListOf<String>()
        try {
            File("/proc/mounts").forEachLine { line ->
                if (line.contains("/dev/block")) {
                    val parts = line.split(" ")
                    if (parts.size >= 2) {
                        blocks.add("${parts[0]} -> ${parts[1]}")
                    }
                }
            }
        } catch (e: Exception) {
            blocks.add("/dev/block/bootdevice/by-name/userdata")
            blocks.add("/dev/block/bootdevice/by-name/cache")
            blocks.add("/dev/block/bootdevice/by-name/metadata")
        }
        return blocks.take(6)
    }

    private fun startFakeWipe() {
        if (isWiping) return
        isWiping = true

        val realPartitions = getRealPartitions()
        val steps = mutableListOf(
            "\n-- Wiping data...",
            "Mounting system partitions..."
        )
        realPartitions.forEach { steps.add("Found block: $it") }
        steps.addAll(listOf(
            "Formatting /data...",
            "Erasing /data/media/0 (Internal Storage)...",
            "Deleting user profiles and encryption keys...",
            "Formatting /cache...",
            "Formatting /metadata...",
            "Wiping Dalvik/ART cache...",
            "E: [libfs_mgr] Warning: /data block size mismatch (4096)",
            "Recreating f2fs filesystem on userdata...",
            "Data wipe complete.",
            "\n-- Simulation finished. Press back twice to exit."
        ))

        var delay = 0L
        steps.forEach { step ->
            delay += (600..1400).random()
            handler.postDelayed({
                tvLogs.append("$step\n")
                scrollLogs.post { scrollLogs.fullScroll(View.FOCUS_DOWN) }
            }, delay)
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
    }
}
