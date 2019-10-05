package com.example.bbcsoundapplication

class Particle(initialPosition: FloatArray) {

    var position: FloatArray = initialPosition
    var velocity: FloatArray = floatArrayOf((0..300).random().toFloat(), (0..300).random().toFloat())
}