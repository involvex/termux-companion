package com.termux.companion.ui.snippets

import com.termux.companion.data.db.SnippetDao
import com.termux.companion.data.db.SnippetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SnippetsViewModelTest {

    private class FakeSnippetDao : SnippetDao {
        val rows = MutableStateFlow<List<SnippetEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(snippet: SnippetEntity): Long {
            if (rows.value.any { it.name.equals(snippet.name, ignoreCase = true) }) return -1
            val withId = snippet.copy(id = nextId++)
            rows.value = rows.value + withId
            return withId.id
        }

        override suspend fun update(snippet: SnippetEntity) {
            rows.value = rows.value.map { if (it.id == snippet.id) snippet else it }
        }

        override fun observeAll(): Flow<List<SnippetEntity>> = rows

        override suspend fun getAll(): List<SnippetEntity> = rows.value

        override suspend fun findByName(name: String): SnippetEntity? =
            rows.value.firstOrNull { it.name.equals(name, ignoreCase = true) }

        override suspend fun deleteById(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
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

    @Test
    fun saveEditor_rejectsBlankName_inlineError() = runTest {
        val viewModel = SnippetsViewModel(FakeSnippetDao())
        viewModel.showCreateEditor()
        viewModel.onCommandChange("echo hi")

        viewModel.saveEditor()

        assertNotNull(viewModel.editor.value.nameError)
    }

    @Test
    fun saveEditor_createsNewSnippet_andClosesEditor() = runTest {
        val dao = FakeSnippetDao()
        val viewModel = SnippetsViewModel(dao)
        viewModel.showCreateEditor()
        viewModel.onNameChange("update")
        viewModel.onCommandChange("pkg upgrade -y")

        viewModel.saveEditor()

        assertEquals(listOf("update"), dao.rows.value.map { it.name })
        assertEquals("pkg upgrade -y", dao.rows.value.single().command)
        assertEquals(false, viewModel.editor.value.isVisible)
    }

    @Test
    fun saveEditor_duplicateNewName_overwritesExistingCommand() = runTest {
        val dao = FakeSnippetDao()
        val viewModel = SnippetsViewModel(dao)
        seed(viewModel, "cleanup", "rm old.log")
        viewModel.showCreateEditor()
        viewModel.onNameChange("cleanup")
        viewModel.onCommandChange("rm -rf old.log")

        viewModel.saveEditor()

        val saved = dao.rows.value.single { it.name == "cleanup" }
        assertEquals("rm -rf old.log", saved.command)
        // Still exactly one row — no duplicate created.
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun saveEditor_editExisting_updatesInPlace() = runTest {
        val dao = FakeSnippetDao()
        val viewModel = SnippetsViewModel(dao)
        seed(viewModel, "deploy", "old.sh")
        val original = dao.rows.value.single()

        viewModel.showEditEditor(original)
        viewModel.onCommandChange("new.sh")

        viewModel.saveEditor()

        val updated = dao.rows.value.single()
        assertEquals(original.id, updated.id)
        assertEquals("new.sh", updated.command)
        assertEquals("deploy", updated.name)
    }

    @Test
    fun saveEditor_renameOntoAnotherName_replacesTarget() = runTest {
        val dao = FakeSnippetDao()
        val viewModel = SnippetsViewModel(dao)
        seed(viewModel, "a", "cmd-a")
        seed(viewModel, "b", "cmd-b")
        val a = dao.rows.value.first { it.name == "a" }

        viewModel.showEditEditor(a)
        viewModel.onNameChange("b")
        viewModel.onCommandChange("cmd-a-v2")

        viewModel.saveEditor()

        val names = dao.rows.value.map { it.name }.sorted()
        assertEquals(listOf("b"), names)
        assertEquals(1, dao.rows.value.size)
        assertEquals("cmd-a-v2", dao.rows.value.single().command)
    }

    @Test
    fun delete_removesRow() = runTest {
        val dao = FakeSnippetDao()
        val viewModel = SnippetsViewModel(dao)
        seed(viewModel, "temp", "ls")
        val snippet = dao.rows.value.single()

        viewModel.delete(snippet)

        assertNull(dao.rows.value.firstOrNull())
    }

    private fun seed(viewModel: SnippetsViewModel, name: String, command: String) {
        viewModel.showCreateEditor()
        viewModel.onNameChange(name)
        viewModel.onCommandChange(command)
        viewModel.saveEditor()
    }
}
