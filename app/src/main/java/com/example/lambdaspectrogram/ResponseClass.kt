package com.example.lambdaspectrogram

class ResponseClass {
    lateinit var body: Array<Array<Array<Array<FloatArray>>>>
    lateinit var shape: Array<String>

    constructor(result: Array<Array<Array<Array<FloatArray>>>>, shape: Array<String>) {
        this.body = result
        this.shape = shape
    }

    constructor()
}
