package com.iokreal.myapplication

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.EditText
import com.iokreal.myapplication.MainActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Settings : AppCompatActivity() {
    val camList = mutableListOf<String>()
    val camListNames = mutableListOf<String>()

    val doorList = mutableListOf<String>()
    val doorListNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(this)
        setContentView(R.layout.activity_settings)

        val editTextText = findViewById<EditText>(R.id.editTextText)
        editTextText.setText(AppPreferences.urlCam)

        Thread{
            initCams()
        }.start()
        Thread{
            initDoors()
        }.start()
    }
    private fun initCams(){
        val spi = findViewById<Spinner>(R.id.country_spinner1)
        camList.clear()
        for (i in 0 until MainActivity.cams.size) {
            Log.d("initCams", MainActivity.cams[i][1])
            camList.add(MainActivity.cams[i][0])
        }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            camList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spi.adapter = adapter
    }
    private fun initDoors(){
        try {
            val connection = URL("https://household.key.rt.ru/api/v2/app/devices/intercom").openConnection() as HttpURLConnection // thx https://github.com/artgl/hass_rtkey/blob/master/custom_components/rtkey/__init__.py
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Authorization", AppPreferences.key)
            Log.d("setStreamsRTC", connection.responseCode.toString())
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val rootObject = JSONObject(jsonString)
            val dataObject = rootObject.getJSONObject("data")
            val itemsArray = dataObject.getJSONArray("devices")
            doorList.clear()
            doorListNames.clear()
            if (itemsArray.length() > 0){
                for (i in 0 until itemsArray.length()) {
                    val itemObject = itemsArray.getJSONObject(i)
                    val id = itemObject.getString("id")
                    val name = itemObject.getString("name_by_company")
                    doorList.add(id)
                    doorListNames.add(name)
                }
            }
        } finally {
            runOnUiThread {
                val spi = findViewById<Spinner>(R.id.country_spinner2)
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    doorListNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spi.adapter = adapter
            }
        }
    }

    fun save(view: View) {
        val spi1 = findViewById<Spinner>(R.id.country_spinner1)
        Log.d("save", doorList[spi1.selectedItemPosition])
        AppPreferences.idDoor = doorList[spi1.selectedItemPosition]

        val spi2 = findViewById<Spinner>(R.id.country_spinner2)
        Log.d("save", MainActivity.cams[spi2.selectedItemPosition][1])
        AppPreferences.idCam = MainActivity.cams[spi2.selectedItemPosition][1]

        val editTextText = findViewById<EditText>(R.id.editTextText)
        Log.d("save", editTextText.text.toString())
        AppPreferences.urlCam = editTextText.text.toString()
        finish()
    }
}