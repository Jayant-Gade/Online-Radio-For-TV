package com.jay.onlinetvradio

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import org.json.JSONArray
import org.json.JSONObject

class ServerChangeFragment(
    private val stationKey: String,        // e.g., "Vividh Bharati-1+r2"
    private val parentRow: LinearLayout    // the row where button is
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val prefs = context.getSharedPreferences("stations_prefs", Context.MODE_PRIVATE)
        val view = requireActivity().layoutInflater.inflate(R.layout.dialog_server_change, null)
        val container = view.findViewById<LinearLayout>(R.id.serverListContainer)

        val serversJson = prefs.getString("${stationKey.substringBefore("-")}_servers", null)

        // parse as list of Triple<url, language, state>
        val servers: List<Triple<String, String, String>> = if (serversJson != null) {
            JSONArray(serversJson).let { arr ->
                List(arr.length()) { idx ->
                    val obj = arr.getJSONObject(idx)
                    Triple(
                        obj.optString("url"),
                        obj.optString("language"),
                        obj.optString("state")
                    )
                }
            }
        } else {
            emptyList()
        }

        // show list of server states in dialog
        val serverStates = servers.map { it.third }.toTypedArray()

        return AlertDialog.Builder(context, R.style.Theme_Radio_Server)
            .setTitle("Select Server for ${stationKey.substringBefore("+")}")
            .setItems(serverStates) { _, which ->
                val selectedServer = servers[which]
                val newLink = selectedServer.first
                val newLanguage = selectedServer.second
                val state = selectedServer.third

                // update view's meta (update tags / languagecodes)
                for (i in 0 until parentRow.childCount) {
                    val view = parentRow.getChildAt(i)
                    val meta = view.tag as? JSONObject
                    if (meta?.optString("name") == stationKey.substringBefore("+")) {
                        meta.put("server", newLink)
                        meta.put("languagecodes", newLanguage)
                        meta.put("state", state)
                        view.tag = meta
                    }
                }

                // save new link + language + state persistently
                saveServerChange(context, stationKey, newLink, newLanguage, state)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    // save selected server persistently (link + language + state)
    private fun saveServerChange(
        context: Context,
        stationKey: String,
        newLink: String,
        language: String,
        state: String
    ) {
        Log.d("MyApp", "saving prefs")
        val prefs = context.getSharedPreferences("stations_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putString("${stationKey}_selected_link", newLink)
            putString("${stationKey}_selected_language", language)
            putString("${stationKey}_selected_state", state)
        }
    }
}
