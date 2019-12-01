package com.example.thesonosynthesiserapplication

class Configuration(
    val primaryAngle: Float,
    val secondaryAngle: Float,
    val timeToFocus: Float,
    val minAngleBetweenSounds: Float,
    val maxMediaPlayers: Int,
    val maxIdleSensorDifference: Float,
    val maxIdleSeconds: Float,
    val selectRandomly: Boolean,
    val numRandomlySelected: Int
) {}