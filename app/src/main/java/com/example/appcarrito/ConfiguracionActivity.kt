package com.example.appcarrito

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.HttpURLConnection
import java.net.URL

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var editTextConnectionStatus: EditText
    private lateinit var progressBarBattery: ProgressBar
    private lateinit var buttonLogout: Button

    private var raspberryPiIp: String? = null // IP de la Raspberry Pi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.configuracion)

        // Inicializamos los elementos de la interfaz
        editTextConnectionStatus = findViewById(R.id.editTextConnectionStatus)
        progressBarBattery = findViewById(R.id.progressBarBattery)
        buttonLogout = findViewById(R.id.buttonLogout)

        // Recuperamos los datos de la sesión de MainActivity a través del Intent
        val batteryLevel = intent.getIntExtra("batteryLevel", 50)
        val isConnected = intent.getBooleanExtra("isConnected", false)
        raspberryPiIp = intent.getStringExtra("raspberryPiIp") // IP de la Raspberry Pi

        // Actualizamos la interfaz con los valores recibidos
        updateConnectionStatus(isConnected)
        updateBatteryLevel(batteryLevel)

        // Configura el botón de Cerrar Sesión
        buttonLogout.setOnClickListener {
            logout()
        }
    }

    private fun logout() {
        // Borra los datos de sesión en SharedPreferences
        val sharedPreferences = getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()

        // Redirige al usuario a la pantalla de inicio de sesión
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    // Función para mostrar el estado de conexión
    private fun updateConnectionStatus(isConnected: Boolean) {
        editTextConnectionStatus.setText(if (isConnected) "Conectado" else "No Conectado")
    }

    // Función para actualizar el nivel de batería
    private fun updateBatteryLevel(level: Int) {
        progressBarBattery.progress = if (level != 0) level else 50 // Predetermina a 50 si no se obtiene valor
    }

    // Método para enviar comandos HTTP al Raspberry Pi
    private fun sendCommandToRaspberry(command: String) {
        raspberryPiIp?.let {
            val url = "http://$it/$command"
            SendRequestTask(command).execute(url)
        } ?: showToast("Raspberry Pi no encontrada en la red")
    }

    // Método para mostrar mensajes al usuario
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Clase para enviar comandos en segundo plano
    private inner class SendRequestTask(private val command: String) : AsyncTask<String, Void, Boolean>() {

        override fun doInBackground(vararg urls: String): Boolean {
            return try {
                val url = URL(urls[0])
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.responseCode == 200
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        override fun onPostExecute(success: Boolean) {
            if (success) {
                showToast("Comando $command enviado con éxito")
            } else {
                showToast("Error al enviar comando $command")
            }
        }
    }
}
