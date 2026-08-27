package com.example.carlauncher;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.carlauncher.databinding.ActivityAppDrawerBinding;
import com.example.carlauncher.model.AppAdapter;
import com.example.carlauncher.model.AppInfo;

import java.util.ArrayList;
import java.util.List;

public class AppDrawerActivity extends AppCompatActivity {
    private static final String TAG = "APP_COMPACT";

    private ActivityAppDrawerBinding binding; // 视图绑定

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "on Create ");
        super.onCreate(savedInstanceState);


        // 初始化视图绑定 (ViewBinding)
        binding = ActivityAppDrawerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        EdgeToEdge.enable(this);

        ViewCompat.setOnApplyWindowInsetsListener(binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        binding.backButton.setOnClickListener(v -> finish());
        List<AppInfo> apps = loadInstalledApps();
        AppAdapter adapter = new AppAdapter(apps, app -> {
            Log.i(
                    TAG,
                    "Launching app: "
                            + app.getName()
                            + ", package="
                            + app.getPackageName()
            );

            Intent launchIntent = app.getLaunchIntent();

            if (launchIntent == null) {
                Log.e(TAG, "Launch Intent is null: " + app.getPackageName());

                Toast.makeText(
                        this,
                        "Unable to launch " + app.getName(),
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            try {
                startActivity(launchIntent);
            } catch (ActivityNotFoundException exception) {
                Log.e(
                        TAG,
                        "Unable to launch " + app.getPackageName(),
                        exception
                );

                Toast.makeText(
                        this,
                        "Unable to launch " + app.getName(),
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
        binding.appRecyclerView.setLayoutManager(
                new GridLayoutManager(this, 3)
        );
        binding.appRecyclerView.setAdapter(adapter);

    }

    private List<AppInfo> loadInstalledApps() {
        Intent queryIntent = new Intent(Intent.ACTION_MAIN);
        queryIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        // 继续调用 PackageManager 查询
        PackageManager packageManager = getPackageManager();

        List<ResolveInfo> resolveInfos =
                packageManager.queryIntentActivities(
                        queryIntent,
                        PackageManager.MATCH_ALL
                );
        List<AppInfo> apps = new ArrayList<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            String name =
                    resolveInfo.loadLabel(packageManager).toString();
            String packageName =
                    resolveInfo.activityInfo.packageName;
            // 不把 CarLauncher 自己放入应用抽屉
            if (getPackageName().equals(packageName)) {
                continue;
            }
            Drawable icon =
                    resolveInfo.loadIcon(packageManager);

            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setClassName(
                    packageName,
                    resolveInfo.activityInfo.name
            );
            apps.add(new AppInfo(
                    name,
                    packageName,
                    icon,
                    launchIntent
            ));
            Log.i(
                    TAG,
                    "Found app: " + name
                            + ", package=" + packageName
                            + ", activity=" + resolveInfo.activityInfo.name
            );

        }
        apps.sort(
                (left, right) ->
                        left.getName().compareToIgnoreCase(right.getName())
        );
        return apps;
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "on Destroy");

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        startVehicleRefresh();
        Log.i(TAG, "AppDrawerActivity  onResume");
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }

    @Override
    protected void onRestart() {
        Log.i(TAG, "onRestart");
        super.onRestart();
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }

}