package com.tman.mychat

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tman.mychat.databinding.FragmentRoomListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyStore

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
        val db = AppDatabase.getDatabase(requireContext())
        val roomDao = db.roomDao()

        // ログインチェックと鍵の確認
        checkLoginAndKey(sharedPref, db)

        val myUserId = sharedPref.getInt("myUserId", -1)
        Log.d("ChatApp", "【現在のID確認】 myUserId = $myUserId")

        updateUserUI(sharedPref)
        syncRooms()

        roomAdapter = RoomListAdapter(emptyList())
        binding.roomRecyclerView.apply {
            adapter = roomAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        loadRooms(roomDao)
        setLogoutButton(sharedPref, db)
        createRoomButton(sharedPref)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadRooms(roomDao: RoomDao) {
        lifecycleScope.launch {
            val rooms = roomDao.getRooms()
            roomAdapter.updateData(rooms)
        }
    }

    private fun setLogoutButton(sharedPref: SharedPreferences, db: AppDatabase){
        binding.logoutButton.setOnClickListener {
            val roomDao = db.roomDao()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.clearAllTables()
                }
                sharedPref.edit().clear().apply()

                // 💡 モダンパスワード方式：ログアウト時は端末の鍵を削除して安全にします
                CryptoManager.deleteRsaKey(requireContext())

                checkLoginAndKey(sharedPref, db)
                updateUserUI(sharedPref)
                roomAdapter.updateData(emptyList())
                loadRooms(roomDao)
            }
        }
    }

    private fun createRoomButton(sharedPref: SharedPreferences){
        binding.createRoom.setOnClickListener {
            val editText = EditText(requireContext())
            editText.hint = "例：雑談部屋"

            AlertDialog.Builder(requireContext())
                .setTitle("新しい部屋の作成")
                .setView(editText)
                .setPositiveButton("作成") { _, _ ->
                    val roomName = editText.text.toString()
                    if (roomName.isNotEmpty()) {
                        createRoom(roomName)
                    }
                }
                .setNegativeButton("キャンセル", null)
                .show()
        }
    }

    private fun syncRooms() {
        val sharedPref = requireActivity().getSharedPreferences("ChatAppPrefs", Context.MODE_PRIVATE)
        val myUserId = sharedPref.getInt("myUserId", -1)

        if (myUserId == -1) return

        lifecycleScope.launch {
            try {
                val remoteRooms = RetrofitClient.api.getRooms(myUserId)
                val roomEntities = remoteRooms.map { response ->
                    RoomEntity(roomId = response.id, name = response.name, icon = "")
                }

                val database = AppDatabase.getDatabase(requireContext())
                database.roomDao().upsertRoom(roomEntities)
                loadRooms(database.roomDao())

                Log.d("ChatApp", "同期成功！")
            } catch (e: Exception) {
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
                val newRoomEntity = RoomEntity(roomId = response.id, name = response.name, icon = "")

                database.roomDao().upsertRoom(listOf(newRoomEntity))
                RetrofitClient.api.createRoomUser(RoomUserRequest(response.id, myUserId))

                loadRooms(database.roomDao())
                Log.d("ChatApp", "作成成功: ${response.name}")
            } catch (e: Exception) {
                Log.e("ChatApp", "作成失敗: ${e.message}")
            }
        }
    }

    // ==========================================
    // 🔐 新規アカウント作成（暗号化バックアップ送信）
    // ==========================================
    private fun showRegisterDialog(sharedPref: SharedPreferences) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        // 💡 パスワードも一緒に登録させるUIに変更
        val nameInput = EditText(ctx).apply { hint = "新しいユーザー名" }
        val passwordInput = EditText(ctx).apply { hint = "復元用パスワード（引き継ぎに使います）" }
        layout.addView(nameInput)
        layout.addView(passwordInput)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("新規アカウント作成")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("登録", null)
            .setNegativeButton("ログインはこちら") { _, _ -> showLoginDialog(sharedPref) }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val userName = nameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (userName.isEmpty()) {
                nameInput.error = "名前を入力してください"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                passwordInput.error = "パスワードを入力してください"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    // 1. バックアップ用に、まずは「メモリ上」でRSA鍵ペアを作る
                    val (publicKey, privateKeyBytes) = CryptoManager.generateKeyForBackup()

                    // 作った初期鍵ペアを、今すぐ自分の端末にも保存する！
                    CryptoManager.saveKeyPairToLocal(ctx, publicKey, privateKeyBytes)

                    // 2. 入力されたパスワードで、秘密鍵をAES暗号化する
                    val encryptedBackup = CryptoManager.encryptPrivateKeyWithPassword(privateKeyBytes, password)

                    // 3. サーバーへ新規登録リクエスト送信
                    RetrofitClient.api.registerUser(
                        UserRequest(name = userName, password = password, publicKey = publicKey, keyBackup = encryptedBackup)
                    )

                    dialog.dismiss()
                    Toast.makeText(ctx, "登録が完了しました！", Toast.LENGTH_SHORT).show()
                    showLoginDialog(sharedPref)

                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 409) {
                        nameInput.error = "この名前はすでに使われています"
                    } else {
                        Toast.makeText(ctx, "エラー（コード: ${e.code()}）", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ChatApp", "通信失敗", e)
                    Toast.makeText(ctx, "ネットワーク接続を確認してください", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ==========================================
    // 🔓 ログイン（サーバーから鍵を落としてパスワード復元）
    // ==========================================
    private fun showLoginDialog(sharedPref: SharedPreferences) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        val nameInput = EditText(ctx).apply { hint = "ユーザ名" }
        val passwordInput = EditText(ctx).apply { hint = "パスワード" }
        layout.addView(nameInput)
        layout.addView(passwordInput)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("ログイン")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("決定", null)
            .setNegativeButton("新規登録はこちら") { _, _ -> showRegisterDialog(sharedPref) }
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val userName = nameInput.text.toString().trim()
            val password = passwordInput.text.toString().trim() // 💡 ハードコードを廃止し、実際の入力を利用

            if (userName.isEmpty()) {
                nameInput.error = "名前を入力してください"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                passwordInput.error = "パスワードを入力してください"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    // 1. Goサーバーに名前、パスワード、公開鍵を送信！
                    val response = RetrofitClient.api.loginUser(
                        LoginRequest(name = userName, password = password, publicKey = "")
                    )

                    // 💥 【超重要・ゼロナレッジ復元】
                    // サーバーから返ってきた「暗号化された秘密鍵」を、ユーザーが入力したパスワードで解凍してKeyStoreへ格納！
                    // ⚠️ 注意：この処理を動かすには、ChatApi.kt の LoginResponse に val keyBackup: String を追加する必要があります
                    CryptoManager.decryptAndImportPrivateKey(requireContext(),response.keyBackup, password)

                    // 2. サーバーから返ってきた情報をローカルに保存
                    sharedPref.edit().apply {
                        putInt("myUserId", response.userId)
                        putString("myUserName", response.userName)
                        putString("jwt_token", response.token)
                    }.apply()

                    Log.d("ChatApp", "ログイン成功！ID: ${response.userId}")
                    dialog.dismiss()

                    syncRooms()
                    updateUserUI(sharedPref)

                } catch (e: retrofit2.HttpException) {
                    if (e.code() == 401) {
                        nameInput.error = "ユーザー名またはパスワードが間違っています"
                    } else {
                        Toast.makeText(ctx, "サーバーエラー（コード: ${e.code()}）", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ChatApp", "ログイン失敗", e)
                    Toast.makeText(ctx, "ネットワーク接続を確認してください", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUserUI(sharedPref: SharedPreferences) {
        val userName = sharedPref.getString("myUserName", null)

        if (userName != null) {
            binding.currentUserName.text = "${userName}さん"
            binding.userIcon.text = userName.take(1).uppercase()
            binding.logoutButton.visibility = View.VISIBLE
        } else {
            binding.currentUserName.text = "未ログイン"
            binding.userIcon.text = "?"
            binding.logoutButton.visibility = View.GONE
        }
    }

    // 💡 関数名を分かりやすく変更（ログイン状態と鍵の有無を安全にチェック）
    private fun checkLoginAndKey(sharedPref: SharedPreferences, db: AppDatabase) {
        val myUserId = sharedPref.getInt("myUserId", -1)

        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val hasKey = ks.containsAlias("myChatKey")

        if (myUserId == -1) {
            showLoginDialog(sharedPref)
        } else {
            // ログイン状態なのに鍵がない（アプリデータが半分消えた等）の異常系対策
            if (!hasKey) {
                Log.d("ChatApp", "ログイン中ですが、秘密鍵がありません。再生成します。")
                CryptoManager.generateRSAKeyPairIfNeeded(requireContext())
            }
            Log.d("ChatApp", "ログイン済みです！私のID: $myUserId")
            syncRooms()
            loadRooms(db.roomDao())
        }
    }
}