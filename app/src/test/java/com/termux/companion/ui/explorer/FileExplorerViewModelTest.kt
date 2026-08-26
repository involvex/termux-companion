package com.termux.companion.ui.explorer

import com.termux.companion.data.termux.TermuxCommandRunner
import com.termux.companion.domain.model.CommandResult
import com.termux.companion.domain.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileExplorerViewModelTest {

    private class FakeTermuxCommandRunner : TermuxCommandRunner {
        val executedCommands = mutableListOf<String>()
        var installed = true
        var permitted = true

        override fun isTermuxInstalled() = installed
        override fun hasRunCommandPermission() = permitted
        override fun canReadSharedStorage() = true

        override suspend fun execute(
            command: String,
            workdir: String,
            timeoutMs: Long
        ): CommandResult {
            executedCommands.add(command)
            return if (command.startsWith("ls ")) {
                CommandResult(command, LS_OUTPUT, "", 0)
            } else {
                CommandResult(command, "", "", 0)
            }
        }

        override fun executeCommandNoResult(command: String, workdir: String) {
            executedCommands.add(command)
        }

        companion object {
            const val HOME = "/data/data/com.termux/files/home"
            val LS_OUTPUT = """
                total 0
                -rw-r--r-- 1 root root 10 Aug 26 10:00 a.txt
                -rw-r--r-- 1 root root 20 Aug 26 10:00 b.txt
            """.trimIndent()
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun homeFile(name: String) = FileItem(
        name = name,
        path = "${FakeTermuxCommandRunner.HOME}/$name",
        isDirectory = false
    )

    private fun mvCommands(runner: FakeTermuxCommandRunner) =
        runner.executedCommands.filter { it.startsWith("mv ") }

    @Test
    fun rename_requestsConfirmation_whenTargetNameExists() = runTest {
        val runner = FakeTermuxCommandRunner()
        val viewModel = FileExplorerViewModel(runner)

        viewModel.rename(homeFile("a.txt"), "b.txt")

        val request = viewModel.pendingOverwrite.value
        assertNotNull(request)
        assertTrue(request!!.title.contains("b.txt"))
        assertTrue(mvCommands(runner).isEmpty())
    }

    @Test
    fun confirmedRename_executesMove_andClearsRequest() = runTest {
        val runner = FakeTermuxCommandRunner()
        val viewModel = FileExplorerViewModel(runner)

        viewModel.rename(homeFile("a.txt"), "b.txt")
        val request = viewModel.pendingOverwrite.value!!
        viewModel.dismissOverwrite()
        request.onProceed()

        val expected = "mv '${FakeTermuxCommandRunner.HOME}/a.txt' '${FakeTermuxCommandRunner.HOME}/b.txt'"
        assertTrue(mvCommands(runner).contains(expected))
        assertEquals("Renamed to b.txt", viewModel.message.value)
        assertNull(viewModel.pendingOverwrite.value)
    }

    @Test
    fun dismissedOverwrite_cancelsRename_withoutExecuting() = runTest {
        val runner = FakeTermuxCommandRunner()
        val viewModel = FileExplorerViewModel(runner)

        viewModel.rename(homeFile("a.txt"), "b.txt")
        viewModel.dismissOverwrite()

        assertNull(viewModel.pendingOverwrite.value)
        assertTrue(mvCommands(runner).isEmpty())
    }

    @Test
    fun renameWithoutConflict_executesImmediately() = runTest {
        val runner = FakeTermuxCommandRunner()
        val viewModel = FileExplorerViewModel(runner)

        viewModel.rename(homeFile("a.txt"), "renamed.txt")

        assertNull(viewModel.pendingOverwrite.value)
        assertTrue(mvCommands(runner).size == 1)
    }

    @Test
    fun renameToDotDot_isRejected_withoutAnyCommand() = runTest {
        val runner = FakeTermuxCommandRunner()
        val viewModel = FileExplorerViewModel(runner)

        viewModel.rename(homeFile("a.txt"), "..")

        assertNull(viewModel.pendingOverwrite.value)
        assertTrue(mvCommands(runner).isEmpty())
        assertNotNull(viewModel.error.value)
    }

    @Test
    fun pasteCut_requestsConfirmation_whenNameConflicts_andClearsClipboardAfterProceed() = runTest {
        val runner = FakeTermuxCommandRunner()
        val viewModel = FileExplorerViewModel(runner)

        viewModel.copyToClipboard(
            FileItem(name = "b.txt", path = "/sdcard/Download/b.txt", isDirectory = false),
            cut = true
        )
        viewModel.paste()
        assertNotNull(viewModel.pendingOverwrite.value)
        assertTrue(mvCommands(runner).isEmpty())

        val request = viewModel.pendingOverwrite.value!!
        viewModel.dismissOverwrite()
        request.onProceed()

        val expected = "mv '/sdcard/Download/b.txt' '${FakeTermuxCommandRunner.HOME}/b.txt'"
        assertTrue(mvCommands(runner).contains(expected))
        assertNull(viewModel.clipboard.value)
    }

    @Test
    fun pasteCopy_withoutConflict_runsImmediately_andKeepsClipboard() = runTest {
        val runner = FakeTermuxCommandRunner()
        val viewModel = FileExplorerViewModel(runner)

        viewModel.copyToClipboard(
            FileItem(name = "z.txt", path = "/sdcard/Download/z.txt", isDirectory = false),
            cut = false
        )
        viewModel.paste()

        assertNull(viewModel.pendingOverwrite.value)
        val expected = "cp -a '/sdcard/Download/z.txt' '${FakeTermuxCommandRunner.HOME}/z.txt'"
        assertTrue(runner.executedCommands.contains(expected))
        assertNotNull(viewModel.clipboard.value)
    }

    @Test
    fun createFolder_dotDot_rejected_withoutCommand() = runTest {
        val runner = FakeTermuxCommandRunner()
        val viewModel = FileExplorerViewModel(runner)

        viewModel.createFolder("..")

        assertFalse(runner.executedCommands.any { it.startsWith("mkdir") })
        assertNotNull(viewModel.error.value)
    }
}
