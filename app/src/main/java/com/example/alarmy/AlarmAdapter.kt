package com.example.alarmy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AlarmAdapter(
    private var alarms: List<Alarm>,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onDelete: (Alarm) -> Unit,
    private val onEdit: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvAlarmTime)
        val tvLabel: TextView = view.findViewById(R.id.tvAlarmLabel)
        val switchEnabled: Switch = view.findViewById(R.id.switchAlarmEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        val timeStr = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
        holder.tvTime.text = timeStr
        holder.tvLabel.text = alarm.label
        holder.switchEnabled.isChecked = alarm.isEnabled

        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(alarm, isChecked)
        }

        holder.itemView.setOnClickListener {
            onEdit(alarm)
        }

        holder.itemView.setOnLongClickListener {
            onDelete(alarm)
            true
        }
    }

    override fun getItemCount() = alarms.size

    fun updateAlarms(newAlarms: List<Alarm>) {
        alarms = newAlarms
        notifyDataSetChanged()
    }
}
