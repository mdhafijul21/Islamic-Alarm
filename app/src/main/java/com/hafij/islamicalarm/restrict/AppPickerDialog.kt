package com.hafij.islamicalarm.restrict

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hafij.islamicalarm.R
import java.util.Locale

/** Multi-select list of installed apps (browsers first) with a search box. */
object AppPickerDialog {

    class AppEntry(
        val pkg: String,
        val label: String,
        val icon: Drawable?,
        val isBrowser: Boolean
    )

    fun show(activity: Activity, initiallySelected: Set<String>, onDone: (Set<String>) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_picker, null)
        val etSearch = view.findViewById<EditText>(R.id.etAppSearch)
        val listView = view.findViewById<ListView>(R.id.lvApps)
        val tvLoading = view.findViewById<TextView>(R.id.tvAppsLoading)

        val selected = initiallySelected.toMutableSet()
        var allApps: List<AppEntry> = emptyList()
        val shown = mutableListOf<AppEntry>()

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = shown.size
            override fun getItem(position: Int): Any = shown[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = convertView ?: LayoutInflater.from(activity)
                    .inflate(R.layout.item_app_pick, parent, false)
                val entry = shown[position]
                row.findViewById<ImageView>(R.id.ivAppIcon).setImageDrawable(entry.icon)
                row.findViewById<TextView>(R.id.tvAppName).text =
                    (if (entry.isBrowser) "🌐 " else "") + entry.label
                row.findViewById<CheckBox>(R.id.cbAppPick).isChecked = entry.pkg in selected
                return row
            }
        }
        listView.adapter = adapter

        fun applyFilter(query: String) {
            val q = query.trim().lowercase(Locale.getDefault())
            shown.clear()
            for (app in allApps) {
                if (q.isEmpty() || app.label.lowercase(Locale.getDefault()).contains(q)) {
                    shown.add(app)
                }
            }
            adapter.notifyDataSetChanged()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val entry = shown[position]
            if (entry.pkg in selected) selected.remove(entry.pkg) else selected.add(entry.pkg)
            adapter.notifyDataSetChanged()
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s?.toString() ?: "")
            }
        })

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle("অ্যাপ ও ব্রাউজার বাছাই")
            .setView(view)
            .setPositiveButton("ঠিক আছে") { _, _ -> onDone(selected.toSet()) }
            .setNegativeButton("বাতিল", null)
            .create()
        dialog.show()

        Thread {
            val apps = loadApps(activity)
            activity.runOnUiThread {
                if (!activity.isFinishing && dialog.isShowing) {
                    allApps = apps
                    tvLoading.visibility = View.GONE
                    applyFilter(etSearch.text.toString())
                }
            }
        }.start()
    }

    private fun loadApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)

        val browserPackages = HashSet<String>()
        try {
            for (ri in pm.queryIntentActivities(browserIntent, 0)) {
                browserPackages.add(ri.activityInfo.packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val map = LinkedHashMap<String, AppEntry>()
        try {
            for (ri in pm.queryIntentActivities(launcherIntent, 0)) {
                val p = ri.activityInfo.packageName
                if (p == ctx.packageName || map.containsKey(p)) continue
                map[p] = AppEntry(p, ri.loadLabel(pm).toString(), ri.loadIcon(pm), p in browserPackages)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return map.values.sortedWith(
            compareByDescending<AppEntry> { it.isBrowser }.thenBy { it.label.lowercase(Locale.getDefault()) }
        )
    }
}
