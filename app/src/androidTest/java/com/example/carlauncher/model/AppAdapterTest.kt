package com.example.carlauncher.model

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.carlauncher.R
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** AppAdapter 的 Instrumentation 测试：ViewHolder 绑定文案与点击回调分发。 */
@RunWith(AndroidJUnit4::class)
open class AppAdapterTest {

    private fun app(name: String?, packageName: String?): AppInfo? =
        AppInfo(name, packageName, null, null)

    /* 带 LayoutManager 的父容器，保证 ViewBinding 展开可用。 */
    private fun parentRecyclerView(): RecyclerView? {
        val context = ApplicationProvider.getApplicationContext<Context?>()
        val parent = RecyclerView(context!!)
        parent.setLayoutManager(GridLayoutManager(context, 3))
        return parent
    }

    @Test
    open fun getItemCount_reflectsItemListSize() {
        val adapter =
            AppAdapter(
                Arrays.asList<AppInfo?>(
                    app("Radio", "com.sample.radio"),
                    app("Navigation", "com.sample.nav"),
                )
            ) {}
        assertEquals(2, adapter.getItemCount().toLong())

        val empty = AppAdapter(emptyList<AppInfo?>()) {}
        assertEquals(0, empty.getItemCount().toLong())
    }

    @Test
    open fun onBindViewHolder_setsNameAndPackageText() {
        val parent = parentRecyclerView()
        val adapter = AppAdapter(listOf<AppInfo?>(app("Radio", "com.sample.radio"))) {}

        val holder = adapter.onCreateViewHolder(parent!!, 0)
        adapter.onBindViewHolder(holder, 0)

        val itemView = holder.itemView
        assertEquals(
            "Radio",
            (itemView.findViewById<View?>(R.id.appName) as TextView).getText().toString(),
        )
        assertEquals(
            "com.sample.radio",
            (itemView.findViewById<View?>(R.id.packageName) as TextView).getText().toString(),
        )
    }

    @Test
    open fun itemClick_dispatchesBoundAppToListener() {
        val parent = parentRecyclerView()
        val first = app("Radio", "com.sample.radio")
        val second = app("Navigation", "com.sample.nav")
        val clicked = AtomicReference<AppInfo?>()

        val adapter =
            AppAdapter(
                Arrays.asList<AppInfo?>(first, second),
                AppAdapter.OnAppClickListener { clicked.set(it) },
            )

        val holder = adapter.onCreateViewHolder(parent!!, 0)
        adapter.onBindViewHolder(holder, 0)
        assertNotNull(holder.itemView)
        holder.itemView.performClick()
        assertSame(first, clicked.get())

        adapter.onBindViewHolder(holder, 1)
        holder.itemView.performClick()
        assertSame(second, clicked.get())
    }

    @Test
    open fun itemClick_rootClickListenerIsPerItem() {
        val parent = parentRecyclerView()
        val adapter = AppAdapter(listOf<AppInfo?>(app("Only", "com.sample.only"))) {}

        val holder = adapter.onCreateViewHolder(parent!!, 0)
        adapter.onBindViewHolder(holder, 0)
        assertTrue(holder.itemView.hasOnClickListeners())
    }
}
