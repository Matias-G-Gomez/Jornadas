package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import ar.jornadas.data.Charla
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Consigna 41: lo que puede mostrar la pestaña Programa. */
sealed interface EstadoPrograma {
    /** Todavía no hay una edición guardada y se está descargando la primera. */
    data object Cargando : EstadoPrograma
    /** Nunca se descargó nada y la descarga falló: error + Reintentar. */
    data object SinDescarga : EstadoPrograma
    /** Hay una edición guardada (puede estar vacía). usandoGuardados = la última descarga falló. */
    data class Programa(val charlas: List<Charla>, val usandoGuardados: Boolean) : EstadoPrograma
}

class ProgramaModel(
    app: Application,
    private val guardado: SavedStateHandle      // Consigna 43: sobrevive a que Android mate el proceso
) : AndroidViewModel(app) {
    private val servicios = Servicios.obtener(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Consigna 43: los filtros viven acá, no en la pantalla. 0 = ambos días. */
    val dia: StateFlow<Int> = guardado.getStateFlow("dia", 0)
    val tema: StateFlow<String> = guardado.getStateFlow("tema", "Todos")
    fun elegirDia(valor: Int) { guardado["dia"] = valor }
    fun elegirTema(valor: String) { guardado["tema"] = valor }

    val cargando = MutableStateFlow(false)
    private val fallo = MutableStateFlow(false)
    private var descarga: Job? = null

    val estado: StateFlow<EstadoPrograma> =
        combine(repo.programa, repo.hayEdicion, fallo) { lista, hayEdicion, fallo ->
            when {
                hayEdicion -> EstadoPrograma.Programa(lista, usandoGuardados = fallo)
                fallo -> EstadoPrograma.SinDescarga
                else -> EstadoPrograma.Cargando
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, EstadoPrograma.Cargando)

    /** Consigna 44: la carga automática ocurre una sola vez por proceso (no al rotar ni al volver). */
    fun cargaInicial() {
        if (servicios.tomarCargaInicial()) recargar()
    }

    /** Consigna 44: si ya hay una descarga en curso, los toques extra no agregan otra. */
    fun recargar() {
        if (descarga?.isActive == true) return
        descarga = viewModelScope.launch {
            cargando.value = true
            fallo.value = false
            try {
                repo.sincronizar()
            } catch (e: IOException) {          // sin conexión, timeout
                fallo.value = true
            } catch (e: HttpException) {        // el servidor respondió 404, 500…
                fallo.value = true
            } finally {
                cargando.value = false
            }
        }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}