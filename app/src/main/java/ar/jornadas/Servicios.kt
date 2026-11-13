package ar.jornadas

import android.content.Context
import androidx.room.Room
import ar.jornadas.data.*
import ar.jornadas.lab.ServicioLocal
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicBoolean

/** Una sola instancia por proceso: una base, un Retrofit y una carga automática por sesión. */
class Servicios private constructor(context: Context) {
    val mesa = ServicioLocal(context)
    private val archivo = Room.databaseBuilder(context, Archivo::class.java, "cartelera.db")
        .addMigrations(MIGRACION_1_2)
        .build()
    private val api = Retrofit.Builder().baseUrl("https://programa.invalid/")
        .client(OkHttpClient.Builder().addInterceptor(mesa).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(ProgramaApi::class.java)
    val agenda = AgendaRepository(api, archivo.cartelera())

    /** Consigna 44: devuelve true solo la primera vez en todo el proceso. */
    private val cargaInicialHecha = AtomicBoolean(false)
    fun tomarCargaInicial(): Boolean = cargaInicialHecha.compareAndSet(false, true)

    companion object {
        @Volatile private var instancia: Servicios? = null

        fun obtener(context: Context): Servicios =
            instancia ?: synchronized(this) {
                instancia ?: Servicios(context.applicationContext).also { instancia = it }
            }
    }
}