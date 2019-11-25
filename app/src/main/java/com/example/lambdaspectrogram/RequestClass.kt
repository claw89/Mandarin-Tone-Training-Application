package com.example.lambdaspectrogram

class RequestClass {
    lateinit var data: List<Float>
    var num_syl: Int = 0

    constructor(data: List<Float>, num_syl: Int) {
        this.data = data
        this.num_syl = num_syl
    }

    constructor()
}
