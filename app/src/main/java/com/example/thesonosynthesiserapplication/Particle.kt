package com.example.thesonosynthesiserapplication

class Particle(
    val initialPosition: FloatArray,
    val initialVelocity: FloatArray,
    var randomForce: FloatArray
) {
    var position: FloatArray = initialPosition
    var velocity: FloatArray = initialVelocity
}