package com.example.appcarrito

import FirebaseDatabaseHelper
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private var raspberryPiIp: String? = null
    private lateinit var editTextDistance: EditText
    private lateinit var editTextFrequency: EditText
    private lateinit var buttonSaveDistance: Button
    private lateinit var buttonSaveFrequency: Button
    private lateinit var buttonStart: Button
    private lateinit var buttonUpdateBattery: Button
    private lateinit var progressBarBattery: ProgressBar
    private lateinit var spinnerProfile: Spinner
    private lateinit var notificationText: TextView
    private val databaseHelper = FirebaseDatabaseHelper()
    private lateinit var buttonForward: Button
    private lateinit var buttonBackward: Button
    private lateinit var buttonLeft: Button
    private lateinit var buttonRight: Button

    private val connectionHandler = Handler(Looper.getMainLooper())
    private var connectionRunnable: Runnable? = null // Cambia a nullable para evitar la excepción

    private var distanceValue: Int = 0
    private var frequencyValue: Int = 0
    private var batteryLevel: Int = 0
    private var tankLevel: Int = 10
    private var mediaPlayer: MediaPlayer? = null
    private var currentProfile = 0 // Perfil seleccionado (0 a 2)
    private var sessionDistance: Int = 0
    private var sessionStartTime: Long = 0
    private var isCycleRunning = false // Variable para controlar el estado del ciclo
    private var userId: String? = null // Almacenar el ID de usuario actual



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
        val isRegistered = sharedPreferences.getBoolean("isRegistered", false)
        userId = sharedPreferences.getString("userId", null) // Obtener userId del SharedPreferences

        if (!isRegistered) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.inicio)

        initializeUI()
        initializeMediaPlayer()
        loadProfileData(currentProfile) // Cargar datos del perfil inicial
        discoverRaspberryPi() // Inicia el descubrimiento de IP del Raspberry
    }

    private fun initializeUI() {
        editTextDistance = findViewById(R.id.editTextDistance)
        editTextFrequency = findViewById(R.id.editTextFrequency)
        buttonSaveDistance = findViewById(R.id.buttonSaveDistance)
        buttonSaveFrequency = findViewById(R.id.buttonSaveFrequency)
        buttonStart = findViewById(R.id.buttonStart)
        buttonUpdateBattery = findViewById(R.id.buttonUpdateBattery)
        progressBarBattery = findViewById(R.id.progressBarBattery)
        spinnerProfile = findViewById(R.id.spinner)
        notificationText = findViewById(R.id.notificationText)
        buttonForward = findViewById(R.id.buttonForward)
        buttonBackward = findViewById(R.id.buttonBackward)
        buttonLeft = findViewById(R.id.buttonLeft)
        buttonRight = findViewById(R.id.buttonRight)

        setupProfileSpinner()

        buttonSaveDistance.setOnClickListener { saveDistance() }
        buttonSaveFrequency.setOnClickListener { saveFrequency() }
        buttonStart.setOnClickListener { startCycle() }
        buttonUpdateBattery.setOnClickListener { updateBatteryLevel() }
        buttonForward.setOnClickListener { sendMovementCommand("move_forward") }
        buttonBackward.setOnClickListener { sendMovementCommand("move_backward") }
        buttonLeft.setOnClickListener { sendMovementCommand("turn_left") }
        buttonRight.setOnClickListener { sendMovementCommand("turn_right") }

    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, R.raw.background_music)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()
    }

    private fun setupProfileSpinner() {
        val profiles = listOf("Perfil 1", "Perfil 2", "Perfil 3")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, profiles)
        spinnerProfile.adapter = adapter

        spinnerProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                currentProfile = position
                loadProfileData(currentProfile)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun saveDistance() {
        val distanceText = editTextDistance.text.toString()
        if (distanceText.isNotEmpty()) {
            distanceValue = distanceText.toInt()
            saveProfileData(currentProfile, distanceValue, frequencyValue)
            showNotification("Distancia guardada: $distanceValue cm para el perfil ${currentProfile + 1}")
            sendDataToRaspberry("set_distancia?distancia=$distanceValue")
        } else {
            showNotification("Por favor, ingresa una distancia.")
        }
    }

    private fun saveFrequency() {
        val frequencyText = editTextFrequency.text.toString()
        if (frequencyText.isNotEmpty()) {
            frequencyValue = frequencyText.toInt()
            saveProfileData(currentProfile, distanceValue, frequencyValue)
            showNotification("Intervalo guardado: $frequencyValue minutos para el perfil ${currentProfile + 1}")
            sendDataToRaspberry("set_ciclo?ciclo=$frequencyValue")
        } else {
            showNotification("Por favor, ingresa un intervalo de ciclo.")
        }
    }

    private fun saveProfileData(profileIndex: Int, distance: Int, frequency: Int) {
        userId?.let { uid ->
            databaseHelper.saveProfileData(uid, profileIndex, distance, frequency)
        }
    }

    private fun loadProfileData(profileIndex: Int) {
        userId?.let { uid ->
            databaseHelper.loadProfileData(uid, profileIndex) { profileData ->
                if (profileData != null) {
                    distanceValue = profileData.distance
                    frequencyValue = profileData.frequency
                } else {
                    distanceValue = 0
                    frequencyValue = 0
                }
                editTextDistance.setText(distanceValue.toString())
                editTextFrequency.setText(frequencyValue.toString())
                showNotification("Perfil ${profileIndex + 1} cargado")
            }
        }
    }

    private fun startCycle() {
        if (distanceValue > 0 && frequencyValue > 0) {
            if (!isCycleRunning) {
                sessionStartTime = System.currentTimeMillis() // Registra el inicio solo la primera vez
                sessionDistance = 0 // Reinicia la distancia para la sesión
                isCycleRunning = true // Marca el ciclo como iniciado
                showNotification("Iniciando recorrido")
            }
            sendDataToRaspberry("iniciar")
        } else {
            showNotification("Configura distancia e intervalo primero")
        }
    }

    // Define la función `sendMovementCommand` que envía el comando de movimiento al Raspberry Pi
    private fun sendMovementCommand(command: String) {
        raspberryPiIp?.let {
            val url = "http://$it/$command"
            Log.d("sendMovementCommand", "Enviando comando: $command a URL: $url")
            SendRequestTask("Comando enviado: $command", "Error al enviar comando", url).execute()
        } ?: showNotification("Raspberry Pi no encontrado en la red")
    }

    private fun updateBatteryLevel() {
        showNotification("Solicitando nivel de batería...")
        getBatteryLevelFromRaspberry()
    }

    private fun sendDataToRaspberry(endpoint: String) {
        raspberryPiIp?.let {
            val url = "http://$it/$endpoint"
            Log.d("SendDataToRaspberry", "Intentando conectar a: $url")
            SendRequestTask("Comando enviado a Raspberry", "Error al enviar comando", url).execute()
        } ?: showNotification("Raspberry Pi no encontrado en la red")
    }


    private fun getBatteryLevelFromRaspberry() {
        raspberryPiIp?.let {
            val url = "http://$it/get_bateria"
            BatteryRequestTask().execute(url)
        } ?: showNotification("Raspberry Pi no encontrado en la red")
    }

    private inner class BatteryRequestTask : AsyncTask<String, Void, String>() {
        override fun doInBackground(vararg urls: String): String {
            return try {
                val url = URL(urls[0])
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                ""
            }
        }

        override fun onPostExecute(result: String) {
            if (result.isNotEmpty()) {
                try {
                    batteryLevel = result.toInt()
                    progressBarBattery.progress = batteryLevel
                    showNotification("Nivel de batería actualizado: $batteryLevel%")
                } catch (e: NumberFormatException) {
                    showNotification("Error al obtener nivel de batería")
                }
            } else {
                showNotification("No se pudo obtener el nivel de batería")
            }
        }
    }

    private inner class SendRequestTask(
        private val successMessage: String,
        private val errorMessage: String,
        private val url: String
    ) : AsyncTask<Void, Void, Boolean>() {

        override fun doInBackground(vararg params: Void?): Boolean {
            return try {
                val urlConnection = URL(url).openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.connectTimeout = 5000  // Tiempo de espera de conexión
                urlConnection.readTimeout = 5000     // Tiempo de espera de lectura

                // Conectar y verificar el código de respuesta
                val responseCode = urlConnection.responseCode
                responseCode == 200
            } catch (e: java.net.SocketTimeoutException) {
                Log.e("SendRequestTask", "Error: Tiempo de espera agotado para la URL: $url", e)
                false
            } catch (e: java.net.UnknownHostException) {
                Log.e("SendRequestTask", "Error: Host desconocido para la URL: $url", e)
                false
            } catch (e: java.io.IOException) {
                Log.e("SendRequestTask", "Error de E/S al intentar conectar con la URL: $url", e)
                false
            } catch (e: Exception) {
                Log.e("SendRequestTask", "Error al enviar el comando", e)
                false
            }
        }

        override fun onPostExecute(success: Boolean) {
            if (success) {
                showNotification(successMessage)
            } else {
                showNotification(errorMessage)
            }
        }
    }


    private fun showNotification(message: String) {
        notificationText.text = message
        notificationText.visibility = View.VISIBLE

        Handler(Looper.getMainLooper()).postDelayed({
            notificationText.text = ""
        }, 5000)
    }

    // Descubrimiento de Raspberry Pi usando Broadcast UDP
// Modificar el método discoverRaspberryPi
    private fun discoverRaspberryPi() {
        connectionRunnable = Runnable {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val udpSocket = DatagramSocket(5005).apply {
                        broadcast = true
                    }
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    Log.d("MainActivity", "Esperando mensajes de broadcast para detectar la Raspberry Pi...")

                    var lastKnownIp: String? = null

                    while (true) {
                        udpSocket.receive(packet)
                        val message = String(packet.data, 0, packet.length).trim()
                        Log.d("MainActivity", "Mensaje recibido: $message")

                        if (message.startsWith("Pico W IP:")) {
                            val newIp = message.split(":")[1].trim()
                            if (newIp != lastKnownIp) {
                                lastKnownIp = newIp
                                raspberryPiIp = newIp
                                Log.d("MainActivity", "Raspberry Pi detectada en IP: $raspberryPiIp")
                                runOnUiThread {
                                    showNotification("Raspberry Pi detectada en IP: $raspberryPiIp")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error en discoverRaspberryPi", e)
                    e.printStackTrace()
                }
            }
            // Repetir la verificación cada 10 segundos
            connectionHandler.postDelayed(connectionRunnable!!, 10000)
        }
        // Iniciar la verificación de conexión
        connectionHandler.post(connectionRunnable!!)
    }


    fun onCuadrito1Click(view: View) {
        openSettings()
    }

    private fun openSettings() {
        val intent = Intent(this, ConfiguracionActivity::class.java)
        intent.putExtra("batteryLevel", batteryLevel) // Nivel actual de batería
        intent.putExtra("tankLevel", tankLevel)
        intent.putExtra("cyclesCompleted", sessionDistance) // Número de ciclos realizados en la sesión
        intent.putExtra("runningTime", System.currentTimeMillis() - sessionStartTime) // Tiempo en funcionamiento en milisegundos
        intent.putExtra("isConnected", raspberryPiIp != null) // Verifica si el Raspberry Pi está conectado
        startActivity(intent)
    }


    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.start()
    }

    // Llamar a connectionHandler.removeCallbacks(connectionRunnable) para detenerlo al destruir la actividad
    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        // Detener el Runnable de conexión si está inicializado
        connectionRunnable?.let {
            connectionHandler.removeCallbacks(it)
        }
    }

}
