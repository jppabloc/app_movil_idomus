package com.example.idomus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class WelcomeFragment : Fragment() {

    private var iduser: Int = -1
    private var nombre: String = "Usuario"
    private var correo: String = "-"
    private var rol: String = "usuario"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            iduser = args.getInt("iduser", -1)
            nombre = args.getString("nombre", "Usuario") ?: "Usuario"
            correo = args.getString("correo", "-") ?: "-"
            rol    = args.getString("rol", "usuario") ?: "usuario"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_welcome, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvWelcomeTitle).text = "Hola, $nombre 👋"
        view.findViewById<TextView>(R.id.tvWelcomeSubtitle).text = "Bienvenido a iDomus"
        view.findViewById<TextView>(R.id.tvWelcomeRole).text =
            "Rol: ${rol.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}"
    }

    companion object {
        fun newInstance(iduser: Int, nombre: String, correo: String, rol: String) =
            WelcomeFragment().apply {
                arguments = Bundle().apply {
                    putInt("iduser", iduser)
                    putString("nombre", nombre)
                    putString("correo", correo)
                    putString("rol", rol)
                }
            }
    }
}