package com.jay.onlinetvradio.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * A layout that automatically applies system bar insets as padding.
 * This is similar to SafeAreaView in React Native.
 */
class SafeAreaLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    init {
        // Enforce that this view will handle insets
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply the insets as padding to the view
            view.updatePadding(
                left = insets.left,
                top = insets.top,
                right = insets.right,
                bottom = insets.bottom
            )

            // Return consumers the insets they need (usually none if we consumed them)
            // But we return the original value so children can also receive them if needed
            windowInsets
        }
    }
}
