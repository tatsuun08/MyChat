package com.tman.mychat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.tman.mychat.databinding.FragmentRoomListBinding

class RoomListFragment : Fragment(R.layout.fragment_room_list) {
    private var _binding: FragmentRoomListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // XMLに配置したボタンを探す
        val btnRoom1 = view.findViewById<Button>(R.id.btnGoToRoom1)

        btnRoom1.setOnClickListener {
            // Safe Args を使った遷移（roomId = 1 を渡す）
            val action = RoomListFragmentDirections.actionListToRoom(1)
            findNavController().navigate(action)
        }
    }
}