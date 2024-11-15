package com.example.appcarrito

import FirebaseDatabaseHelper
import User
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    private lateinit var editTextEmail: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonAction: Button
    private lateinit var toggleTextView: TextView
    private lateinit var databaseReference: DatabaseReference
    private val databaseHelper = FirebaseDatabaseHelper() // Instancia del helper de la base de datos
    private var isLoginMode = true // Modo inicial: Iniciar Sesión

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Inicializamos la referencia de la base de datos de Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference("users")

        editTextEmail = findViewById(R.id.emailEditText)
        editTextPassword = findViewById(R.id.passwordEditText)
        buttonAction = findViewById(R.id.actionButton)
        toggleTextView = findViewById(R.id.toggleTextView)

        buttonAction.setOnClickListener {
            val email = editTextEmail.text.toString()
            val password = editTextPassword.text.toString()

            if (isValidEmail(email) && isValidPassword(password)) {
                if (isLoginMode) {
                    // Lógica de inicio de sesión
                    loginUser(email, password)
                } else {
                    // Lógica de registro
                    registerUser(email, password)
                }
            } else {
                Toast.makeText(this, "Correo o contraseña no válidos", Toast.LENGTH_SHORT).show()
            }
        }

        toggleTextView.setOnClickListener {
            isLoginMode = !isLoginMode
            updateUI()
        }
    }

    private fun loginUser(email: String, password: String) {
        databaseReference.get().addOnSuccessListener { dataSnapshot ->
            var userExists = false
            var userId: String? = null

            for (snapshot in dataSnapshot.children) {
                val storedEmail = snapshot.child("email").getValue(String::class.java)
                val storedPassword = snapshot.child("password").getValue(String::class.java)
                if (email == storedEmail && password == storedPassword) {
                    userExists = true
                    userId = snapshot.key
                    // Guardar el estado de inicio de sesión en SharedPreferences
                    val sharedPreferences = getSharedPreferences("UserPreferences", Context.MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.putBoolean("isRegistered", true)
                    editor.putString("userId", userId) // Guardar el ID del usuario para futuras referencias
                    editor.apply()
                    break
                }
            }

            if (userExists && userId != null) {
                // Registrar la sesión de inicio de sesión en la base de datos
                databaseHelper.recordLoginSession(userId)
                Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                goToMainActivity()
            } else {
                Toast.makeText(this, "Correo o contraseña incorrectos", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error al conectarse a la base de datos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerUser(email: String, password: String) {
        val userId = databaseReference.push().key ?: return
        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val user = User(
            id = userId,
            email = email,
            password = password,
            registrationDate = currentDateTime,
            deviceModel = Build.MODEL
        )

        databaseReference.child(userId).setValue(user).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                isLoginMode = true // Cambia a modo inicio de sesión tras el registro
                updateUI()
            } else {
                Toast.makeText(this, "Error al registrar usuario", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        if (isLoginMode) {
            buttonAction.text = "Iniciar Sesión"
            toggleTextView.text = "¿No tienes cuenta? Regístrate aquí"
        } else {
            buttonAction.text = "Registrar"
            toggleTextView.text = "¿Ya tienes cuenta? Inicia sesión aquí"
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidPassword(password: String): Boolean {
        return password.length >= 8 // Se puede modificar según los requisitos de seguridad
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
