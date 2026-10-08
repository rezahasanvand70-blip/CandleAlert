package com.example.candlealert

import android.os.Bundle
import android.graphics.Color
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class JournalActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this)
        view.text = "Journal"
        view.textSize = 28f
        view.setTextColor(Color.BLACK)
        view.setPadding(32, 48, 32, 32)
        setContentView(view)
    }
}
