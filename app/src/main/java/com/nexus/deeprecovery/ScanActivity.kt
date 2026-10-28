package com.nexus.deeprecovery

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_MEDIA = 0
        const val MODE_SD = 1
        const val MODE_DEEP = 2
    }

    private var mode = MODE_MEDIA
    private var allItems: List<VideoItem> = emptyList()
    private var scanJob: Job? = null
    private var copyJob: Job? = null
    private var sortMode = 0
    private var scanFinished = false
    private var pendingDeep = false
    private var pendingCopy: VideoItem? = null
    private var baseStatus = ""

    private lateinit var tvStatus: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText
    private lateinit var chipsType: ChipGroup
    private lateinit var chipRecent: Chip
    private lateinit var chipLarge: Chip
    private lateinit var btnStart: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSort: MaterialButton
    private lateinit var progress: LinearProgressIndicator
    private lateinit var adapter: VideoAdapter

    private val readPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res.isNotEmpty() && res.values.all { it }) {
                if (mode == MODE_DEEP) startDeepScan() else startMediaScan()
            } else {
                showPermDenied()
            }
        }

    private val writePermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            val item = pendingCopy
            pendingCopy = null
            if (ok && item != null) doCopy(item) else showPermDenied()
        }

    private val treeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                }
                startSdScan(uri)
            }
        }

    private val trashLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
            if (r.resultCode == RESULT_OK) {
                toast(getString(R.string.restore_done))
                if (mode == MODE_MEDIA) startMediaScan()
            } else {
                toast(getString(R.string.restore_failed))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        mode = intent.getIntExtra(EXTRA_MODE, MODE_MEDIA)

        val tvTitle: TextView = findViewById(R.id.tv_title)
        val tvNote: TextView = findViewById(R.id.tv_note)
        tvStatus = findViewById(R.id.tv_status)
        tvEmpty = findViewById(R.id.tv_empty)
        etSearch = findViewById(R.id.et_search)
        chipsType = findViewById(R.id.chips_type)
        chipRecent = findViewById(R.id.chip_recent)
        chipLarge = findViewById(R.id.chip_large)
        btnStart = findViewById(R.id.btn_start)
        btnCancel = findViewById(R.id.btn_cancel)
        btnSort = findViewById(R.id.btn_sort)
        progress = findViewById(R.id.progress)
        val btnPc: MaterialButton = findViewById(R.id.btn_pc_guide)
        val recycler: RecyclerView = findViewById(R.id.recycler)

        when (mode) {
            MODE_DEEP -> {
                tvTitle.setText(R.string.deep_title)
                tvNote.setText(R.string.deep_note)
                btnStart.setText(R.string.btn_start_deep)
                btnStart.setOnClickListener { requestDeepAndScan() }
                btnPc.visibility = View.VISIBLE
            }
            MODE_SD -> {
                tvTitle.setText(R.string.scan_sd_title)
                tvNote.setText(R.string.scan_sd_note)
                btnStart.setText(R.string.btn_choose_folder)
                btnStart.setOnClickListener { treeLauncher.launch(null) }
                btnPc.visibility = View.VISIBLE
            }
            else -> {
                tvTitle.setText(R.string.scan_media_title)
                tvNote.setText(R.string.scan_media_note)
                btnStart.setText(R.string.btn_start_media)
                btnStart.setOnClickListener { requestMediaAndScan() }
            }
        }

        tvNote.setOnClickListener {
            tvNote.maxLines = if (tvNote.maxLines == 3) Int.MAX_VALUE else 3
        }
        btnPc.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pc_guide_title)
                .setMessage(R.string.pc_guide_body)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        btnCancel.setOnClickListener { scanJob?.cancel() }

        adapter = VideoAdapter(this, lifecycleScope) { onItemClick(it) }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        etSearch.doAfterTextChanged { applyFilters() }
        chipsType.setOnCheckedStateChangeListener { _, _ -> applyFilters() }
        chipRecent.setOnCheckedChangeListener { _, _ -> applyFilters() }
        chipLarge.setOnCheckedChangeListener { _, _ -> applyFilters() }
        btnSort.setOnClickListener {
            sortMode = (sortMode + 1) % 3
            btnSort.setText(
                when (sortMode) {
                    0 -> R.string.sort_date
                    1 -> R.string.sort_size
                    else -> R.string.sort_name
                }
            )
            applyFilters()
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingDeep && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingDeep = false
            if (Environment.isExternalStorageManager()) startDeepScan() else showPermDenied()
        }
    }

    // ---------- permissions ----------

    private fun readPerms(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun hasAll(perms: Array<String>) =
        perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

    private fun requestMediaAndScan() {
        val perms = readPerms()
        if (hasAll(perms)) startMediaScan() else readPermLauncher.launch(perms)
    }

    private fun requestDeepAndScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                startDeepScan()
                return
            }
            pendingDeep = true
            toast(getString(R.string.deep_perm_needed))
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: ActivityNotFoundException) {
                    pendingDeep = false
                    showPermDenied()
                }
            }
        } else {
            val perms = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (hasAll(perms)) startDeepScan() else readPermLauncher.launch(perms)
        }
    }

    private fun showPermDenied() {
        MaterialAlertDialogBuilder(this)
            .setMessage(if (mode == MODE_DEEP) R.string.deep_perm_needed else R.string.perm_denied)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- scanning ----------

    private fun startMediaScan() = runScan { _ ->
        val list = withContext(Dispatchers.IO) { MediaStoreScanner.scan(this@ScanActivity) }
        ScanResult(list, list.size)
    }

    private fun startSdScan(tree: Uri) = runScan { report ->
        SafScanner(this@ScanActivity).scan(tree, report)
    }

    private fun startDeepScan() = runScan { report ->
        DeepFileScanner(this@ScanActivity).scan(report)
    }

    private fun runScan(block: suspend (report: (ScanProgress) -> Unit) -> ScanResult) {
        scanJob?.cancel()
        scanFinished = false
        allItems = emptyList()
        applyFilters()
        setScanning(true)
        tvStatus.text = getString(R.string.status_scanning, 0, 0)
        scanJob = lifecycleScope.launch {
            try {
                val result = block { p ->
                    tvStatus.post {
                        if (scanJob?.isActive == true) {
                            tvStatus.text = getString(R.string.status_scanning, p.scanned, p.found)
                        }
                    }
                }
                allItems = result.items
                scanFinished = true
                baseStatus = getString(R.string.status_done, result.scanned, result.items.size)
                applyFilters()
            } catch (e: CancellationException) {
                baseStatus = getString(R.string.status_cancelled)
                tvStatus.text = baseStatus
                throw e
            } catch (e: Exception) {
                baseStatus = getString(R.string.status_error, e.message ?: e.javaClass.simpleName)
                tvStatus.text = baseStatus
            } finally {
                setScanning(false)
            }
        }
    }

    private fun setScanning(on: Boolean) {
        progress.visibility = if (on) View.VISIBLE else View.GONE
        btnCancel.visibility = if (on) View.VISIBLE else View.GONE
        btnStart.isEnabled = !on
    }

    private fun applyFilters() {
        val q = etSearch.text?.toString()?.trim()?.lowercase() ?: ""
        val type = chipsType.checkedChipId
        val now = System.currentTimeMillis()
        val month = 30L * 24 * 3600 * 1000
        val big = 50L * 1024 * 1024
        val filtered = allItems.filter { item ->
            val typeOk = when (type) {
                R.id.chip_trash -> item.kind == ItemKind.TRASHED
                R.id.chip_hidden -> item.kind == ItemKind.HIDDEN || item.kind == ItemKind.SIGNATURE
                R.id.chip_thumbs -> item.kind == ItemKind.THUMBNAIL
                else -> true
            }
            typeOk &&
                (!chipRecent.isChecked || now - item.dateMillis <= month) &&
                (!chipLarge.isChecked || item.size >= big) &&
                (q.isEmpty() || item.name.lowercase().contains(q) || item.location.lowercase().contains(q))
        }
        val sorted = when (sortMode) {
            0 -> filtered.sortedByDescending { it.dateMillis }
            1 -> filtered.sortedByDescending { it.size }
            else -> filtered.sortedBy { it.name.lowercase() }
        }
        adapter.submit(sorted)
        tvEmpty.visibility = if (scanFinished && sorted.isEmpty()) View.VISIBLE else View.GONE
        if (scanFinished) {
            tvStatus.text = baseStatus + "  " + getString(R.string.status_shown, sorted.size, allItems.size)
        }
    }

    // ---------- actions ----------

    private fun onItemClick(item: VideoItem) {
        val labels = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        labels.add(getString(R.string.action_preview)); actions.add { preview(item) }
        labels.add(getString(R.string.action_copy)); actions.add { startCopy(item) }
        if (item.kind == ItemKind.TRASHED && item.fromMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            labels.add(getString(R.string.action_restore)); actions.add { restore(item) }
        }
        labels.add(getString(R.string.action_details)); actions.add { details(item) }

        MaterialAlertDialogBuilder(this)
            .setTitle(item.name)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun preview(item: VideoItem) {
        try {
            val viewUri = if (item.uri.scheme == "file") {
                FileProvider.getUriForFile(this, "$packageName.files", File(item.uri.path ?: ""))
            } else item.uri
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(viewUri, item.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            toast(getString(R.string.preview_failed))
        }
    }

    private fun startCopy(item: VideoItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingCopy = item
            writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        doCopy(item)
    }

    private fun doCopy(item: VideoItem) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setMessage(R.string.copy_running)
            .setCancelable(false)
            .setNegativeButton(R.string.btn_cancel) { _, _ -> copyJob?.cancel() }
            .show()
        copyJob = lifecycleScope.launch {
            try {
                val where = FileOps.copyToRecovery(this@ScanActivity, item)
                dialog.dismiss()
                MaterialAlertDialogBuilder(this@ScanActivity)
                    .setMessage(getString(R.string.copy_done, where))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (e: CancellationException) {
                dialog.dismiss()
                toast(getString(R.string.copy_cancelled))
                throw e
            } catch (e: Exception) {
                dialog.dismiss()
                toast(getString(R.string.copy_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    private fun restore(item: VideoItem) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val pi = MediaStore.createTrashRequest(contentResolver, listOf(item.uri), false)
            trashLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } catch (e: Exception) {
            toast(getString(R.string.restore_failed))
        }
    }

    private fun details(item: VideoItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_details)
            .setMessage(
                getString(
                    R.string.details_body,
                    item.name,
                    Format.size(item.size),
                    Format.date(item.dateMillis),
                    item.location,
                    getString(VideoAdapter.kindLabel(item.kind)) + " · " + item.mime
                )
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
