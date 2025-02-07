package com.example.b1void.DBX

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2

object DropboxClient {
    fun getClient(accessToken: String): DbxClientV2 {
        val config = DbxRequestConfig("dropbox/sample-app", "en_US")
        return DbxClientV2(config, accessToken)
    }
}
