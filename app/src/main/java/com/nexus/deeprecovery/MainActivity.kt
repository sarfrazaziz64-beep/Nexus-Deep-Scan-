package com.nexus.deeprecovery

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupCard(R.id.card_deep, "🔍", R.string.card_deep_title, R.string.card_deep_desc) {
            openScan(ScanActivity.MODE_DEEP)
        }
        setupCard(R.id.card_media, "🗑", R.string.card_media_title, R.string.card_media_desc) {
            openScan(ScanActivity.MODE_MEDIA)
        }
        setupCard(R.id.card_sd, "💾", R.string.card_sd_title, R.string.card_sd_desc) {
            openScan(ScanActivity.MODE_SD)
        }
        setupCard(R.id.card_reset, "🛡", R.string.card_reset_title, R.string.card_reset_desc) {
            startActivity(Intent(this, FactoryResetActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btn_lang).setOnClickListener {
            val current = AppCompatDelegate.getApplicationLocales()
            val isUrdu = if (current.isEmpty()) {
                Locale.getDefault().language == "ur"
            } else {
                current.toLanguageTags().startsWith("ur")
            }
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(if (isUrdu) "en" else "ur")
            )
        }
    }

    private fun openScan(mode: Int) {
        startActivity(Intent(this, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_MODE, mode))
    }

    private fun setupCard(id: Int, icon: String, title: Int, desc: Int, onClick: () -> Unit) {
        val card = findViewById<View>(id)
        card.findViewById<TextView>(R.id.card_icon).text = icon
        card.findViewById<TextView>(R.id.card_title).setText(title)
        card.findViewById<TextView>(R.id.card_desc).setText(desc)
        card.setOnClickListener { onClick() }
    }
}
