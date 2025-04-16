package com.example.calculadoravlsm

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

class MainActivity : AppCompatActivity() {

    // Data classes para almacenar la información.
    data class Subred(val nombre: String, val hosts: Int)
    data class Resultado(
        val nombre: String,
        val ipSubred: String,
        val mascara: String,
        val gateway: String,
        val broadcast: String,
        val rangoHosts: String,
        val cantidadHosts: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Establece el layout principal.
        setContentView(R.layout.activity_main)

        // Referencias a los elementos del layout.
        val numSubredesEdit = findViewById<EditText>(R.id.numSubredes)
        val inputsSubredContainer = findViewById<LinearLayout>(R.id.inputsSubred)
        val calculateButton = findViewById<Button>(R.id.calculateButton)
        val tableResults = findViewById<TableLayout>(R.id.tableResults)

        // Cada vez que se modifique el campo "Número de subredes", se generan los inputs correspondientes.
        numSubredesEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Elimina vistas existentes.
                inputsSubredContainer.removeAllViews()
                val numSubredes = s.toString().toIntOrNull() ?: 0
                for (i in 0 until numSubredes) {
                    // Uso de this@MainActivity para pasar el contexto correcto.
                    val subredView = LayoutInflater.from(this@MainActivity)
                        .inflate(R.layout.subred_item, inputsSubredContainer, false)
                    inputsSubredContainer.addView(subredView)
                }
            }
        })

        // Al pulsar el botón se procesan los datos y se actualiza la tabla de resultados.
        calculateButton.setOnClickListener {
            val direccionIP = findViewById<EditText>(R.id.direccionIP).text.toString()
            val prefijoSubred = findViewById<EditText>(R.id.prefijoSubred).text.toString().toIntOrNull() ?: return@setOnClickListener

            // Se crean las subredes a partir de los datos ingresados.
            val subredes = mutableListOf<Subred>()
            for (i in 0 until inputsSubredContainer.childCount) {
                val subredView = inputsSubredContainer.getChildAt(i)
                val nombreEdit = subredView.findViewById<EditText>(R.id.subredName)
                val hostsEdit = subredView.findViewById<EditText>(R.id.subredHosts)
                val nombre = nombreEdit.text.toString()
                val hosts = hostsEdit.text.toString().toIntOrNull() ?: 0
                subredes.add(Subred(nombre, hosts))
            }

            // Se calculan los datos VLSM.
            val resultados = calcularVLSM(direccionIP, prefijoSubred, subredes)

            // Limpia los resultados anteriores de la tabla (manteniendo la cabecera).
            if (tableResults.childCount > 1) {
                tableResults.removeViews(1, tableResults.childCount - 1)
            }

            // Agrega cada resultado como una nueva fila en la tabla.
            resultados.forEach { res ->
                val row = TableRow(this)
                row.addView(createTextView(res.nombre))
                row.addView(createTextView(res.ipSubred))
                row.addView(createTextView(res.mascara))
                row.addView(createTextView(res.gateway))
                row.addView(createTextView(res.broadcast))
                row.addView(createTextView(res.rangoHosts))
                row.addView(createTextView(res.cantidadHosts.toString()))
                tableResults.addView(row)
            }
        }
    }

    // Función auxiliar para crear un TextView con padding (usado en la tabla).
    private fun createTextView(texto: String): TextView {
        return TextView(this).apply {
            text = texto
            setPadding(8, 8, 8, 8)
        }
    }

    /**
     * Realiza el cálculo VLSM.
     *
     * @param direccionIP Dirección IP base (ej. "192.168.1.0")
     * @param prefijo Número de prefijo de la red principal (ej. 24)
     * @param subredes Lista de subredes con nombre y cantidad de hosts requeridos.
     * @return Lista de objetos Resultado con la información calculada.
     */
    private fun calcularVLSM(
        direccionIP: String,
        prefijo: Int,
        subredes: List<Subred>
    ): List<Resultado> {
        // Separa la dirección IP en cuatro partes.
        val partesIP = direccionIP.split(".")
        if (partesIP.size != 4) return emptyList()

        // Se asume que los tres primeros octetos son fijos y el cuarto es variable.
        var baseIP = partesIP[3].toIntOrNull() ?: 0
        val resultados = mutableListOf<Resultado>()

        // Ordena las subredes de mayor a menor cantidad de hosts.
        val subredesOrdenadas = subredes.sortedByDescending { it.hosts }

        subredesOrdenadas.forEach { subred ->
            // Calcula el tamaño mínimo de la subred: 2^(ceil(log2(hosts + 2)))
            val tamanoSubred = 2.0.pow(ceil(log2((subred.hosts + 2).toDouble()))).toInt()
            // Calcula la máscara de subred en formato CIDR.
            val mascaraSubred = 32 - (log2(tamanoSubred.toDouble())).toInt()
            // Obtiene la máscara en notación decimal punteada.
            val mascaraPunteada = calcularMascaraPunteada(mascaraSubred)

            // Construye las direcciones: IP base, gateway, broadcast y rango de hosts.
            val ipBase = "${partesIP[0]}.${partesIP[1]}.${partesIP[2]}.$baseIP"
            val gateway = "${partesIP[0]}.${partesIP[1]}.${partesIP[2]}.${baseIP + 1}"
            val broadcast = "${partesIP[0]}.${partesIP[1]}.${partesIP[2]}.${baseIP + tamanoSubred - 1}"
            val rangoHosts = "${partesIP[0]}.${partesIP[1]}.${partesIP[2]}." +
                    "${baseIP + 2} - ${partesIP[0]}.${partesIP[1]}.${partesIP[2]}.${baseIP + tamanoSubred - 2}"

            resultados.add(
                Resultado(
                    nombre = subred.nombre,
                    ipSubred = ipBase,
                    mascara = "/$mascaraSubred ($mascaraPunteada)",
                    gateway = gateway,
                    broadcast = broadcast,
                    rangoHosts = rangoHosts,
                    cantidadHosts = tamanoSubred - 2
                )
            )

            // Actualiza el valor del último octeto para la siguiente subred.
            baseIP += tamanoSubred
        }
        return resultados
    }

    /**
     * Calcula la máscara de subred en notación decimal punteada a partir del prefijo.
     * Por ejemplo, para un prefijo de 26 se obtiene "255.255.255.192".
     */
    private fun calcularMascaraPunteada(mascara: Int): String {
        val octetos = mutableListOf<Int>()
        for (j in 0 until 4) {
            val valor = when {
                j < mascara / 8 -> 255
                j == mascara / 8 -> 256 - 2.0.pow((8 - (mascara % 8)).toDouble()).toInt()
                else -> 0
            }
            octetos.add(valor)
        }
        return octetos.joinToString(".")
    }
}
