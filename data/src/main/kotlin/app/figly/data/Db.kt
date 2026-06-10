package app.figly.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A pressed week. Frozen forever at the first event after Sunday 23:59 —
 * the live week is always recomputed from readings, never cached stale.
 */
@Entity(tableName = "weeks")
data class WeekEntity(
    @PrimaryKey val isoWeek: String,
    val seed: Long,
    val pressedAt: Long,
    /** When Loom played the pressing ceremony; null until witnessed. */
    val ceremonyPlayedAt: Long?,
    val seasonName: String,
    val seasonTint: String,
    val statsJson: String,
    val cellsJson: String,
    /** Coarse city string at press time, or null → "—". */
    val collected: String?,
)

/**
 * One day's sealed readings — or its explicit scar.
 * Times are local; the zone offset rides along so travel doesn't corrupt weeks.
 */
@Entity(tableName = "day_readings")
data class DayReadingEntity(
    /** ISO local date, yyyy-MM-dd. */
    @PrimaryKey val date: String,
    val isoWeek: String,
    /** Monday = 1 .. Sunday = 7. */
    val dayIndex: Int,
    /** True when the day was scarred (by choice or lapsed grace). */
    val missed: Boolean,
    val mood: Int,
    val bedMinutesAfterNoon: Int,
    val durationMin: Int,
    val effort: Int,
    val underBudget: Boolean,
    val sealedAt: Long,
    val zoneOffsetMin: Int,
)

@Dao
interface WeekDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(week: WeekEntity): Long

    @Query("SELECT * FROM weeks ORDER BY isoWeek DESC")
    fun observeAll(): Flow<List<WeekEntity>>

    @Query("SELECT * FROM weeks ORDER BY isoWeek DESC")
    suspend fun all(): List<WeekEntity>

    @Query("SELECT * FROM weeks WHERE isoWeek = :key")
    suspend fun byKey(key: String): WeekEntity?

    @Query("SELECT * FROM weeks WHERE ceremonyPlayedAt IS NULL ORDER BY isoWeek DESC LIMIT 1")
    suspend fun pendingCeremony(): WeekEntity?

    @Query("UPDATE weeks SET ceremonyPlayedAt = :at WHERE isoWeek = :key")
    suspend fun markCeremonyPlayed(key: String, at: Long)

    @Query("DELETE FROM weeks WHERE isoWeek = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM weeks")
    suspend fun deleteAll()
}

@Dao
interface DayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: DayReadingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(day: DayReadingEntity): Long

    @Query("SELECT * FROM day_readings WHERE isoWeek = :week ORDER BY dayIndex")
    suspend fun byWeek(week: String): List<DayReadingEntity>

    @Query("SELECT * FROM day_readings WHERE isoWeek = :week ORDER BY dayIndex")
    fun observeWeek(week: String): Flow<List<DayReadingEntity>>

    @Query("SELECT * FROM day_readings WHERE date = :date")
    suspend fun byDate(date: String): DayReadingEntity?

    @Query("DELETE FROM day_readings")
    suspend fun deleteAll()
}

@Database(
    entities = [WeekEntity::class, DayReadingEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FiglyDb : RoomDatabase() {
    abstract fun weeks(): WeekDao
    abstract fun days(): DayDao

    companion object {
        @Volatile private var instance: FiglyDb? = null

        fun get(context: Context): FiglyDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FiglyDb::class.java,
                    "figly.db",
                ).build().also { instance = it }
            }
    }
}
