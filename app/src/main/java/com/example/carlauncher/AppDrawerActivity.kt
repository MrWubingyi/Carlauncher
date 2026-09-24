package com.example.carlauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.carlauncher.databinding.ActivityAppDrawerBinding
import com.example.carlauncher.model.AppAdapter
import com.example.carlauncher.model.AppInfo
import java.util.ArrayList

open class AppDrawerActivity : AppCompatActivity() {

    private var binding: ActivityAppDrawerBinding? = null // 视图绑定
    private var createTime: Int? = 0

    protected override fun onCreate(savedInstanceState: Bundle?) {
        Log.i(TAG, "on Create ")
        super.onCreate(savedInstanceState)

        // 初始化视图绑定 (ViewBinding)
        binding = ActivityAppDrawerBinding.inflate(getLayoutInflater()!!)
        setContentView(binding!!.getRoot())

        enableEdgeToEdge()

        ViewCompat.setOnApplyWindowInsetsListener(binding!!.main) { v, insets ->
            val systemBars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars())
            v!!.setPadding(
                systemBars!!.left,
                systemBars!!.top,
                systemBars!!.right,
                systemBars!!.bottom,
            )
            insets
        }
        binding!!.backButton.setOnClickListener { finish() }
        binding!!.clickButton.setOnClickListener {
            createTime = 5
            binding!!.text.setText(createTime!!.toString())
        }
        if (savedInstanceState != null) {
            createTime = savedInstanceState!!.getInt("test_count", 0)
            Log.i(TAG, "onCreate, restored=true")
        }
        val apps = loadInstalledApps()
        val adapter =
            AppAdapter(
                apps,
                appClick@{ app ->
                    Log.i(
                        TAG,
                        ("Launching app: " + app!!.name + ", package=" + app!!.packageName),
                    )

                    val launchIntent = app!!.launchIntent

                    if (launchIntent == null) {
                        Log.e(TAG, "Launch Intent is null: " + app!!.packageName!!)

                        Toast.makeText(
                                this,
                                getString(R.string.launch_failed, app!!.name),
                                Toast.LENGTH_SHORT,
                            )!!
                            .show()

                        return@appClick
                    }

                    try {
                        startActivity(launchIntent)
                    } catch (exception: ActivityNotFoundException) {
                        Log.e(
                            TAG,
                            "Unable to launch " + app!!.packageName!!,
                            exception,
                        )

                        Toast.makeText(
                                this,
                                getString(R.string.launch_failed, app!!.name),
                                Toast.LENGTH_SHORT,
                            )!!
                            .show()
                    }
                },
            )
        binding!!.appRecyclerView.setLayoutManager(GridLayoutManager(this, 3))
        binding!!.appRecyclerView.setAdapter(adapter)
    }

    private fun loadInstalledApps(): List<AppInfo?>? {
        val queryIntent = Intent(Intent.ACTION_MAIN)
        queryIntent.addCategory(Intent.CATEGORY_LAUNCHER)

        // 继续调用 PackageManager 查询
        val packageManager = getPackageManager()

        val resolveInfos =
            packageManager!!.queryIntentActivities(
                queryIntent,
                PackageManager.MATCH_ALL,
            )
        val apps = ArrayList<AppInfo?>()
        for (resolveInfo in resolveInfos!!) {
            val name = resolveInfo!!.loadLabel(packageManager).toString()
            val packageName = resolveInfo!!.activityInfo!!.packageName
            // 不把 CarLauncher 自己放入应用抽屉
            if (getPackageName() == packageName) {
                continue
            }
            val icon = resolveInfo!!.loadIcon(packageManager)

            val launchIntent = Intent(Intent.ACTION_MAIN)
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            launchIntent.setClassName(
                packageName,
                resolveInfo!!.activityInfo!!.name,
            )
            apps.add(
                AppInfo(
                    name,
                    packageName,
                    icon,
                    launchIntent,
                )
            )
            Log.i(
                TAG,
                ("Found app: " +
                    name +
                    ", package=" +
                    packageName +
                    ", activity=" +
                    resolveInfo!!.activityInfo!!.name),
            )
        }
        apps.sortWith(
            Comparator { left, right ->
                left!!.name!!.compareTo(right!!.name!!, ignoreCase = true)
            }
        )
        return apps
    }

    protected override fun onDestroy() {
        Log.i(TAG, "on Destroy")

        super.onDestroy()
    }

    protected override fun onResume() {
        super.onResume()
        //        startVehicleRefresh();
        Log.i(TAG, "AppDrawerActivity  onResume")
        binding!!.text.setText(createTime!!.toString())
    }

    protected override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("test_count", createTime!!)
        Log.i(TAG, "onSaveInstanceState, count=" + createTime)
        super.onSaveInstanceState(outState)
    }

    protected override fun onPause() {
        Log.i(TAG, "onPause")
        super.onPause()
    }

    protected override fun onRestart() {
        Log.i(TAG, "onRestart")
        super.onRestart()
    }

    protected override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
    }

    protected override fun onStop() {
        Log.i(TAG, "onStop")
        super.onStop()
    }

    companion object {
        private const val TAG: String = "APP_COMPACT"
    }
}
