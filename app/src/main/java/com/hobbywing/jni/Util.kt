package com.hobbywing.jni

object Util {

    @JvmStatic
    var available: Boolean = false
        private set

    init {
        available = runCatching { System.loadLibrary("util") }.isSuccess
    }

        external fun parse(data: ByteArray?, version: String?, info: ByteArray?): Array<ByteArray>?
}
