package com.example.idomus

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class PaymentsFragment : Fragment() {

    // === AJUSTA: base de tu API ===
    private val API_BASE = "http://10.49.119.145/idomus/api"

    // UI
    private lateinit var progress: ProgressBar
    private lateinit var tvEmpty: TextView
    private lateinit var sectionReservas: LinearLayout
    private lateinit var sectionCuotas: LinearLayout

    // Sesión
    private var iduser: Int = -1

    // HTTP
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
        iduser = arguments?.getInt("iduser", -1)
            ?: activity?.intent?.getIntExtra("iduser", -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.fragment_payments, container, false)
        progress = v.findViewById(R.id.progress)
        tvEmpty = v.findViewById(R.id.tvEmpty)
        sectionReservas = v.findViewById(R.id.sectionReservas)
        sectionCuotas = v.findViewById(R.id.sectionCuotas)

        loadOptions()
        return v
    }

    private fun loadOptions() {
        if (iduser <= 0) {
            showEmpty("Falta iduser desde el Login")
            return
        }
        showLoading(true)

        // Tu API usa "actions=options" (con 's')
        val url = "$API_BASE/pagos_api.php?actions=options&iduser=$iduser"
        val req = Request.Builder().url(url).get().build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = ui {
                showLoading(false)
                showEmpty("Error de red: ${e.localizedMessage}")
            }

            override fun onResponse(call: Call, resp: Response) {
                val body = resp.body?.string().orEmpty()
                ui { showLoading(false) }

                if (!resp.isSuccessful) {
                    ui { showEmpty("HTTP ${resp.code}") }
                    return
                }

                try {
                    val js = JSONObject(body)
                    val ok = js.optBoolean("success", false)
                    if (!ok) {
                        ui { showEmpty(js.optString("message", "Error")) }
                        return
                    }

                    val reservas = js.optJSONArray("reservas_aprobadas") ?: JSONArray()
                    val cuotasObj = js.optJSONObject("cuotas_pendientes")
                    val cuotasRows = cuotasObj?.optJSONArray("rows") ?: JSONArray()

                    ui {
                        // Limpiamos contenedores
                        sectionReservas.removeAllViews()
                        sectionCuotas.removeAllViews()

                        var count = 0

                        // === Reservas aprobadas ===
                        if (reservas.length() > 0) {
                            for (i in 0 until reservas.length()) {
                                val o = reservas.getJSONObject(i)
                                val id = o.optInt("id_reserva")
                                val area = o.optString("nombre_area")
                                val edificio = o.optString("edificio")
                                val inicio = o.optString("fecha_inicio").replace('T',' ').take(16)
                                val fin = o.optString("fecha_fin").replace('T',' ').take(16)
                                val pagado = o.optBoolean("pagado", false)
                                val monto = o.opt("monto")?.toString()?.toDoubleOrNull() ?: 0.0

                                sectionReservas.addView(
                                    buildReservaCard(
                                        title = "Reserva #$id · $area",
                                        subtitle = "$edificio  •  $inicio - $fin",
                                        monto = monto,
                                        estado = if (pagado) "PAGADO" else "PENDIENTE",
                                        colorKey = area
                                    )
                                )
                                count++
                            }
                        } else {
                            sectionReservas.addView(makeMutedText("No tienes reservas aprobadas."))
                        }

                        // === Cuotas pendientes ===
                        if (cuotasRows.length() > 0) {
                            for (i in 0 until cuotasRows.length()) {
                                val o = cuotasRows.getJSONObject(i)
                                val cmId = o.optString("cm_id")
                                val unidad = o.optString("nro_unidad", "—")
                                val monto = o.opt("monto")?.toString()?.toDoubleOrNull() ?: 0.0
                                val fg = o.optString("fecha_generacion") // YYYY-MM-DD
                                val fv = o.optString("fecha_vencimiento")
                                val estado = o.optString("estado","PENDIENTE").uppercase()

                                sectionCuotas.addView(
                                    buildCuotaCard(
                                        title = "Cuota $fg · Unidad $unidad",
                                        subtitle = if (fv.isNotBlank()) "Vence: $fv" else "—",
                                        monto = monto,
                                        estado = estado,
                                        colorKey = unidad.ifBlank { cmId }
                                    )
                                )
                                count++
                            }
                        } else {
                            sectionCuotas.addView(makeMutedText("No tienes cuotas pendientes."))
                        }

                        tvEmpty.visibility = if (count == 0) View.VISIBLE else View.GONE
                    }

                } catch (ex: Exception) {
                    ui { showEmpty("Respuesta inválida") }
                }
            }
        })
    }

    // ---------- UI helpers ----------
    private fun showLoading(show: Boolean) {
        progress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmpty(msg: String) {
        tvEmpty.text = msg
        tvEmpty.visibility = View.VISIBLE
        sectionReservas.removeAllViews()
        sectionCuotas.removeAllViews()
    }

    private fun ui(block: () -> Unit) {
        if (isAdded) requireActivity().runOnUiThread(block)
    }

    // ---------- Cards programáticas (sin XML extra) ----------
    private fun buildReservaCard(
        title: String,
        subtitle: String,
        monto: Double,
        estado: String,
        colorKey: String
    ): View = buildCard(
        leftColor = colorFromKey(colorKey),
        lines = listOf(
            bold(title),
            subtitle,
            "Monto: Bs %.2f".format(monto),
            "Pago: $estado"
        )
    )

    private fun buildCuotaCard(
        title: String,
        subtitle: String,
        monto: Double,
        estado: String,
        colorKey: String
    ): View = buildCard(
        leftColor = colorFromKey(colorKey),
        lines = listOf(
            bold(title),
            subtitle,
            "Monto: Bs %.2f".format(monto),
            "Estado: $estado"
        )
    )

    private fun buildCard(leftColor: Int, lines: List<CharSequence>): View {
        val ctx = requireContext()

        // Contenedor raíz
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f)
                setColor(0xFFFFFFFF.toInt())
            }
            background = bg
            setPadding(dpInt(12))
        }

        // Barra de color
        val stripe = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dpInt(6), ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(leftColor)
        }
        root.addView(stripe)

        // Columna texto
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpInt(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        lines.forEachIndexed { idx, line ->
            val tv = TextView(ctx).apply {
                text = line
                setTextColor(0xFF333333.toInt())
                textSize = if (idx == 0) 16f else 14f
            }
            col.addView(tv)
        }

        root.addView(col)

        // Margen exterior
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpInt(8)
                bottomMargin = dpInt(4)
                leftMargin = dpInt(12)
                rightMargin = dpInt(12)
            }
        }
        wrap.addView(root)
        return wrap
    }

    private fun makeMutedText(text: String): View {
        val tv = TextView(requireContext())
        tv.text = text
        tv.setTextColor(0xFF777777.toInt())
        tv.textSize = 14f
        tv.setPadding(dpInt(12))
        return tv
    }

    // ---------- utils ----------
    private fun bold(s: String): CharSequence =
        SpannableStringBuilder(s).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, s.length, 0)
        }

    private fun dp(px: Float): Float =
        px * resources.displayMetrics.density

    private fun dpInt(px: Int): Int =
        (px * resources.displayMetrics.density).toInt()

    private fun dpInt(px: Float): Int =
        (px * resources.displayMetrics.density).toInt()

    private fun colorFromKey(key: String): Int {
        val palette = intArrayOf(
            0xFF0F3557.toInt(), // primary
            0xFF1BAAA6.toInt(), // secondary
            0xFF4D4D4D.toInt(), // tertiary
            0xFF124870.toInt(),
            0xFF19B9A2.toInt(),
            0xFF333333.toInt()
        )
        val idx = abs(key.hashCode()) % palette.size
        return palette[idx]
    }
}