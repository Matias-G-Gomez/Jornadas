package ar.jornadas.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
                       val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
                  val hora: String, val sala: String, val ponente: String, val resumen: String,
                  val favorita: Boolean = false)

/** Consigna 41: una sola fila que existe si alguna descarga salió bien (aunque el programa venga vacío). */
@Entity(tableName = "edicion")
data class Edicion(@PrimaryKey val id: Int = 0, val descargadaEn: Long)

@Dao
abstract class CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    abstract fun observar(): Flow<List<Charla>>

    /** true si hay una edición guardada. Distingue "nunca descargué" de "descargué un programa vacío". */
    @Query("SELECT EXISTS(SELECT 1 FROM edicion)")
    abstract fun observarHayEdicion(): Flow<Boolean>

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    abstract suspend fun alternar(id: String)

    @Query("DELETE FROM charlas WHERE id NOT IN (:ids)")
    protected abstract suspend fun borrarLasQueNoVienen(ids: List<String>)

    @Query("DELETE FROM charlas")
    protected abstract suspend fun borrarTodas()

    /** Solo inserta las charlas nuevas; las que ya existen se ignoran (así no se pisa la marca). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertarNuevas(fichas: List<Charla>)

    /** Actualiza los datos de una charla existente sin tocar la columna favorita. */
    @Query("""UPDATE charlas SET titulo = :titulo, tema = :tema, dia = :dia, hora = :hora,
              sala = :sala, ponente = :ponente, resumen = :resumen WHERE id = :id""")
    protected abstract suspend fun actualizarDatos(id: String, titulo: String, tema: String, dia: Int,
                                                   hora: String, sala: String, ponente: String, resumen: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun registrarEdicion(edicion: Edicion)

    /**
     * Reemplaza el programa por la edición descargada, todo o nada.
     * Una lista vacía (v4) también es una edición válida: borra todo y queda registrada.
     */
    @Transaction
    open suspend fun reemplazarPrograma(fichas: List<Charla>, descargadaEn: Long) {
        if (fichas.isEmpty()) borrarTodas() else borrarLasQueNoVienen(fichas.map { it.id })
        insertarNuevas(fichas)
        fichas.forEach {
            actualizarDatos(it.id, it.titulo, it.tema, it.dia, it.hora, it.sala, it.ponente, it.resumen)
        }
        registrarEdicion(Edicion(descargadaEn = descargadaEn))
    }
}

@Database(entities = [Charla::class, Edicion::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao
}

/** v1 → v2: crea la tabla edicion sin borrar nada. Si ya había charlas guardadas, cuentan como edición. */
val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `edicion` (`id` INTEGER NOT NULL, `descargadaEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("INSERT INTO edicion (id, descargadaEn) SELECT 0, 0 WHERE EXISTS (SELECT 1 FROM charlas)")
    }
}