package com.lanxin.android.presentation

import java.io.File
import java.lang.reflect.Field
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CrashHandler.reportNonFatal] 在未 init 时不应抛；
 * init 后应写入 filesDir/logs/error-*.log。
 */
class CrashHandlerTest {

    @Test
    fun `reportNonFatal without init does not throw`() {
        setAppContext(null)
        CrashHandler.reportNonFatal("unit-test", detail = "no context")
        CrashHandler.reportNonFatal(
            "unit-test-with-error",
            error = IllegalStateException("boom"),
            detail = "should not throw"
        )
    }

    @Test
    fun `reportNonFatal writes error log after init`() {
        val tmp = createTempDir(prefix = "lanxin-crash-test-")
        try {
            setAppContext(FakeFilesContext(tmp))
            CrashHandler.reportNonFatal(
                "FloatingPetService.attachOverlay",
                error = RuntimeException("overlay fail"),
                detail = "unit"
            )
            val logs = File(tmp, "logs")
            assertTrue("logs dir should exist", logs.isDirectory)
            val errors = logs.listFiles()?.filter { it.name.startsWith("error-") }.orEmpty()
            assertTrue("should write error-*.log, got=${logs.list()?.toList()}", errors.isNotEmpty())
            val text = errors.first().readText()
            assertTrue(text.contains("FloatingPetService.attachOverlay"))
            assertTrue(text.contains("overlay fail") || text.contains("RuntimeException"))
            val rolling = File(logs, "lanxin.log")
            assertTrue(rolling.exists())
            assertTrue(rolling.readText().contains("non-fatal"))
        } finally {
            setAppContext(null)
            tmp.deleteRecursively()
        }
    }

    private fun setAppContext(ctx: android.content.Context?) {
        val f: Field = CrashHandler::class.java.getDeclaredField("appContext")
        f.isAccessible = true
        f.set(CrashHandler, ctx)
    }
}

private class FakeFilesContext(private val files: File) : android.content.ContextWrapper(null) {
    override fun getApplicationContext(): android.content.Context = this
    override fun getFilesDir(): File = files.also { it.mkdirs() }
    override fun getPackageName(): String = "com.lanxin.android.test"
}
