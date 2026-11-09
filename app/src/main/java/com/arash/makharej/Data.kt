package com.arash.makharej

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** مبلغ‌ها به ریال ذخیره می‌شوند و به تومان نمایش داده می‌شوند. */
@Entity(
    tableName = "tx",
    indices = [Index(value = ["dedupKey"], unique = true)]
)
data class Tx(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isExpense: Boolean,
    val amount: Long,
    val balance: Long?,
    val jDate: String,   // 1405/08/16
    val time: String,    // 17:21
    val ym: Int,         // 140508
    val ts: Long,
    val categoryId: Long? = null,
    val note: String = "",
    val raw: String = "",
    /** انتقال به حساب خودم: در جمع‌ها و گزارش‌ها حساب نمی‌شود */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
    /** برای جلوگیری از ثبت تکراری یک پیامک */
    val dedupKey: String? = null
)

@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
    @ColumnInfo(defaultValue = "0") val color: Int = 0
)

/** رنگ‌های قابل انتخاب برای دسته‌ها */
object Palette {
    val colors: List<Int> = listOf(
        0xFFEF5350, 0xFFFF9800, 0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFF00ACC1,
        0xFFEC407A, 0xFF6D4C41, 0xFF7CB342, 0xFF5C6BC0, 0xFFF9A825, 0xFF546E7A
    ).map { it.toInt() }

    fun at(i: Int) = colors[((i % colors.size) + colors.size) % colors.size]
}

/** توضیح‌های قبلی برای پیشنهاد */
data class NoteStat(val note: String, val categoryId: Long?, val cnt: Int)

@Dao
interface TxDao {
    @Query(
        "SELECT note, categoryId, COUNT(*) AS cnt FROM tx WHERE TRIM(note) != '' " +
            "GROUP BY note, categoryId ORDER BY cnt DESC, MAX(ts) DESC"
    )
    fun noteStats(): Flow<List<NoteStat>>

    @Query("SELECT * FROM tx ORDER BY ts")
    suspend fun list(): List<Tx>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Tx>)

    @Query("DELETE FROM tx")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: Tx): Long

    @Query("SELECT * FROM tx ORDER BY ts DESC")
    fun all(): Flow<List<Tx>>

    @Query("SELECT * FROM tx WHERE id = :id")
    suspend fun get(id: Long): Tx?

    @Query("UPDATE tx SET categoryId = :cat WHERE id = :id")
    suspend fun setCategory(id: Long, cat: Long?)

    @Query("UPDATE tx SET excluded = :ex WHERE id = :id")
    suspend fun setExcluded(id: Long, ex: Boolean)

    @Query("UPDATE tx SET categoryId = :cat, note = :note, excluded = :ex, amount = :amount WHERE id = :id")
    suspend fun update(id: Long, cat: Long?, note: String, ex: Boolean, amount: Long)

    @Query("DELETE FROM tx WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tx SET note = '' WHERE isExpense = 1 AND note = :merchant AND amount = :amount AND ts BETWEEN :from AND :to")
    suspend fun clearAutoNote(merchant: String, amount: Long, from: Long, to: Long)

    @Query("SELECT * FROM tx WHERE isExpense = :e AND jDate = :d AND time = :t")
    suspend fun findSameMinute(e: Boolean, d: String, t: String): List<Tx>

    @Query("UPDATE tx SET categoryId = NULL WHERE categoryId = :cat")
    suspend fun clearCategory(cat: Long)

    /** پرکاربردترین دسته‌ها، برای دکمه‌ی سریع نوتیفیکیشن */
    @Query(
        "SELECT categoryId FROM tx WHERE categoryId IS NOT NULL AND isExpense = 1 AND excluded = 0 " +
            "GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT 2"
    )
    suspend fun topCategoryIds(): List<Long>
}

@Dao
interface CatDao {
    @Query("SELECT * FROM Category ORDER BY sort, id")
    fun all(): Flow<List<Category>>

    @Query("SELECT * FROM Category ORDER BY sort, id")
    suspend fun list(): List<Category>

    @Insert
    suspend fun insert(c: Category): Long

    @Query("UPDATE Category SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE Category SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Int)

    @Query("DELETE FROM Category WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Category>)

    @Query("DELETE FROM Category")
    suspend fun deleteAll()
}

/** نسخه‌ی ۱ ← ۲: رنگ دسته‌ها و گزینه‌ی «حساب خودم». داده‌های قبلی حفظ می‌شوند. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tx ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Category ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
        val ids = mutableListOf<Long>()
        db.query("SELECT id FROM Category ORDER BY sort, id").use { c ->
            while (c.moveToNext()) ids.add(c.getLong(0))
        }
        ids.forEachIndexed { i, id ->
            db.execSQL("UPDATE Category SET color = ? WHERE id = ?", arrayOf<Any>(Palette.at(i), id))
        }
    }
}

/**
 * نسخه‌ی ۲ ← ۳: تشخیص تکراری بر اساس متن پیامک، و حذف تکراری‌های قبلی.
 * از بین چند نسخه‌ی یک پیامک، آنکه دسته/توضیح دارد نگه داشته می‌شود.
 */
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tx ADD COLUMN dedupKey TEXT")
        data class Row(val id: Long, val key: String, val score: Int)
        val rows = mutableListOf<Row>()
        db.query("SELECT id, raw, isExpense, amount, jDate, time, categoryId, excluded, note FROM tx").use { c ->
            while (c.moveToNext()) {
                val raw = c.getString(1) ?: ""
                val key = if (raw.isNotBlank()) Ingest.dedupKey(raw)
                else "legacy|${c.getInt(2)}|${c.getLong(3)}|${c.getString(4)}|${c.getString(5)}"
                val score = (if (!c.isNull(6)) 4 else 0) + (if (c.getInt(7) != 0) 2 else 0) +
                    (if (!c.getString(8).isNullOrEmpty()) 1 else 0)
                rows.add(Row(c.getLong(0), key, score))
            }
        }
        rows.groupBy { it.key }.forEach { (key, list) ->
            val keep = list.sortedWith(compareByDescending<Row> { it.score }.thenBy { it.id }).first()
            list.filter { it.id != keep.id }.forEach {
                db.execSQL("DELETE FROM tx WHERE id = ?", arrayOf<Any>(it.id))
            }
            db.execSQL("UPDATE tx SET dedupKey = ? WHERE id = ?", arrayOf<Any>(key, keep.id))
        }
        db.execSQL("DROP INDEX IF EXISTS `index_tx_isExpense_amount_balance_jDate_time`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tx_dedupKey` ON `tx` (`dedupKey`)")
    }
}

/**
 * نسخه‌ی ۳ ← ۴: خواندن دوباره‌ی موجودی از متن پیامک و حذف تکراری‌ها بر اساس
 * نوع + مبلغ + تاریخ + ساعت + موجودی (اگر موجودی یکی از دو مورد معلوم نباشد، باز هم تکراری حساب می‌شود).
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        data class Row(
            val id: Long, val group: String, val balance: Long?, val score: Int
        )
        val rows = mutableListOf<Row>()
        db.query("SELECT id, raw, ts, isExpense, amount, jDate, time, balance, categoryId, excluded, note FROM tx").use { c ->
            while (c.moveToNext()) {
                val raw = c.getString(1) ?: ""
                val parsed = if (raw.isNotBlank()) BluParser.parse(raw, c.getLong(2))?.balance else null
                val balance = parsed ?: if (c.isNull(7)) null else c.getLong(7)
                val score = (if (!c.isNull(8)) 4 else 0) + (if (c.getInt(9) != 0) 2 else 0) +
                    (if (!c.getString(10).isNullOrEmpty()) 1 else 0)
                rows.add(
                    Row(c.getLong(0), "${c.getInt(3)}|${c.getLong(4)}|${c.getString(5)}|${c.getString(6)}", balance, score)
                )
            }
        }
        rows.groupBy { it.group }.values.forEach { list ->
            val kept = mutableListOf<Row>()
            list.sortedWith(compareByDescending<Row> { it.score }.thenBy { it.id }).forEach { r ->
                val same = kept.any { k -> k.balance == null || r.balance == null || k.balance == r.balance }
                if (same) {
                    db.execSQL("DELETE FROM tx WHERE id = ?", arrayOf<Any>(r.id))
                } else {
                    kept.add(r)
                    if (r.balance != null) {
                        db.execSQL("UPDATE tx SET balance = ? WHERE id = ?", arrayOf<Any>(r.balance, r.id))
                    }
                }
            }
        }
    }
}

/**
 * نسخه‌ی ۴ ← ۵: حذف پیامک‌های «رمز پویا» که اشتباهی برداشت ثبت شده بودند.
 * اسم فروشگاه (و دسته/توضیحی که رویشان گذاشته بودی) به برداشت واقعی همان مبلغ منتقل می‌شود.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        data class R(val id: Long, val raw: String, val ts: Long, val amount: Long, val cat: Long?, val note: String)
        val all = mutableListOf<R>()
        db.query("SELECT id, raw, ts, amount, categoryId, note FROM tx WHERE isExpense = 1").use { c ->
            while (c.moveToNext()) {
                all.add(
                    R(
                        c.getLong(0), c.getString(1) ?: "", c.getLong(2), c.getLong(3),
                        if (c.isNull(4)) null else c.getLong(4), c.getString(5) ?: ""
                    )
                )
            }
        }
        val bogus = all.filter { it.raw.isNotBlank() && BluParser.parse(it.raw, it.ts) == null }
        val real = all.filter { r -> bogus.none { it.id == r.id } }.toMutableList()
        bogus.forEach { b ->
            val otp = BluParser.parseOtp(b.raw)
            val target = real
                .filter { it.amount == b.amount && it.ts - b.ts in -2 * 60_000L..20 * 60_000L }
                .minByOrNull { kotlin.math.abs(it.ts - b.ts) }
            if (target != null) {
                val note = target.note.ifBlank { b.note.ifBlank { otp?.merchant ?: "" } }
                val cat = target.cat ?: b.cat
                db.execSQL(
                    "UPDATE tx SET note = ?, categoryId = ? WHERE id = ?",
                    arrayOf<Any?>(note, cat, target.id)
                )
                real.remove(target)
            }
            db.execSQL("DELETE FROM tx WHERE id = ?", arrayOf<Any>(b.id))
        }
    }
}

@Database(entities = [Tx::class, Category::class], version = 5, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun tx(): TxDao
    abstract fun cat(): CatDao

    companion object {
        val DEFAULT_CATS = listOf(
            "خوراک و رستوران", "سوپرمارکت", "رفت‌وآمد", "خرید", "قبض و شارژ",
            "سلامت", "تفریح", "خانه", "سایر"
        )

        @Volatile
        private var inst: AppDb? = null

        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "makharej.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_CATS.forEachIndexed { i, n ->
                            db.execSQL(
                                "INSERT INTO Category(name, sort, color) VALUES(?, ?, ?)",
                                arrayOf<Any>(n, i, Palette.at(i))
                            )
                        }
                    }
                })
                .build().also { inst = it }
        }
    }
}
