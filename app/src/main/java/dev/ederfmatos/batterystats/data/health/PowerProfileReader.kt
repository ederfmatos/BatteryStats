package dev.ederfmatos.batterystats.data.health

import android.content.res.Resources
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * Lê o `power_profile.xml` do próprio aparelho.
 *
 * É um recurso XML do pacote `android`, alcançável por `getIdentifier` — lookup de recurso por
 * nome, não API oculta, sem permissão nenhuma. Traz a **capacidade de projeto que este aparelho
 * declara**, além dos mA que o fabricante atribui a cada subsistema.
 *
 * Isso resolve o denominador que faltava para a saúde da bateria. O AccuBattery usa uma tabela de
 * capacidade por modelo, editável à mão pelo usuário — e é dela que saem os prints de "saúde 256%",
 * porque um denominador errado produz um percentual sem sentido exibido com toda a confiança.
 *
 * Ressalva honesta, que a UI precisa repetir: vários fabricantes deixam os valores default do
 * AOSP aqui. O número é **declarado**, não medido.
 */
class PowerProfileReader {

    data class PowerProfile(
        /** `battery.capacity`, em mAh. Null quando o aparelho não declara. */
        val batteryCapacityMah: Double?,
        /** Consumo declarado da tela ligada no brilho mínimo, em mA. */
        val screenOnMa: Double?,
        /** Consumo adicional declarado no brilho máximo, em mA. */
        val screenFullMa: Double?,
        val gpsOnMa: Double?,
        val wifiOnMa: Double?,
        val radioActiveMa: Double?,
    ) {
        val isEmpty: Boolean get() = batteryCapacityMah == null && screenOnMa == null
    }

    fun read(): PowerProfile {
        val values = mutableMapOf<String, Double>()
        try {
            val resources = Resources.getSystem()
            val id = resources.getIdentifier(RESOURCE_NAME, "xml", ANDROID_PACKAGE)
            if (id == 0) {
                Log.i(TAG, "Este aparelho não expõe power_profile.xml")
                return EMPTY
            }

            resources.getXml(id).use { parser ->
                var currentName: String? = null
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            currentName = if (parser.name == "item" || parser.name == "value") {
                                parser.getAttributeValue(null, "name") ?: currentName
                            } else {
                                null
                            }
                        }

                        XmlPullParser.TEXT -> {
                            val name = currentName ?: continue
                            // `array` tem vários `value` sob o mesmo nome; o primeiro basta aqui.
                            parser.text?.trim()?.toDoubleOrNull()?.let { number ->
                                values.putIfAbsent(name, number)
                            }
                        }

                        XmlPullParser.END_TAG -> if (parser.name == "item") currentName = null
                    }
                }
            }
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "power_profile.xml malformado neste aparelho", e)
            return EMPTY
        } catch (e: IOException) {
            Log.w(TAG, "Falha ao ler power_profile.xml", e)
            return EMPTY
        } catch (e: Resources.NotFoundException) {
            Log.i(TAG, "power_profile.xml não encontrado", e)
            return EMPTY
        }

        return PowerProfile(
            batteryCapacityMah = values["battery.capacity"],
            screenOnMa = values["screen.on"],
            screenFullMa = values["screen.full"],
            gpsOnMa = values["gps.on"],
            wifiOnMa = values["wifi.on"],
            radioActiveMa = values["radio.active"],
        )
    }

    private fun android.content.res.XmlResourceParser.use(block: (XmlPullParser) -> Unit) {
        try {
            block(this)
        } finally {
            close()
        }
    }

    companion object {
        private const val TAG = "PowerProfileReader"
        private const val RESOURCE_NAME = "power_profile"
        private const val ANDROID_PACKAGE = "android"
        val EMPTY = PowerProfile(null, null, null, null, null, null)
    }
}
