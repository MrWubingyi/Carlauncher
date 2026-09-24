package com.example.carlauncher.data

import android.content.Context
import com.example.carlauncher.data.mock.MockVehicleDataSource
import com.example.carlauncher.data.vhal.VhalVehicleDataSource

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
