package com.nexus.deeprecovery

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class FactoryResetActivity : AppCompatActivity() {

    private data class Source(val key: String, val title: Int, val desc: Int, val open: (() -> Unit)?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_factory_reset)

        findViewById<TextView>(R.id.tv_device).text =
            getString(R.string.reset_device, Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE)
        findViewById<TextView>(R.id.tv_verdict).setText(verdict())

        val prefs = getSharedPreferences("checklist", Context.MODE_PRIVATE)
        val sources = listOf(
            Source("gphotos", R.string.src_gphotos, R.string.src_gphotos_desc) { web("https://photos.google.com/trash") },
            Source("takeout", R.string.src_takeout, R.string.src_takeout_desc) { web("https://takeout.google.com") },
            Source("drive", R.string.src_drive, R.string.src_drive_desc) { web("https://drive.google.com/drive/backups") },
            Source("whatsapp", R.string.src_whatsapp, R.string.src_whatsapp_desc) { openApp("com.whatsapp") },
            Source("meta", R.string.src_meta, R.string.src_meta_desc) {
                web("https://accountscenter.facebook.com/info_and_permissions/dyi")
            },
            Source("youtube", R.string.src_youtube, R.string.src_youtube_desc) { web("https://studio.youtube.com") },
            Source("cloud", R.string.src_cloud, R.string.src_cloud_desc) { web("https://onedrive.live.com") },
            Source("offline", R.string.src_offline, R.string.src_offline_desc) {
                startActivity(
                    Intent(this, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_MODE, ScanActivity.MODE_SD)
                )
            }
        )

        val container = findViewById<LinearLayout>(R.id.ll_sources)
        val inflater = LayoutInflater.from(this)
        for (s in sources) {
            val row = inflater.inflate(R.layout.item_source, container, false)
            row.findViewById<TextView>(R.id.tv_src_title).setText(s.title)
            row.findViewById<TextView>(R.id.tv_src_desc).setText(s.desc)
            val cb = row.findViewById<CheckBox>(R.id.cb_done)
            cb.isChecked = prefs.getBoolean(s.key, false)
            cb.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(s.key, checked).apply() }
            val btn = row.findViewById<MaterialButton>(R.id.btn_src_open)
            val action = s.open
            if (action == null) btn.visibility = View.GONE else btn.setOnClickListener { action() }
            container.addView(row)
        }
    }

    private fun verdict(): Int {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val status = dpm?.storageEncryptionStatus ?: -1
        return when (status) {
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY,
            DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> R.string.reset_enc_active
            DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE,
            DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) R.string.reset_enc_unknown
                else R.string.reset_enc_inactive
            else -> R.string.reset_enc_unknown
        }
    }

    private fun web(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }

    private fun openApp(pkg: String) {
        val launch = packageManager.getLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        if (launch != null) startActivity(launch)
        else Toast.makeText(this, R.string.preview_failed, Toast.LENGTH_SHORT).show()
    }
}
