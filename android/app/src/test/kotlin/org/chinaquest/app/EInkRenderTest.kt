package org.chinaquest.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
        } finally { controller.pause().stop().destroy() }
    }
}
