package com.mmgpt.shoplistgpt

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.mmgpt.shoplistgpt", appContext.packageName)
    }

    @Test
    fun categoryRecyclerViewPerformsClickAndNotifiesListener() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var clickPerformed = false
        var clickListenerCalled = false

        instrumentation.runOnMainSync {
            val recyclerView = ClickableRecyclerView(context)
            recyclerView.setOnClickListener {
                clickListenerCalled = true
            }
            clickPerformed = recyclerView.performClick()
        }

        assertTrue(clickPerformed)
        assertTrue(clickListenerCalled)
    }
}