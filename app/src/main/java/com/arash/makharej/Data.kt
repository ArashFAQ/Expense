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
    val dedupKey: String? = null,
    /** BLU / MELLAT / EN / PASARGAD / MANUAL */
    val bank: String = Banks.BLU,
    /** ۰ عادی، ۱ انتقال بین حساب‌ها، ۲ طبق نقش حساب، ۳ پس‌انداز — ببین Banks */
    @ColumnInfo(defaultValue = "0") val reason: Int = 0,
    /** این واریز بازپرداخت دنگِ کدام خرج است (reason = 4) */
    val refundOf: Long? = null,
    /** خرج دنگی (با دیگران تقسیم شده) */
    @ColumnInfo(defaultValue = "0") val shared: Boolean = false,
    /** قرض: به چه کسی (reason = 6) */
    val person: String? = null
)

@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sort: Int = 0,
    @ColumnInfo(defaultValue = "0") val color: Int = 0,
    /** کلید آیکون (CatIcons) */
    val icon: String = ""
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

    @Query("DELETE FROM tx WHERE bank = :bank")
    suspend fun deleteByBank(bank: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: Tx): Long

    /** ردیف‌های دستی ساخته‌شده از اضافه‌ی یک واریز (dedupKey = excess-<id>-…) */
    @Query("DELETE FROM tx WHERE dedupKey LIKE :prefix || '%'")
    suspend fun deleteByKeyPrefix(prefix: String)

    @Query("SELECT * FROM tx ORDER BY ts DESC")
    fun all(): Flow<List<Tx>>

    @Query("SELECT * FROM tx WHERE id = :id")
    suspend fun get(id: Long): Tx?

    @Query("UPDATE tx SET categoryId = :cat WHERE id = :id")
    suspend fun setCategory(id: Long, cat: Long?)

    @Query("UPDATE tx SET excluded = :ex, reason = 0 WHERE id = :id")
    suspend fun setExcluded(id: Long, ex: Boolean)

    @Query("UPDATE tx SET excluded = 1, reason = 2 WHERE bank = :bank AND isExpense = :e AND excluded = 0 AND reason = 0")
    suspend fun setRoleIgnore(bank: String, e: Boolean)

    @Query("UPDATE tx SET excluded = 0, reason = 0 WHERE bank = :bank AND isExpense = :e AND reason = 2")
    suspend fun clearRoleIgnore(bank: String, e: Boolean)

    @Query(
        "SELECT * FROM tx WHERE isExpense = :e AND bank != :notBank AND bank != 'MANUAL' AND reason NOT IN (1, 3, 6, 7, 8, 9) " +
            "AND ts BETWEEN :from AND :to"
    )
    suspend fun transferCandidates(e: Boolean, notBank: String, from: Long, to: Long): List<Tx>

    /** درخواست وجه مفید که برگشت خورد (reason = 10): هیچ‌جا حساب نمی‌شود */
    @Query("UPDATE tx SET reason = 10, excluded = 1, refundOf = NULL WHERE id = :id")
    suspend fun markBounced(id: Long)

    /** برداشت‌های «پس‌انداز» (پرداخت با رمز پویا به مفید) در یک بازه */
    @Query("SELECT * FROM tx WHERE isExpense = 1 AND reason = 3 AND ts BETWEEN :from AND :to")
    suspend fun savingsOut(from: Long, to: Long): List<Tx>

    @Query("UPDATE tx SET excluded = 1, reason = 1 WHERE id IN (:ids)")
    suspend fun markTransfer(ids: List<Long>)

    /** نوع واریز: درآمد (۰، حساب می‌شود) یا حساب خودم (۰، حساب نمی‌شود) */
    @Query("UPDATE tx SET reason = 0, excluded = :own WHERE id = :id")
    suspend fun setDepositKind(id: Long, own: Boolean)

    @Query("SELECT * FROM tx WHERE isExpense = 0 AND reason = 5 AND ym = :ym")
    suspend fun pendingIn(ym: Int): List<Tx>

    @Query(
        "UPDATE tx SET reason = 5, excluded = 1 WHERE isExpense = 0 AND bank NOT IN ('MANUAL', 'MOFID') " +
            "AND reason IN (0, 2) AND categoryId IS NULL AND TRIM(note) = ''"
    )
    suspend fun migrateDepositsToPendingRaw()

    @Query("UPDATE tx SET reason = 0, excluded = 0 WHERE isExpense = 0 AND bank = 'MOFID' AND note = 'سود'")
    suspend fun mofidProfitIncome()

    @Query("SELECT * FROM tx WHERE isExpense = 0 AND bank != :notBank AND amount = :amount AND ts BETWEEN :from AND :to")
    suspend fun depositsWithAmount(amount: Long, notBank: String, from: Long, to: Long): List<Tx>

    @Query("SELECT * FROM tx WHERE bank = 'MOFID' AND isExpense = 1 AND amount = :amount AND ts BETWEEN :from AND :to AND reason != 10 AND note LIKE 'درخواست وجه%'")
    suspend fun mofidRequests(amount: Long, from: Long, to: Long): List<Tx>

    @Query(
        "UPDATE tx SET categoryId = :cat, note = :note, amount = :amount, " +
            "reason = CASE WHEN excluded = :ex THEN reason ELSE 0 END, excluded = :ex WHERE id = :id"
    )
    suspend fun update(id: Long, cat: Long?, note: String, ex: Boolean, amount: Long)

    @Query("DELETE FROM tx WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tx SET refundOf = :expenseId, excluded = 1, reason = 4 WHERE id = :id")
    suspend fun setRefund(id: Long, expenseId: Long)

    /** اتصال واریز برداشته می‌شود و دوباره «در انتظار تأیید» است */
    @Query("UPDATE tx SET refundOf = NULL, excluded = 1, reason = 5 WHERE id = :id")
    suspend fun clearRefund(id: Long)

    /** قرض دادم (reason = 6): خرج حساب نمی‌شود، طلب است */
    @Query("UPDATE tx SET reason = 6, excluded = 1, person = :person, categoryId = NULL WHERE id = :id")
    suspend fun setLoan(id: Long, person: String)

    @Query("UPDATE tx SET reason = 0, excluded = 0, person = NULL WHERE id = :id")
    suspend fun clearLoan(id: Long)

    /** قرض گرفتم (reason = 8): واریز درآمد نیست، بدهی است */
    @Query("UPDATE tx SET reason = 8, excluded = 1, person = :person, categoryId = NULL, refundOf = NULL WHERE id = :id")
    suspend fun setDebt(id: Long, person: String)

    /** «قرض نگرفتم»: واریز دوباره در انتظار تأیید */
    @Query("UPDATE tx SET reason = 5, excluded = 1, person = NULL WHERE id = :id")
    suspend fun clearDebt(id: Long)

    /** پرداخت بدهی (reason = 9) */
    @Query("UPDATE tx SET refundOf = :debtId, excluded = 1, reason = 9, categoryId = NULL WHERE id = :id")
    suspend fun setDebtRepay(id: Long, debtId: Long)

    /** برداشتی که دیگر پرداخت بدهی نیست، خرج عادی می‌شود */
    @Query("UPDATE tx SET refundOf = NULL, excluded = 0, reason = 0 WHERE id = :id")
    suspend fun clearRepayOut(id: Long)

    /** بازپرداخت قرض (reason = 7) */
    @Query("UPDATE tx SET refundOf = :loanId, excluded = 1, reason = 7 WHERE id = :id")
    suspend fun setLoanRepay(id: Long, loanId: Long)

    @Query("UPDATE tx SET shared = :shared WHERE id = :id")
    suspend fun setShared(id: Long, shared: Boolean)

    @Query("SELECT * FROM tx WHERE refundOf = :expenseId")
    suspend fun refundsOf(expenseId: Long): List<Tx>

    @Query("SELECT * FROM tx WHERE isExpense = 1 AND shared = 1 AND ts >= :since")
    suspend fun sharedSince(since: Long): List<Tx>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM tx WHERE refundOf = :expenseId")
    suspend fun refundSum(expenseId: Long): Long

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

    @Query("UPDATE Category SET icon = :icon WHERE id = :id")
    suspend fun setIcon(id: Long, icon: String)

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

/** نسخه‌ی ۵ ← ۶: چند بانک، نقش حساب‌ها، انتقال بین حساب‌ها، پس‌انداز؛ دسته‌ی «قسط» */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tx ADD COLUMN bank TEXT NOT NULL DEFAULT 'BLU'")
        db.execSQL("ALTER TABLE tx ADD COLUMN reason INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE tx SET bank = 'MANUAL' WHERE dedupKey LIKE 'manual-%'")
        var exists = false
        db.query("SELECT COUNT(*) FROM Category WHERE name = 'قسط'").use { c -> if (c.moveToFirst()) exists = c.getInt(0) > 0 }
        if (!exists) {
            var sort = 0
            db.query("SELECT COALESCE(MAX(sort), 0) + 1 FROM Category").use { c -> if (c.moveToFirst()) sort = c.getInt(0) }
            db.execSQL(
                "INSERT INTO Category(name, sort, color) VALUES(?, ?, ?)",
                arrayOf<Any>("قسط", sort, 0xFF6D4C41.toInt())
            )
        }
    }
}

/** نسخه‌ی ۶ ← ۷: آیکون دسته‌ها (از روی اسم حدس زده می‌شود) */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Category ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
        val rows = mutableListOf<Pair<Long, String>>()
        db.query("SELECT id, name FROM Category").use { c -> while (c.moveToNext()) rows.add(c.getLong(0) to c.getString(1)) }
        rows.forEach { (id, name) ->
            db.execSQL("UPDATE Category SET icon = ? WHERE id = ?", arrayOf<Any>(CatIcons.guess(name), id))
        }
    }
}

/** نسخه‌ی ۷ ← ۸: دنگ (اتصال بازپرداخت به خرج) */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tx ADD COLUMN refundOf INTEGER")
        db.execSQL("ALTER TABLE tx ADD COLUMN shared INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * دارایی غیربانکی: دلار، سکه، صندوق، پول نقد، …
 * kind: usd / emami / nim / rob / gerami / fund / cash / other
 * qty برای دلار و سکه؛ value (ریال) برای صندوق/نقد/سایر؛ buyTotal (ریال) بهای خرید برای حساب سود (۰ = نامعلوم)
 */
@Entity
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val qty: Double = 0.0,
    val buyTotal: Long = 0,
    val value: Long = 0,
    val sort: Int = 0
)

@Dao
interface AssetDao {
    @Query("SELECT * FROM Asset ORDER BY sort, id")
    fun all(): Flow<List<Asset>>

    @Query("SELECT * FROM Asset ORDER BY sort, id")
    suspend fun list(): List<Asset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(a: Asset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Asset>)

    @Query("DELETE FROM Asset WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Asset")
    suspend fun deleteAll()
}

/** نسخه‌ی ۸ ← ۹: جدول دارایی‌ها */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `Asset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `qty` REAL NOT NULL, `buyTotal` INTEGER NOT NULL, `value` INTEGER NOT NULL, `sort` INTEGER NOT NULL)"
        )
    }
}

/** نسخه‌ی ۹ ← ۱۰: قرض (طرف قرض) */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tx ADD COLUMN person TEXT")
    }
}

@Database(entities = [Tx::class, Category::class, Asset::class], version = 10, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun tx(): TxDao
    abstract fun cat(): CatDao
    abstract fun asset(): AssetDao

    companion object {
        val DEFAULT_CATS = listOf(
            "خوراک و رستوران", "سوپرمارکت", "رفت‌وآمد", "خرید", "قبض و شارژ",
            "سلامت", "تفریح", "خانه", "قسط", "سایر"
        )

        @Volatile
        private var inst: AppDb? = null

        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "makharej.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_CATS.forEachIndexed { i, n ->
                            db.execSQL(
                                "INSERT INTO Category(name, sort, color, icon) VALUES(?, ?, ?, ?)",
                                arrayOf<Any>(n, i, Palette.at(i), CatIcons.guess(n))
                            )
                        }
                    }
                })
                .build().also { inst = it }
        }
    }
}
