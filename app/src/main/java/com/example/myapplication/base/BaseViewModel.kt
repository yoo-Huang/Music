package com.example.myapplication.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel 基类
 * 封装协程调用，统一处理异常，简化子类网络请求逻辑
 */
open class BaseViewModel : ViewModel() {

    /**
     * 在 viewModelScope 中安全发起网络请求
     *
     * @param dispatcher 协程调度器，默认 IO 线程
     * @param block 网络请求代码块，返回 Result<T>
     * @param onSuccess 成功回调，在主线程执行
     * @param onError 失败回调，在主线程执行
     */
    protected fun <T> launchRequest(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> Result<T>,
        onSuccess: (T) -> Unit,
        onError: (AppException) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = withContext(dispatcher) { block() }
                when (result) {
                    is Result.Success -> onSuccess.invoke(result.data)
                    is Result.Error -> onError.invoke(result.exception)
                }
            } catch (e: Exception) {
                onError.invoke(e.toAppException())
            }
        }
    }

    /**
     * 在 viewModelScope 中安全发起请求（通用版本，block 可返回任意类型）
     *
     * @param dispatcher 协程调度器，默认 IO 线程
     * @param block 代码块，返回 T
     * @param onSuccess 成功回调，在主线程执行
     * @param onError 失败回调，在主线程执行
     */
    protected fun <T> launchRequestRaw(
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (AppException) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = withContext(dispatcher) { block() }
                onSuccess.invoke(result)
            } catch (e: Exception) {
                onError.invoke(e.toAppException())
            }
        }
    }

    /**
     * 扩展函数：将 Throwable 转换为 AppException
     */
    protected fun Throwable.toAppException(): AppException = when (this) {
        is AppException -> this
        is java.net.UnknownHostException, is java.net.ConnectException ->
            AppException.NetworkException(cause = this)
        is java.net.SocketTimeoutException ->
            AppException.NetworkException("网络请求超时", cause = this)
        else -> AppException.UnknownException(cause = this)
    }
}
