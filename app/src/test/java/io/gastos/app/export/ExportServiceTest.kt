package io.gastos.app.export

import androidx.test.core.app.ApplicationProvider
import io.gastos.app.data.ExpenseRepository
import io.gastos.app.data.GastosDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ExportServiceTest {
    private val manila = ZoneId.of("Asia/Manila")
    private lateinit var db: GastosDatabase

    @Before fun setUp() {
        db = GastosDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After fun tearDown() = db.close()

    @Test fun dayExportIsOldestFirst() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = ExpenseRepository(db, Clock.system(manila), { manila }) { }
        val d = LocalDate.of(2026, 10, 16)
        val labelId = db.labelDao().all().first().id
        val cash = db.paymentMethodDao().active().first().id
        fun ts(h: Int) = d.atTime(h, 0).atZone(manila).toInstant().toEpochMilli()
        val a = repo.add(100, labelId, cash)
        val b = repo.add(200, labelId, cash)
        val c = repo.add(300, labelId, cash)
        repo.update(a, 100, labelId, cash, ts(18), null, null)
        repo.update(b, 200, labelId, cash, ts(8), null, null)
        repo.update(c, 300, labelId, cash, ts(12), null, null)
        val r = ExportService(ctx, repo, { manila }, { android.net.Uri.fromFile(it) }).exportDayFile(d, Format.CSV)
        val lines = String(File(ctx.cacheDir, "exports/${r.fileName}").readBytes(), 3, File(ctx.cacheDir, "exports/${r.fileName}").length().toInt() - 3, Charsets.UTF_8).lines()
        assertEquals(listOf("08:00", "12:00", "18:00"), lines.drop(1).take(3).map { it.split(",")[1] })
    }

    @Test fun monthXlsxColumnsFitContent() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = ExpenseRepository(db, Clock.system(manila), { manila }) { }
        val labels = io.gastos.app.data.LabelRepository(db) { }
        labels.add("Weekend groceries run", 0)
        val long = labels.all().first { it.name == "Weekend groceries run" }
        val d = LocalDate.of(2026, 10, 16)
        val cash = db.paymentMethodDao().active().first().id
        val id = repo.add(1_234_567_89, long.id, cash)
        repo.update(id, 1_234_567_89, long.id, cash, d.atTime(9, 0).atZone(manila).toInstant().toEpochMilli(), null, null)
        val r = ExportService(ctx, repo, { manila }, { android.net.Uri.fromFile(it) })
            .exportMonthFile(java.time.YearMonth.from(d), Format.XLSX)
        val parts = HashMap<String, String>()
        java.util.zip.ZipInputStream(File(ctx.cacheDir, "exports/${r.fileName}").inputStream()).use { z ->
            while (true) { val e = z.nextEntry ?: break; parts[e.name] = String(z.readBytes(), Charsets.UTF_8) }
        }
        fun widths(xml: String) = Regex("""<col min="\d+" max="\d+" width="([0-9.]+)"""").findAll(xml).map { it.groupValues[1] }.toList()
        // Expenses: date 10, "Time" 4 vs "09:00" 5, label 21, "₱1,234,567.89" 13; each + 2.
        assertEquals(listOf("12.0", "7.0", "23.0", "15.0"), widths(parts.getValue("xl/worksheets/sheet1.xml")))
        assertEquals(listOf("23.0", "15.0"), widths(parts.getValue("xl/worksheets/sheet2.xml")))
    }
}
