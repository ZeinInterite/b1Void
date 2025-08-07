package com.example.b1void.utils

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2

object DropboxClientFactory {

    private var sDbxClient: DbxClientV2? = null

    fun init(accessToken: String) {
        if (sDbxClient == null) {
            val requestConfig = DbxRequestConfig.newBuilder("b1void/1.0").build()
            sDbxClient = DbxClientV2(requestConfig, accessToken)
        }
    }

    fun getClient(): DbxClientV2 {
        if (sDbxClient == null) {
            throw IllegalStateException("Client not initialized. Call init() first.")
        }
        return sDbxClient!!
    }
}
