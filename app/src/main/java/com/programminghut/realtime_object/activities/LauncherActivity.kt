package com.programminghut.realtime_object.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.programminghut.realtime_object.R

class LauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)

        val btnDarija = findViewById<Button>(R.id.btnDarija)
        val btnFrancais = findViewById<Button>(R.id.btnFrancais)

        btnDarija.setOnClickListener {
            launchNavigation("darija")
        }

        btnFrancais.setOnClickListener {
            launchNavigation("fr")
        }
    }

    private fun launchNavigation(language: String) {
        val intent = Intent(this, NavigationWithDetectionActivity::class.java)
        intent.putExtra("lang", language)
        startActivity(intent)
        finish()
    }
}
