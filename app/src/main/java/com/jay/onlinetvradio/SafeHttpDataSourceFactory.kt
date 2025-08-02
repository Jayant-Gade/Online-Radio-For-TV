package com.jay.onlinetvradio

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.preference.PreferenceManager
import java.io.IOException

@UnstableApi
class SafeHttpDataSourceFactory(
    private val context: Context,
    private val defaultFactory: DataSource.Factory,
    private val onHttpBlocked: () -> Unit
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val enableHttp = prefs.getBoolean("enable_http", false)
        val realSource = defaultFactory.createDataSource()
        return object : DataSource by realSource {
            override fun open(dataSpec: DataSpec): Long {
                if (!enableHttp && dataSpec.uri.scheme.equals("http", true)) {
                    onHttpBlocked()
                    throw IOException("HTTP traffic disabled by user") as Throwable
                }
                return realSource.open(dataSpec)
            }

            override fun getResponseHeaders(): Map<String, List<String>> {
                return realSource.responseHeaders
            }
        }
    }
}
