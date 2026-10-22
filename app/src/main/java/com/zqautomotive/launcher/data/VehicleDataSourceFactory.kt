package com.zqautomotive.launcher.data

import android.content.Context
import com.zqautomotive.launcher.data.mock.MockVehicleDataSource
import com.zqautomotive.launcher.data.vhal.VhalVehicleDataSource

class VehicleDataSourceFactory private constructor() {
    companion object {

        @JvmStatic
        fun create(
            context: Context?,
            sourceType: SourceType?,
        ): VehicleDataSource? {
            if (sourceType == SourceType.VHAL) {
                return VhalVehicleDataSource(context)
            }
            return MockVehicleDataSource()
        }
    }
}
