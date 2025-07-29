package com.jay.onlinetvradio

import android.app.Dialog
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread


class SearchDialogFragment(private val onSelect: (station: JSONObject) -> Unit) : DialogFragment() {

    private val okHttpClient = OkHttpClient()
    private var apiServer: String? = null
    private lateinit var adapter: StationAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        apiServer = (activity as? MainActivity)?.apiServer

        val dialog = Dialog(requireContext(), R.style.Theme_Radio_Search)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_search)
        val searchButton = dialog.findViewById<Button>(R.id.searchButton)
        val input = dialog.findViewById<EditText>(R.id.searchInput)
        val recycler = dialog.findViewById<RecyclerView>(R.id.recyclerView)
        adapter = StationAdapter { station ->
            onSelect(station)
            dismiss()
        }
        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        fun doSearch() {
            val query = input.text.toString()
            if (query.isNotBlank()) {
                searchStations(query) {
                    recycler.post {
                        recycler.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            }
        }
        input.setOnEditorActionListener { _, _, _ ->
            doSearch()
            true
        }

        searchButton.setOnClickListener {
            doSearch()
        }

        return dialog

    }

    private fun searchStations(query: String, onLoaded: () -> Unit) {
        thread {
            try {
                val server = apiServer ?: return@thread
                val url = "https://$server/json/stations/search?limit=10&name=$query&hidebroken=true"
                val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()
                val body = response.body?.string()
                if (!response.isSuccessful || body.isNullOrEmpty()) return@thread
                val arr = JSONArray(body)
                val list = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) list.add(arr.getJSONObject(i))
                requireActivity().runOnUiThread {
                    adapter.setData(list)
                    onLoaded()
                }

            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        fun newInstance(onSelect: (station: JSONObject) -> Unit) = SearchDialogFragment(onSelect)
    }
}
