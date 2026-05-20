package com.tman.mychat

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast.makeText
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
import java.security.KeyPairGenerator
import java.security.KeyStore
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
        //SharedPreferenceの読み込み
        val sharedPref = requireActivity().getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE)
        //DB
        val db = AppDatabase.getDatabase(requireContext())
        val roomDao = db.roomDao()

        //ログイン処理 ユーザーIDが読み取れる場合はそのまま
        login(sharedPref, db)
        //ユーザーID読み取り
        val myUserId = sharedPref.getInt("myUserId", -1)
        Log.d("ChatApp", "【現在のID確認】 myUserId = $myUserId")

        //UIを更新する関数を呼ぶ　ユーザーIDの表示
        updateUserUI(sharedPref)

        //サーバーDBとローカルDBの同期
        syncRooms()

        //RecyclerView と Adapter のセットアップ
        roomAdapter = RoomListAdapter(emptyList())
        binding.roomRecyclerView.apply {
            adapter = roomAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        //画面が開いたときに、DBから部屋一覧を読み込んで表示
        loadRooms(roomDao)

        //ログアウトボタン
        setLogoutButton(sharedPref, db)
        //ルーム作成ボタン
        createRoomButton(sharedPref)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // メモリリーク防止
    }

    //ローカルデータベースからルームリストをロード，リサイクルビューの更新
    private fun loadRooms(roomDao: RoomDao) {
        lifecycleScope.launch {
            val rooms = roomDao.getRooms()
            roomAdapter.updateData(rooms)
        }
    }

    //ログアウトボタン処理
    private fun setLogoutButton(sharedPref: SharedPreferences, db: AppDatabase){
        binding.logoutButton.setOnClickListener {
            val roomDao = db.roomDao()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.clearAllTables() // これだけで rooms も messages も全て空になります
                }
                // SharedPreferences の中身を空にする
                sharedPref.edit().clear().apply()

                //秘密鍵の削除
                CryptoManager.deleteRsaKey("myChatKey")

                // 再度ログインダイアログを表示（またはログイン画面へ遷移）
                login(sharedPref, db)

                // UIをリセット（名前を消すなど）
                updateUserUI(sharedPref)

                // 部屋リストも一旦空にする
                roomAdapter.updateData(emptyList())
                loadRooms(roomDao)
            }
        }
    }

    //ルーム作成ボタン
    private fun createRoomButton(sharedPref: SharedPreferences){
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

    // 新規登録ダイアログ
    private fun showRegisterDialog(sharedPref: SharedPreferences) {
        val editText = EditText(requireContext())
        editText.hint = "新しいユーザー名を入力"

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("新規アカウント作成")
            .setView(editText)
            .setCancelable(false)
            // 💡 1. 決定ボタンの処理を一旦「null」にして、自動で閉じないようにする！
            .setPositiveButton("登録", null)
            .setNegativeButton("ログインはこちら") { _, _ -> showLoginDialog(sharedPref) }
            .create() // show() ではなく、まずは create() でインスタンス化

        // 💡 2. まずダイアログを画面に表示する
        dialog.show()

        // 💡 3. 表示された後に、登録ボタンのクリックイベントを手動で上書きする！
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val userName = editText.text.toString().trim()

            if (userName.isEmpty()) {
                editText.error = "名前を入力してください"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    // 鍵の生成と取得
                    CryptoManager.generateRSAKeyPairIfNeeded()
                    val myPublicKey = CryptoManager.getMyPublicKeyString() ?: ""

                    // サーバーへ新規登録リクエスト送信
                    RetrofitClient.api.registerUser(UserRequest(name = userName, publicKey = myPublicKey))

                    // ✨ 【成功時】ここで初めて手動でダイアログを閉じる！
                    dialog.dismiss()
                    makeText(requireContext(), "登録が完了しました！", android.widget.Toast.LENGTH_SHORT).show()

                    // そのままログインダイアログへ誘導
                    showLoginDialog(sharedPref)

                } catch (e: retrofit2.HttpException) {
                    //  サーバーからエラーが返ってきた場合
                    if (e.code() == 409) {
                        // 409 Conflict（名前の重複）
                        editText.error = "この名前はすでに使われています。別の名前を入力してください"

                        makeText(requireContext(), "別の名前で登録し直してください", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        // その他のサーバーエラー
                        makeText(requireContext(), "エラーが発生しました（コード: ${e.code()}）", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // ネットワークが繋がっていないなどの通信エラー
                    Log.e("ChatApp", "通信失敗", e)
                    makeText(requireContext(), "ネットワーク接続を確認してください", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ログイン用のダイアログを表示する関数
    private fun showLoginDialog(sharedPref: SharedPreferences) {
        val editText = EditText(requireContext())
        editText.hint = "あなたの名前を入力してください"

        AlertDialog.Builder(requireContext())
            .setTitle("ログイン")
            .setView(editText)
            .setCancelable(false) // 戻るボタンで閉じられないようにする
            .setPositiveButton("決定") { _, _ ->
                val userName = editText.text.toString()
                if (userName.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            //ログイン時にパブリックキーをデータベースに保存
                            val myPublicKey = CryptoManager.getMyPublicKeyString() ?: ""
                            // 1. Goサーバーに名前を送信！
                            val response = RetrofitClient.api.loginUser(LoginRequest(name = userName, myPublicKey))

                            // 2. サーバーから返ってきた自分のIDを SharedPreferences に永久保存！
                            sharedPref.edit().putInt("myUserId", response.userId).apply()
                            sharedPref.edit().putString("myUserName", response.userName).apply()

                            Log.d("ChatApp", "ログイン成功！ID: ${response.userId}")

                            // 3. ログインできたので、部屋の読み込みをスタート
                            syncRooms()
                            val db = AppDatabase.getDatabase(requireContext())
                            updateUserUI(sharedPref)

                        } catch (e: Exception) {
                            Log.e("ChatApp", "ログイン失敗", e)
                            showLoginDialog(sharedPref) //TODO　ログイン失敗時にユーザー名が見つかりませんを表示
                        }
                    }
                }else{
                    showLoginDialog(sharedPref)
                }
            }
            .setNegativeButton("新規登録はこちら") { _, _ -> showRegisterDialog(sharedPref) }
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
        //鍵のロード
        val ks : KeyStore = KeyStore.getInstance("AndroidKeyStore").apply{
            load(null)
        }
        //鍵のエイリアス
        var entry = ks.getEntry("myChatKey", null)
        if (entry == null) {
            Log.d("ChatApp", "鍵がありません");
            Log.d("ChatApp", "鍵を作成します");
            //ログインするたびに新しい鍵を生成，【TODO】古いメッセージが観れなくなる
            CryptoManager.generateRSAKeyPairIfNeeded()
        }

        //userIDが取得できない場合　
        if (myUserId == -1) {
            //ログインダイアログの処理
            showLoginDialog(sharedPref)

        } else {
            Log.d("ChatApp", "ログイン済みです！私のID: $myUserId")
            syncRooms()
            loadRooms(db.roomDao())
        }

    }
}