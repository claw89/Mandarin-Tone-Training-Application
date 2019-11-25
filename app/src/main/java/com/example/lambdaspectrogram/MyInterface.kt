package com.example.lambdaspectrogram

import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunction

interface MyInterface {

    /**
     * Invoke the Lambda function "ScipyTestLambdaFunction".
     * The function name is the method name.
     */
    @LambdaFunction
    fun AndroidToneBackendLambdaFunction(request: RequestClass): ResponseClass

}
