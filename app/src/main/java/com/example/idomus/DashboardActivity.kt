package com.example.idomus

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class DashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawer: DrawerLayout
    private lateinit var navigationView: NavigationView

    // Datos de sesión (del login)
    private var iduser: Int = -1
    private var nombre: String = "Usuario"
    private var correo: String = "-"
    private var rol: String = "usuario"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // 1) Recupera extras del Login
        iduser = intent.getIntExtra("iduser", -1)
        nombre = intent.getStringExtra("nombre") ?: "Usuario"
        correo = intent.getStringExtra("correo") ?: "-"
        rol    = intent.getStringExtra("rol") ?: "usuario"

        // 2) Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "iDomus"

        // 3) Drawer + toggle
        drawer = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)

        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        // 4) Listener del menú
        navigationView.setNavigationItemSelectedListener(this)
        navigationView.bringToFront()

        // 5) Header del drawer (nav_header.xml debe tener headerNombre y headerCorreo)
        val header = if (navigationView.headerCount > 0) {
            navigationView.getHeaderView(0)
        } else {
            navigationView.inflateHeaderView(R.layout.nav_header)
        }
        header.findViewById<android.widget.TextView>(R.id.headerNombre)?.text = nombre
        header.findViewById<android.widget.TextView>(R.id.headerCorreo)?.text = correo

        // 6) Fragment por defecto: Welcome (solo primera vez)
        if (savedInstanceState == null) {
            openWelcome()
        }
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_reservas -> {
                openReservas()
                item.isChecked = true
            }
            R.id.nav_pagos -> {
                openPagos()
                item.isChecked = true
            }
            R.id.nav_logout -> {
                // Cerrar sesión: limpia el back stack y vuelve al Login
                val i = Intent(this, MainActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(i)
                finish()
            }
        }
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    // ---------- Navegación a fragments ----------
    private fun openWelcome() {
        // Desmarca todos los ítems del menú
        clearCheckedMenu()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, WelcomeFragment.newInstance(iduser, nombre, correo, rol))
            .commit()
        supportActionBar?.title = "iDomus"
    }

    private fun openReservas() {
        val frag = ReservasFragment().apply {
            arguments = bundleOf(
                "iduser" to iduser,
                "nombre" to nombre,
                "correo" to correo,
                "rol" to rol
            )
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, frag)
            .commit()
        supportActionBar?.title = "Reservas"
        navigationView.setCheckedItem(R.id.nav_reservas)
    }

    private fun openPagos() {
        val frag = PaymentsFragment().apply {
            arguments = bundleOf("iduser" to iduser)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, frag)
            .commit()
        supportActionBar?.title = "Pagos"
    }

    private fun clearCheckedMenu() {
        val menu = navigationView.menu
        for (i in 0 until menu.size()) {
            val it = menu.getItem(i)
            it.isChecked = false
            // si tienes submenus:
            val sub = it.subMenu ?: continue
            for (j in 0 until sub.size()) sub.getItem(j).isChecked = false
        }
    }
}