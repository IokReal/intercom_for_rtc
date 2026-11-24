package com.iokreal.myapplication

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Settings : AppCompatActivity() {

    private lateinit var camSpinner: Spinner
    private lateinit var doorSpinner: Spinner
    private lateinit var urlEditText: EditText

    private val doors = mutableListOf<Door>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(this)
        setContentView(R.layout.activity_settings)

        camSpinner = findViewById(R.id.country_spinner1)
        doorSpinner = findViewById(R.id.country_spinner2)
        urlEditText = findViewById(R.id.editTextText)

        urlEditText.setText(AppPreferences.urlCam)

        setupCamsSpinner()
        loadDoors()
    }

    private fun setupCamsSpinner() {
        val camNames = MainActivity.cams.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, camNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        camSpinner.adapter = adapter

        val savedIndex = MainActivity.cams.indexOfFirst { it.id == AppPreferences.idCam }
        if (savedIndex >= 0) {
            camSpinner.setSelection(savedIndex)
        }
    }

    private fun loadDoors() {
        lifecycleScope.launch {
            val loadedDoors = withContext(Dispatchers.IO) { fetchDoors() }
            if (loadedDoors == null) {
                Toast.makeText(this@Settings, "Не удалось загрузить список дверей", Toast.LENGTH_SHORT).show()
                return@launch
            }

            doors.clear()
            doors.addAll(loadedDoors)

            val adapter = ArrayAdapter(
                this@Settings,
                android.R.layout.simple_spinner_item,
                doors.map { it.name }
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            doorSpinner.adapter = adapter

            val savedIndex = doors.indexOfFirst { it.id == AppPreferences.idDoor }
            if (savedIndex >= 0) {
                doorSpinner.setSelection(savedIndex)
            }
        }
    }

    private fun fetchDoors(): List<Door>? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("https://household.key.rt.ru/api/v2/app/devices/intercom")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Authorization", AppPreferences.key)

            if (connection.responseCode != 200) {
                Log.e("Settings", "Doors API error: ${connection.responseCode}")
                return null
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val itemsArray = JSONObject(jsonString)
                .getJSONObject("data")
                .getJSONArray("devices")

            List(itemsArray.length()) { i ->
                val item = itemsArray.getJSONObject(i)
                Door(
                    id = item.getString("id"),
                    name = item.getString("name_by_company")
                )
            }
        } catch (e: Exception) {
            Log.e("Settings", "Failed to load doors", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun save(view: View) {
        doors.getOrNull(doorSpinner.selectedItemPosition)?.let { selectedDoor ->
            AppPreferences.idDoor = selectedDoor.id
        }

        MainActivity.cams.getOrNull(camSpinner.selectedItemPosition)?.let { selectedCam ->
            AppPreferences.idCam = selectedCam.id
        }

        AppPreferences.urlCam = urlEditText.text.toString().trim()
        finish()
    }
}