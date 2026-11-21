package com.example.alarmy

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Calendar

class AlarmAdapter(
    private var alarms: List<Alarm>,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onDelete: (Alarm) -> Unit,
    private val onEdit: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvAlarmTime)
        val tvLabel: TextView = view.findViewById(R.id.tvAlarmLabel)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteAlarm)
        val switchEnabled: SwitchMaterial = view.findViewById(R.id.switchAlarmEnabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        val context = holder.itemView.context

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
        }
        val timeFormat = DateFormat.getTimeFormat(context)
        holder.tvTime.text = timeFormat.format(calendar.time)
        holder.tvLabel.text = alarm.label
        holder.switchEnabled.isChecked = alarm.isEnabled

        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(alarm, isChecked)
        }

        holder.btnDelete.setOnClickListener {
            onDelete(alarm)
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
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = alarms.size
            override fun getNewListSize(): Int = newAlarms.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return alarms[oldItemPosition].id == newAlarms[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return alarms[oldItemPosition] == newAlarms[newItemPosition]
            }
        }
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        alarms = newAlarms
        diffResult.dispatchUpdatesTo(this)
    }
}
