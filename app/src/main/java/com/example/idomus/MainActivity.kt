package com.example.idomus

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button

    private val API_URL = "http://10.49.119.145/idomus/api/login_api.php"

    private val client by lazy {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        OkHttpClient.Builder()
            .addInterceptor(log)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val pass = etPassword.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                toast("Completa email y contraseña")
                return@setOnClickListener
            }
            doLogin(email, pass)
        }

        val ivShowPass = findViewById<ImageView>(R.id.ivShowPass)
        var isVisible = false
        ivShowPass.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                etPassword.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                ivShowPass.setImageResource(android.R.drawable.ic_menu_view)
            } else {
                etPassword.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                ivShowPass.setImageResource(android.R.drawable.ic_secure)
            }
            etPassword.setSelection(etPassword.text.length)
        }
    }

    private fun doLogin(email: String, password: String) {
        val form = FormBody.Builder()
            .add("email", email)
            .add("password", password)
            .build()

        val req = Request.Builder()
            .url(API_URL)
            .post(form)
            .header("Accept", "application/json")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { toast("Network error: ${e.localizedMessage}") }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                runOnUiThread {
                    try {
                        val js = JSONObject(bodyStr)
                        if (js.optBoolean("success")) {
                            val i = Intent(this@MainActivity, DashboardActivity::class.java)
                            i.putExtra("iduser", js.optInt("iduser", -1))
                            i.putExtra("nombre", js.optString("nombre"))
                            i.putExtra("correo", js.optString("correo"))
                            i.putExtra("rol", js.optString("rol"))
                            startActivity(i)
                            finish()
                        } else {
                            toast(js.optString("message", "Error de login"))
                        }
                    } catch (_: Exception) {
                        toast("Respuesta no válida del servidor")
                    }
                }
            }
        })
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}