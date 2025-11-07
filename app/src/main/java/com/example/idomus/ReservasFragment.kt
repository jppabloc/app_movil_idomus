package com.example.idomus

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.text.format.DateFormat
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ReservasFragment : Fragment() {

    private val API_BASE = "http://10.49.119.145/idomus/api"

    private lateinit var sectionReservas: LinearLayout
    private lateinit var tvHeader: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var fab: com.google.android.material.floatingactionbutton.FloatingActionButton

    private var iduser: Int = -1
    private var nombre: String = "Usuario"

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
        iduser = activity?.intent?.getIntExtra("iduser", -1) ?: -1
        nombre = activity?.intent?.getStringExtra("nombre") ?: "Usuario"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_reservas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sectionReservas = view.findViewById(R.id.sectionReservas)
        tvHeader = view.findViewById(R.id.tvHeader)
        tvEmpty = view.findViewById(R.id.emptyView)
        progress = view.findViewById(R.id.progress)
        fab = view.findViewById(R.id.fabNew)

        tvHeader.text = "📅 Hola, $nombre — estas son tus reservas"

        // Pull-to-refresh
        val swipe = view.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipe)
        swipe.setOnRefreshListener { loadReservas { swipe.isRefreshing = false } }

        fab.setOnClickListener { showCreateDialog() }

        loadReservas()
    }

    // ====== API: Lista de reservas (coloca tarjetas de colores) ======
    private fun loadReservas(done: (() -> Unit)? = null) {
        if (iduser <= 0) {
            toast("Falta iduser (desde el Login)")
            showEmpty(true); done?.invoke(); return
        }
        showLoading(true)
        val url = "$API_BASE/reservas_api.php?action=list&iduser=$iduser"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = ui {
                showLoading(false); done?.invoke()
                toast("Error de red: ${e.localizedMessage}")
                showEmpty(true)
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                ui {
                    showLoading(false); done?.invoke()
                    if (!response.isSuccessful) {
                        toast("HTTP ${response.code}")
                        showEmpty(true)
                        return@ui
                    }
                    try {
                        val js = JSONObject(body)
                        if (!js.optBoolean("success", false)) {
                            toast(js.optString("message", "Error"))
                            showEmpty(true)
                            return@ui
                        }
                        val arr = js.optJSONArray("rows") ?: JSONArray()
                        renderReservas(arr)
                    } catch (_: Exception) {
                        toast("JSON inválido")
                        showEmpty(true)
                    }
                }
            }
        })
    }

    private fun renderReservas(arr: JSONArray) {
        sectionReservas.removeAllViews()
        if (arr.length() == 0) {
            showEmpty(true); return
        }
        showEmpty(false)

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optInt("id_reserva")
            val area = o.optString("nombre_area", "—")
            val edificio = o.optString("edificio", "—")
            val inicio = o.optString("fecha_inicio", "").replace('T',' ').take(16)
            val fin = o.optString("fecha_fin", "").replace('T',' ').take(16)
            val estado = o.optString("estado", "PENDIENTE").uppercase()

            val card = makeCard()

            // título
            val title = TextView(requireContext()).apply {
                text = "Reserva #$id • $area"
                textSize = 16f
                setTextColor(0xFF0F3557.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            card.addView(title)

            // detalle
            val detail = TextView(requireContext()).apply {
                text = "$edificio  •  $inicio - $fin"
                textSize = 14f
                setTextColor(0xFF4D4D4D.toInt())
            }
            card.addView(detail)

            // badge de estado
            val badge = TextView(requireContext()).apply {
                text = estado
                textSize = 12f
                setPadding(20, 8, 20, 8)
                setTextColor(0xFFFFFFFF.toInt())
                background = makeBadgeBg(estado)
            }
            // contenedor para separar
            val wrap = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                p.topMargin = dp(8)
                layoutParams = p
                addView(badge)
            }
            card.addView(wrap)

            sectionReservas.addView(card)
        }
    }

    // ====== Crear nueva reserva (diálogo) ======
    private fun showCreateDialog() {
        if (iduser <= 0) { toast("Falta iduser"); return }
        showBlocking(true)
        loadAreas { areas ->
            showBlocking(false)
            if (areas.isEmpty()) { toast("No hay áreas disponibles"); return@loadAreas }

            val ctx = requireContext()
            val content = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(8), dp(16), 0)
            }

            val sp = Spinner(ctx).apply {
                adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, areas.map { it.label })
            }
            content.addView(TextView(ctx).apply { text = "Área"; setTextColor(0xFF333333.toInt()) })
            content.addView(sp)

            val etInicio = EditText(ctx).apply { isFocusable = false; hint = "Inicio (YYYY-MM-DD HH:mm)" }
            val etFin = EditText(ctx).apply { isFocusable = false; hint = "Fin (YYYY-MM-DD HH:mm)" }
            content.addView(TextView(ctx).apply { text = "Inicio"; setTextColor(0xFF333333.toInt()) })
            content.addView(etInicio)
            content.addView(TextView(ctx).apply { text = "Fin"; setTextColor(0xFF333333.toInt()) })
            content.addView(etFin)

            val pickDateTime: (EditText) -> Unit = { target ->
                val cal = Calendar.getInstance()
                DatePickerDialog(ctx, { _, y, m, d ->
                    cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
                    val is24 = DateFormat.is24HourFormat(ctx)
                    TimePickerDialog(ctx, { _, hh, mm ->
                        cal.set(Calendar.HOUR_OF_DAY, hh); cal.set(Calendar.MINUTE, mm)
                        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        target.setText(fmt.format(cal.time))
                    }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), is24).show()
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
            etInicio.setOnClickListener { pickDateTime(etInicio) }
            etFin.setOnClickListener { pickDateTime(etFin) }

            AlertDialog.Builder(ctx)
                .setTitle("Nueva reserva")
                .setView(content)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Reservar") { _, _ ->
                    val pos = sp.selectedItemPosition.coerceAtLeast(0)
                    val idArea = areas[pos].id
                    val f1 = etInicio.text.toString().trim()
                    val f2 = etFin.text.toString().trim()
                    if (idArea <= 0 || f1.isEmpty() || f2.isEmpty()) toast("Completa todos los campos")
                    else createReserva(idArea, f1, f2, "")
                }.show()
        }
    }

    // ====== API: Crear ======
    private fun createReserva(idArea: Int, ini: String, fin: String, nota: String) {
        showBlocking(true)
        val form = FormBody.Builder()
            .add("action", "create")
            .add("iduser", iduser.toString())
            .add("id_area", idArea.toString())
            .add("fecha_inicio", ini)
            .add("fecha_fin", fin)
            .add("nota", nota)
            .build()

        val req = Request.Builder()
            .url("$API_BASE/reservas_api.php")
            .post(form)
            .header("Accept","application/json")
            .build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = ui {
                showBlocking(false); toast("Error: ${e.localizedMessage}")
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                ui {
                    showBlocking(false)
                    if (!response.isSuccessful) { toast("HTTP ${response.code}"); return@ui }
                    try {
                        val js = JSONObject(body)
                        val ok = js.optBoolean("success", false)
                        val msg = js.optString("message","")
                        toast(if (ok) "Reserva creada" else "Error: $msg")
                        if (ok) loadReservas()
                    } catch (_: Exception) { toast("Respuesta inválida") }
                }
            }
        })
    }

    // ====== API: Áreas ======
    private fun loadAreas(onReady: (List<AreaOption>) -> Unit) {
        val url = "$API_BASE/reservas_api.php?action=areas"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = ui { toast("No se pudieron cargar áreas"); onReady(emptyList()) }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) { ui { onReady(emptyList()) }; return }
                try {
                    val js = JSONObject(body)
                    val list = mutableListOf<AreaOption>()
                    val arr = js.optJSONArray("areas") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(
                            AreaOption(
                                id = o.optInt("id_area"),
                                label = (o.optString("edificio","") + " · " + o.optString("nombre_area","")).trim(' ','·')
                            )
                        )
                    }
                    ui { onReady(list) }
                } catch (_: Exception) { ui { onReady(emptyList()) } }
            }
        })
    }

    // ====== Helpers UI ======
    private fun makeCard(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(0xFFFFFFFF.toInt())
            elevation = 4f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(dp(0), dp(0), dp(0), dp(12))
            layoutParams = lp
        }
    }

    private fun makeBadgeBg(estado: String): android.graphics.drawable.GradientDrawable {
        val color = when (estado.uppercase()) {
            "APROBADA"   -> 0xFF1BAAA6.toInt() // verde agua
            "PENDIENTE"  -> 0xFFFFA000.toInt() // ámbar
            "CANCELADA"  -> 0xFFB0BEC5.toInt() // gris
            "RECHAZADA"  -> 0xFFB00020.toInt() // rojo
            else         -> 0xFF4D4D4D.toInt() // neutro
        }
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(color)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun showLoading(show: Boolean) { progress.isVisible = show }
    private fun showEmpty(empty: Boolean) {
        tvEmpty.isVisible = empty
        sectionReservas.isVisible = !empty
    }
    private fun showBlocking(b: Boolean) { progress.isVisible = b }
    private fun toast(s: String) = Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show()
    private fun ui(block: () -> Unit) { if (isAdded) requireActivity().runOnUiThread(block) }

    data class AreaOption(val id: Int, val label: String)
}