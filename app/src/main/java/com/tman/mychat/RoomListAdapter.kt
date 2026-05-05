package com.tman.mychat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView

class RoomListAdapter(private var roomList: List<RoomEntity>) :
    RecyclerView.Adapter<RoomListAdapter.RoomViewHolder>() {

    class RoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val roomNameText: TextView = view.findViewById(R.id.roomNameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room, parent, false)
        return RoomViewHolder(view)
    }

    override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
        val room = roomList[position]
        holder.roomNameText.text = room.name

        holder.itemView.setOnClickListener { view ->
            val action = RoomListFragmentDirections.actionListToRoom(room.roomId, room.name)

            view.findNavController().navigate(action)
        }
    }
    override fun getItemCount(): Int {
        return roomList.size
    }

    // データを更新するためのメソッド
    fun updateData(newRooms: List<RoomEntity>) {
        roomList = newRooms
        notifyDataSetChanged()
    }
}