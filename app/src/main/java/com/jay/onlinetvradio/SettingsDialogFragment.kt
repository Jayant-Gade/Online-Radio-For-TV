package com.jay.onlinetvradio



import android.os.Bundle
import androidx.fragment.app.DialogFragment

class SettingsDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.drawable.border_background_info)
        // or your custom style for transparent / rounded background
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        // Inflate a container layout that hosts the SettingsFragment
        val view = inflater.inflate(R.layout.fragment_settings_dialog, container, false)
        childFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
        return view
    }
}
