package com.example.carlauncher.model;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.carlauncher.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * AppAdapter 的 Instrumentation 测试：ViewHolder 绑定文案与点击回调分发。
 */
@RunWith(AndroidJUnit4.class)
public class AppAdapterTest {

    private static AppInfo app(String name, String packageName) {
        return new AppInfo(name, packageName, null, null);
    }

    /** 带 LayoutManager 的父容器，保证 ViewBinding 展开可用。 */
    private static RecyclerView parentRecyclerView() {
        Context context = ApplicationProvider.getApplicationContext();
        RecyclerView parent = new RecyclerView(context);
        parent.setLayoutManager(new GridLayoutManager(context, 3));
        return parent;
    }

    @Test
    public void getItemCount_reflectsItemListSize() {
        AppAdapter adapter = new AppAdapter(
                Arrays.asList(app("Radio", "com.sample.radio"),
                        app("Navigation", "com.sample.nav")),
                ignored -> {
                });
        assertEquals(2, adapter.getItemCount());

        AppAdapter empty = new AppAdapter(Collections.emptyList(), ignored -> {
        });
        assertEquals(0, empty.getItemCount());
    }

    @Test
    public void onBindViewHolder_setsNameAndPackageText() {
        RecyclerView parent = parentRecyclerView();
        AppAdapter adapter = new AppAdapter(
                Collections.singletonList(app("Radio", "com.sample.radio")),
                ignored -> {
                });

        AppAdapter.AppViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);

        View itemView = holder.itemView;
        assertEquals("Radio", ((TextView) itemView.findViewById(R.id.appName))
                .getText().toString());
        assertEquals("com.sample.radio",
                ((TextView) itemView.findViewById(R.id.packageName))
                        .getText().toString());
    }

    @Test
    public void itemClick_dispatchesBoundAppToListener() {
        RecyclerView parent = parentRecyclerView();
        AppInfo first = app("Radio", "com.sample.radio");
        AppInfo second = app("Navigation", "com.sample.nav");
        AtomicReference<AppInfo> clicked = new AtomicReference<>();

        AppAdapter adapter = new AppAdapter(Arrays.asList(first, second), clicked::set);

        AppAdapter.AppViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        assertNotNull(holder.itemView);
        holder.itemView.performClick();
        assertSame(first, clicked.get());

        adapter.onBindViewHolder(holder, 1);
        holder.itemView.performClick();
        assertSame(second, clicked.get());
    }

    @Test
    public void itemClick_rootClickListenerIsPerItem() {
        RecyclerView parent = parentRecyclerView();
        AppAdapter adapter = new AppAdapter(
                Collections.singletonList(app("Only", "com.sample.only")),
                ignored -> {
                });

        AppAdapter.AppViewHolder holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);
        assertTrue(holder.itemView.hasOnClickListeners());
    }
}

