package com.example.myapplication.base

/**
 * 网络请求结果密封类
 * 使用 Kotlin 密封类统一封装成功/失败状态，搭配协程使用
 *
 * @param T 数据类型
 */
sealed class Result<out T> {
    /** 请求成功，携带数据 */
    data class Success<T>(val data: T) : Result<T>()

    /** 请求失败，携带异常信息 */
    data class Error(val exception: AppException) : Result<Nothing>()
}

/**
 * 自定义异常体系
 * 便于统一处理网络、解析、业务等各类异常
 */
sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 网络连接异常 */
    class NetworkException(message: String = "网络连接失败，请检查网络设置", cause: Throwable? = null)
        : AppException(message, cause)

    /** 服务器返回异常（HTTP 状态码非 2xx） */
    class ServerException(val code: Int, message: String = "服务器异常", cause: Throwable? = null)
        : AppException(message, cause)

    /** 数据解析异常 */
    class ParseException(message: String = "数据解析失败", cause: Throwable? = null)
        : AppException(message, cause)

    /** 未知异常 */
    class UnknownException(message: String = "未知错误", cause: Throwable? = null)
        : AppException(message, cause)
}
