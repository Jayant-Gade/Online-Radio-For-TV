package com.jay.onlinetvradio

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.json.JSONObject

class StationAdapter(private val onSelect: (station: JSONObject) -> Unit) :
    RecyclerView.Adapter<StationAdapter.ViewHolder>() {

    private val stations = mutableListOf<JSONObject>()

    fun setData(newData: List<JSONObject>) {
        stations.clear()
        stations.addAll(newData)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = stations.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        view.isFocusable = true
        return ViewHolder(view)
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val station = stations[position]
        holder.name.text = station.optString("name")
        val iconUrl = station.optString("favicon")
        if (iconUrl.isNotEmpty())
            Glide.with(holder.itemView).load(iconUrl).into(holder.icon)
        else
            holder.icon.setImageResource(R.mipmap.ic_launcher)

        holder.itemView.setOnClickListener { onSelect(station) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.stationName)
        val icon: ImageView = view.findViewById(R.id.stationIcon)
    }
}
