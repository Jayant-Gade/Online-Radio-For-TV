package com.example.radio

import android.app.Dialog
import android.os.Bundle
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.bumptech.glide.Glide
import org.json.JSONObject

class InfoDialogFragment(private val station: JSONObject) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_info)

        val icon = dialog.findViewById<ImageView>(R.id.infoIcon)
        val text = dialog.findViewById<TextView>(R.id.infoText)

        val favicon = station.optString("favicon")
        if (favicon.isNotEmpty()) Glide.with(this).load(favicon).into(icon)
        text.text = """
            Name: ${station.optString("name")}
            Country: ${station.optString("country")}
            Tags: ${station.optString("tags")}
            Language: ${station.optString("language")}
        """.trimIndent()

        return dialog
    }

    companion object {
        fun newInstance(station: JSONObject) = InfoDialogFragment(station)
    }
}
