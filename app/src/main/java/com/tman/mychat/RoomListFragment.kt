package com.tman.mychat

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

        val sharedPref = requireActivity().getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE)
        //DB
        val db = AppDatabase.getDatabase(requireContext())
        val roomDao = db.roomDao()

        val myUserId = sharedPref.getInt("myUserId", -1)

        roomAdapter = RoomListAdapter(emptyList())

        // 1. UIを更新する関数を呼ぶ
        updateUserUI(sharedPref)

        login(sharedPref, db)
        Log.d("ChatApp", "【現在のID確認】 myUserId = $myUserId")

        syncRooms()


//        // --- ★ここからテスト通信のコード ---
//        lifecycleScope.launch {
//            testServerConnection()
//        }

        //RecyclerView と Adapter のセットアップ
        binding.roomRecyclerView.apply {
            adapter = roomAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        //画面が開いたときに、DBから部屋一覧を読み込んで表示
        loadRooms(roomDao)

        //ログアウトボタン
        binding.logoutButton.setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.clearAllTables() // これだけで rooms も messages も全て空になります
                }
                // SharedPreferences の中身を空にする
                sharedPref.edit().clear().apply()

                // 再度ログインダイアログを表示（またはログイン画面へ遷移）
                showLoginDialog(sharedPref)

                // UIをリセット（名前を消すなど）
                updateUserUI(sharedPref)

                // 部屋リストも一旦空にする
                roomAdapter.updateData(emptyList())
                loadRooms(roomDao)
            }
        }

        //ルーム作成ボタン
        binding.createRoom.setOnClickListener {
            val editText = EditText(requireContext())
            editText.hint = "例：雑談部屋"

            AlertDialog.Builder(requireContext())
                .setTitle("新しい部屋の作成")
                .setView(editText) // さっき作った入力欄をセット
                .setPositiveButton("作成") { _, _ ->
                    val roomName = editText.text.toString()
                    if (roomName.isNotEmpty()) {
                        // lifecycleScope.launch は外してOK（createRoomの中でやってるから）
                        createRoom(roomName)
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

    //RoomEntityの同期
    private fun syncRooms() {
        // 1. 引き出しから自分のIDを取り出す
        val sharedPref = requireActivity().getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE)
        val myUserId = sharedPref.getInt("myUserId", -1)

        // ログインしていない場合は同期しない
        if (myUserId == -1) return

        lifecycleScope.launch {
            try {
                val remoteRooms = RetrofitClient.api.getRooms(myUserId)
                val roomEntities = remoteRooms.map { response ->
                    RoomEntity(
                        roomId = response.id,
                        name = response.name,
                        icon = "" // サーバー側にアイコンがないので一旦空文字
                    )
                }


                val database = AppDatabase.getDatabase(requireContext())
                database.roomDao().upsertRoom(roomEntities)

                loadRooms(database.roomDao())

                val localRooms = database.roomDao().getRooms()

                Log.d("ChatApp", "同期成功！ ${localRooms.size}件の部屋を読み込みました")

            } catch (e: Exception) {
                // 通信エラー（サーバーが落ちている、Wi-Fiがない等）の時の処理
                Log.e("ChatApp", "通信エラー: ${e.message}")

            }
        }
    }


    private fun createRoom(roomName: String) {
        val sharedPref = requireActivity().getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE)
        val myUserId = sharedPref.getInt("myUserId", -1)

        lifecycleScope.launch {
            try {
                val request = RoomRequest(name = roomName)
                val response = RetrofitClient.api.createRoom(request)

                val database = AppDatabase.getDatabase(requireContext())
                val newRoomEntity = RoomEntity(
                    roomId = response.id,
                    name = response.name,
                    icon = ""
                )


                //RoomEntityをローカルデータベースに保存
                database.roomDao().upsertRoom(listOf(newRoomEntity))
                RetrofitClient.api.createRoomUser(RoomUserRequest(response.id, myUserId))

                // ★重要：DBを更新したら、画面のリストを再読み込みする！
                loadRooms(database.roomDao())

                Log.d("ChatApp", "作成成功: ${response.name}")

            } catch (e: Exception) {
                Log.e("ChatApp", "作成失敗: ${e.message}")
            }
        }
    }

    // ログイン用のダイアログを表示する関数
    private fun showLoginDialog(sharedPref: SharedPreferences) {
        val editText = EditText(requireContext())
        editText.hint = "あなたの名前を入力してください"

        AlertDialog.Builder(requireContext())
            .setTitle("ユーザー登録")
            .setView(editText)
            .setCancelable(false) // 戻るボタンで閉じられないようにする
            .setPositiveButton("決定") { _, _ ->
                val userName = editText.text.toString()
                if (userName.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            // 1. Goサーバーに名前を送信！
                            val response = RetrofitClient.api.loginUser(UserRequest(name = userName))

                            // 2. サーバーから返ってきた自分のIDを SharedPreferences に永久保存！
                            sharedPref.edit().putInt("myUserId", response.id).apply()
                            sharedPref.edit().putString("myUserName", response.name).apply()

                            Log.d("ChatApp", "ログイン成功！ID: ${response.id}")

                            // 3. ログインできたので、部屋の読み込みをスタート
                            syncRooms()
                            val db = AppDatabase.getDatabase(requireContext())
                            updateUserUI(sharedPref)

                        } catch (e: Exception) {
                            Log.e("ChatApp", "ログイン失敗", e)
                        }
                    }
                }
            }
            .show()
    }

    // 現在のログイン状態に合わせてUIを書き換える関数
    private fun updateUserUI(sharedPref: SharedPreferences) {
        val userName = sharedPref.getString("myUserName", null)

        if (userName != null) {
            binding.currentUserName.text = "${userName}さん"
            binding.userIcon.text = userName.take(1).uppercase() // 名前の1文字目
            binding.logoutButton.visibility = View.VISIBLE
        } else {
            binding.currentUserName.text = "未ログイン"
            binding.userIcon.text = "?"
            binding.logoutButton.visibility = View.GONE
        }
    }

    private fun login(sharedPref: SharedPreferences, db: AppDatabase) {
        val myUserId = sharedPref.getInt("myUserId", -1)

        // ★ ここに足跡ログを追加！
        Log.d("ChatApp", "【現在のID確認】 myUserId = $myUserId")

        if (myUserId == -1) {
            showLoginDialog(sharedPref)
        } else {
            Log.d("ChatApp", "ログイン済みです！私のID: $myUserId")
            syncRooms()
            loadRooms(db.roomDao())
        }
    }
}