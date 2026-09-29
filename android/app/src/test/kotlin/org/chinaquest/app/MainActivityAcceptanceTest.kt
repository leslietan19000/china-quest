package org.chinaquest.app

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainActivityAcceptanceTest {
    private lateinit var context: Context
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var activity: MainActivity

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("china-quest.db")
        context.getSharedPreferences("parent_gate", Context.MODE_PRIVATE).edit().clear().commit()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        activity = controller.get()
    }

    @After fun tearDown() {
        controller.pause().stop().destroy()
        context.deleteDatabase("china-quest.db")
    }

    private fun view(tag: String): View = requireNotNull(activity.window.decorView.findViewWithTag<View>(tag)) {
        "Expected visible action tagged $tag"
    }
    private fun click(tag: String) { assertTrue("Click failed: $tag", view(tag).performClick()) }
    private fun type(tag: String, value: String) { (view(tag) as EditText).setText(value) }
    private fun screenHas(text: String): Boolean {
        fun contains(view: View): Boolean = when (view) {
            is TextView -> view.text.contains(text)
            is ViewGroup -> (0 until view.childCount).any { contains(view.getChildAt(it)) }
            else -> false
        }
        return contains(activity.window.decorView)
    }
    private fun configurePin() {
        assertNotNull(view("save-pin"))
        type("pin", "458726")
        type("confirm-pin", "458726")
        click("save-pin")
        assertNotNull(view("child-a"))
    }

    @Test fun childCanFinishFirstLessonAndOtherChildRemainsUntouched() {
        configurePin()
        click("child-a")
        click("start")
        val today = LocalDate.now()
        val reader = QuestStore(context)
        try {
            val tasks = reader.lesson("child-a", today).tasks
            assertTrue(tasks.any { it.kind == "LEARN" })
            assertTrue(tasks.any { it.kind == "FIND" })
            assertTrue(tasks.any { it.kind == "READ" })
            for (task in tasks) {
                when (task.kind) {
                    "LEARN" -> click("learn-done")
                    "FIND", "REVIEW" -> {
                        click("answer-${task.characterId}")
                        click("continue")
                    }
                    "READ", "WRITE" -> {
                        click("reveal")
                        click("self-correct")
                    }
                    else -> fail("Unexpected task kind ${task.kind}")
                }
            }
            click("complete")
            assertTrue(reader.status("child-a", today).completed)
            assertEquals(1, reader.stampCount("child-a"))
            assertEquals(0, reader.status("child-b", today).learned)
            assertEquals(0, reader.stampCount("child-b"))
            click("switch-child")
            click("child-b")
            assertNotNull(view("start"))
            assertEquals(0, reader.status("child-b", today).cursor)
        } finally { reader.close() }
    }

    @Test fun parentOverviewNeedsConfiguredPin() {
        configurePin()
        click("parent")
        type("parent-pin", "000000")
        click("unlock-parent")
        assertNull(activity.window.decorView.findViewWithTag<View>("settings-child-a"))
        type("parent-pin", "458726")
        click("unlock-parent")
        assertNotNull(view("settings-child-a"))
        click("lock-parent")
        assertNull(activity.window.decorView.findViewWithTag<View>("settings-child-a"))
    }

    @Test fun parentOverviewRelocksWhenAppLeavesForeground() {
        configurePin()
        click("parent")
        type("parent-pin", "458726")
        click("unlock-parent")
        assertNotNull(view("settings-child-a"))
        controller.pause().resume()
        assertNull(activity.window.decorView.findViewWithTag<View>("settings-child-a"))
        assertNotNull(view("parent-pin"))
        type("parent-pin", "458726")
        click("unlock-parent")
        assertNotNull(view("settings-child-a"))
    }

    @Test fun activityRecreationResumesSavedLessonCursor() {
        configurePin()
        click("child-a")
        click("start")
        click("learn-done")
        val reader = QuestStore(context)
        try {
            assertEquals(1, reader.lesson("child-a", LocalDate.now()).cursor)
            controller.recreate()
            activity = controller.get()
            assertNotNull(view("learn-done"))
            assertEquals(1, reader.lesson("child-a", LocalDate.now()).cursor)
            click("learn-done")
            assertEquals(2, reader.lesson("child-a", LocalDate.now()).cursor)
        } finally { reader.close() }
    }

    @Test fun wrongCharacterChoiceShowsFeedbackAndKeepsItDueToday() {
        configurePin()
        click("child-a")
        click("start")
        val reader = QuestStore(context)
        try {
            val lesson = reader.lesson("child-a", LocalDate.now())
            repeat(lesson.newCount) { click("learn-done") }
            val target = reader.card(lesson.tasks[lesson.newCount].characterId)
            val wrong = reader.cards().first { it.id != target.id && it.pinyin != target.pinyin }
            click("answer-${wrong.id}")
            assertTrue(screenHas("一起再看一次"))
            assertNotNull(view("continue"))
            click("continue")
            assertEquals(lesson.newCount + 1, reader.lesson("child-a", LocalDate.now()).cursor)
            assertEquals(1, reader.review("child-a", target.id)!!.lapses)
            assertEquals(LocalDate.now(), reader.review("child-a", target.id)!!.due)
        } finally { reader.close() }
    }

    @Test fun installedAppRequestsNoNetworkPermission() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertFalse((info.requestedPermissions ?: emptyArray()).contains("android.permission.INTERNET"))
        assertFalse((info.requestedPermissions ?: emptyArray()).contains("android.permission.ACCESS_NETWORK_STATE"))
    }
}
