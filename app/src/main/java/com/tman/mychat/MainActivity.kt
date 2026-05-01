package com.tman.mychat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import com.tman.mychat.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var _binding : ActivityMainBinding? = null
    private val binding get() = _binding!!

//    val db = AppDatabase.getDatabase(requireContext())
//    val messageDao = db.messageDao()

    override fun onCreate(savedInstanceState: Bundle?) {//Bundleは入力途中の一時データ ?はNullの場合があるかもしれない
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

    }
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        // Navigationコンポーネントに戻る処理を任せる
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}

