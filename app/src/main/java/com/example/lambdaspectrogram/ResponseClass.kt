package com.example.lambdaspectrogram

class ResponseClass {
    lateinit var body: Array<Array<Array<FloatArray>>>
    lateinit var shape: String

    constructor(result: Array<Array<Array<FloatArray>>>, shape: String) {
        this.body = result
        this.shape = shape
    }

    constructor()
}
