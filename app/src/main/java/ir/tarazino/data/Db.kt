package ir.tarazino.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "accounts")
data class AccountEntity(@PrimaryKey val id: String, val name: String, val openingBalance: Long)

/** idempotencyKey یکتا است؛ تکرار درخواست با IGNORE نادیده گرفته می‌شود. هر قسط وام هم فقط یک‌بار ثبت می‌شود. */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["idempotencyKey"], unique = true), Index(value = ["loanId", "installmentNo"], unique = true), Index("accountId"), Index("epochDay")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val idempotencyKey: String,
    val type: String,
    val amount: Long,
    val accountId: String,
    val toAccountId: String?,
    val epochDay: Long,
    val categoryId: String?,
    val note: String,
    val loanId: String?,
    val installmentNo: Int?,
)

@Entity(tableName = "budgets") data class BudgetEntity(@PrimaryKey val id: String, val category: String, val monthlyLimit: Long, val warnPercent: Int)
@Entity(tableName = "goals") data class GoalEntity(@PrimaryKey val id: String, val name: String, val target: Long, val deadlineEpochDay: Long?)

/** واریز به هدف فقط «اختصاص» است: موجودی نقدی را تغییر نمی‌دهد و درآمد/هزینه/انتقال حساب نمی‌شود. */
@Entity(tableName = "goal_deposits", indices = [Index(value = ["idempotencyKey"], unique = true), Index("goalId")])
data class GoalDepositEntity(@PrimaryKey val id: String, val goalId: String, val amount: Long, val epochDay: Long, val idempotencyKey: String)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey val id: String, val title: String, val presetId: String, val principal: Long, val months: Int,
    val ratePercent: Double, val method: String, val givenInstallment: Long?, val firstDueEpochDay: Long,
)

@Entity(tableName = "cheques") data class ChequeEntity(@PrimaryKey val id: String, val amount: Long, val dueEpochDay: Long, val accountId: String, val status: String, val note: String)

@Entity(tableName = "obligations")
data class ObligationEntity(
    @PrimaryKey val id: String, val counterparty: String, val amount: Long, val isReceivable: Boolean,
    val dueEpochDay: Long?, val status: String, val note: String,
)

@Dao
interface TarazinoDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAccount(a: AccountEntity): Long

    /** برمی‌گرداند -1 اگر تکراری بود (چیزی ثبت نشد). */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTransaction(t: TransactionEntity): Long
    @Update suspend fun updateTransaction(t: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTransaction(id: String)
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM transactions ORDER BY epochDay DESC") suspend fun transactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBudget(b: BudgetEntity)
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: String)
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGoal(g: GoalEntity)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: String)
    @Query("DELETE FROM goal_deposits WHERE goalId = :id") suspend fun deleteGoalDeposits(id: String)
    @Query("SELECT * FROM goals") suspend fun goals(): List<GoalEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertDeposit(d: GoalDepositEntity): Long
    @Query("SELECT * FROM goal_deposits") suspend fun deposits(): List<GoalDepositEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLoan(l: LoanEntity)
    @Query("DELETE FROM loans WHERE id = :id") suspend fun deleteLoan(id: String)
    @Query("SELECT * FROM loans") suspend fun loans(): List<LoanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCheque(c: ChequeEntity)
    @Query("DELETE FROM cheques WHERE id = :id") suspend fun deleteCheque(id: String)
    @Query("SELECT * FROM cheques ORDER BY dueEpochDay") suspend fun cheques(): List<ChequeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertObligation(o: ObligationEntity)
    @Query("DELETE FROM obligations WHERE id = :id") suspend fun deleteObligation(id: String)
    @Query("SELECT * FROM obligations ORDER BY dueEpochDay") suspend fun obligations(): List<ObligationEntity>
}

@Database(
    entities = [AccountEntity::class, TransactionEntity::class, BudgetEntity::class, GoalEntity::class, GoalDepositEntity::class, LoanEntity::class, ChequeEntity::class, ObligationEntity::class],
    version = 1, exportSchema = false,
)
abstract class TarazinoDb : RoomDatabase() {
    abstract fun dao(): TarazinoDao

    companion object {
        @Volatile private var inst: TarazinoDb? = null
        fun get(ctx: Context): TarazinoDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, TarazinoDb::class.java, "tarazino.db").build().also { inst = it }
        }
    }
}
