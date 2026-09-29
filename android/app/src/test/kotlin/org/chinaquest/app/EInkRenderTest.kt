package org.chinaquest.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.EditText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Synthetic rendering evidence, not a claim of BOOX hardware testing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], qualifiers="w744dp-h992dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EInkRenderTest {
    @Test fun renderPickerAndFirstLearningCard() {
        val context=RuntimeEnvironment.getApplication()
        context.deleteDatabase("china-quest.db")
        context.getSharedPreferences("parent_gate",0).edit().clear().commit()
        ParentGate(context).setPin("458726")
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get()
        fun capture(name:String) {
            val view=activity.window.decorView
            view.measure(View.MeasureSpec.makeMeasureSpec(744,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(992,View.MeasureSpec.EXACTLY))
            view.layout(0,0,744,992)
            val bitmap=Bitmap.createBitmap(744,992,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file=File("build/screenshots/$name.png");file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        try {
            capture("01-profile-picker")
            activity.window.decorView.findViewWithTag<View>("child-a").performClick()
            capture("02-today")
            activity.window.decorView.findViewWithTag<View>("start").performClick()
            capture("03-character")
            activity.window.decorView.findViewWithTag<View>("pause").performClick()
            activity.window.decorView.findViewWithTag<View>("adjust-start").performClick()
            activity.window.decorView.findViewWithTag<EditText>("parent-pin").setText("458726")
            activity.window.decorView.findViewWithTag<View>("unlock-parent").performClick()
            activity.window.decorView.findViewWithTag<View>("known-page").performClick()
            capture("04-parent-placement")
            activity.window.decorView.findViewWithTag<View>("save-placement").performClick()
            activity.window.decorView.findViewWithTag<View>("lock-parent").performClick()
            activity.window.decorView.findViewWithTag<View>("child-a").performClick()
            activity.window.decorView.findViewWithTag<View>("start").performClick()
            capture("05-adjusted-start")
            val store=QuestStore(context)
            try {
                val day=java.time.LocalDate.now()
                val lesson=store.lesson("child-a",day)
                for(index in lesson.tasks.indices) {
                    if(lesson.tasks[index].kind=="WORD") break
                    store.answer("child-a",day,index,if(lesson.tasks[index].kind=="LEARN") null else org.chinaquest.core.ReviewOutcome.CORRECT)
                }
            } finally { store.close() }
            activity.window.decorView.findViewWithTag<View>("pause").performClick()
            activity.window.decorView.findViewWithTag<View>("start").performClick()
            capture("06-word-challenge")
        } finally { controller.pause().stop().destroy() }
    }
}
