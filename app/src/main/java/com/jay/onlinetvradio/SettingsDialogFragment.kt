package com.jay.onlinetvradio



import android.app.Dialog
import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.DialogFragment
/*
class SettingsDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*val dialog = Dialog(requireContext(), R.style.Theme_Radio_Setting)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.fragment_settings_dialog)*/
        //AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        //setStyle(STYLE_NORMAL, R.drawable.border_background_info)
        // or your custom style for transparent / rounded background
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.Theme_Radio_Setting)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.fragment_settings_dialog)
        return super.onCreateDialog(savedInstanceState)

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
*/
class SettingsDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Use your custom style (with rounded background + dark mode)
        val dialog = Dialog(requireContext(), R.style.Theme_Radio_Setting)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Inflate the root view
        val rootView = requireActivity().layoutInflater.inflate(
            R.layout.fragment_settings_dialog, null
        )
        dialog.setContentView(rootView)

        // Replace container with SettingsFragment
        childFragmentManager.beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commitNow()

        // Optional: make background transparent + apply radius
        dialog.window?.setBackgroundDrawableResource(R.drawable.border_background_info)

        return dialog
    }
}
