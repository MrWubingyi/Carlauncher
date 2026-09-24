package com.example.carlauncher.model

enum class Gear private constructor(val value: Int) {
    P(0),
    R(1),
    N(2),
    D(3);

    companion object {

        @JvmStatic
        fun fromString(gearStr: String?): Gear {
            if (gearStr == null) return P
            when (gearStr.uppercase(java.util.Locale.getDefault())) {
                "R" -> return R
                "N" -> return N
                "D" -> return D
                "P" -> return P
                else -> return P
            }
        }
    }
}
