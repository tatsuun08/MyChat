package com.tman.mychat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.tman.mychat.databinding.FragmentRoomListBinding
import kotlinx.coroutines.launch
import java.util.zip.Inflater

class RoomListFragment : Fragment(R.layout.fragment_room_list) {
    private var _binding: FragmentRoomListBinding? = null
    private val binding get() = _binding!!

    private lateinit var roomAdapter: RoomListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //DB
        val db = AppDatabase.getDatabase(requireContext())
        val roomDao = db.roomDao()

    // 1. RecyclerView と Adapter のセットアップ
        roomAdapter = RoomListAdapter(emptyList())
        binding.roomRecyclerView.apply {
            adapter = roomAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // 2. 画面が開いたときに、DBから部屋一覧を読み込んで表示
        loadRooms(roomDao)

        //ルーム作成処理
        binding.createRoom.setOnClickListener {
            val editText = EditText(requireContext())
            editText.hint = "例：雑談部屋"

            AlertDialog.Builder(requireContext())
                .setTitle("新しい部屋の作成")
                .setView(editText) // さっき作った入力欄をセット
                .setPositiveButton("作成") { dialog, which ->
                    // 「作成」が押されたときの処理
                    val roomName = editText.text.toString()
                    if (roomName.isNotEmpty()) {
                        //DBに保存
                        lifecycleScope.launch {
                            val newRooms = mutableListOf(
                                RoomEntity(name = roomName, icon = "", roomId=0)
                            )
                            roomDao.upsertRoom(newRooms)
                            loadRooms(roomDao)
                        }
                    }
                }
                .setNegativeButton("キャンセル", null) // 何もせずに閉じる
                .show()

        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // メモリリーク防止
    }

    //データベースからルームリストをロード，アダプターに渡す
    private fun loadRooms(roomDao: RoomDao) {
        lifecycleScope.launch {
            val rooms = roomDao.getRooms()
            roomAdapter.updateData(rooms)
        }
    }
}