package com.example.andro

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.andro.models.BoardSize
import com.example.andro.models.MemoryGame
import com.github.jinatonic.confetti.CommonConfetti
import kotlinx.android.synthetic.main.activity_main.*

class WonPage : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_won_page)

    }
    fun getBoardSize(view: View) {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

}