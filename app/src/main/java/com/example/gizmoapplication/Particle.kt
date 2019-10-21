package com.example.gizmoapplication

class Particle(initialPosition: FloatArray, initialVelocity: FloatArray, var randomForce: FloatArray) {

    var position: FloatArray = initialPosition
    var velocity: FloatArray = initialVelocity
}