package com.tman.mychat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.EditText

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.tman.mychat.R.id
import com.tman.mychat.databinding.ActivityMainBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.collections.mutableListOf

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
}

