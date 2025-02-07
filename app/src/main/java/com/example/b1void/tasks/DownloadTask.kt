package com.example.b1void.tasks

import android.os.AsyncTask
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadTask(
    private val dbxClient: DbxClientV2,
    private val dropboxPath: String,
    private val localFile: File,
    private val delegate: TaskDelegate
) : AsyncTask<Void, Void, Void>() {

    interface TaskDelegate {
        fun onDownloadComplete()
        fun onError(error: DbxException)
    }

    private var error: DbxException? = null

    override fun doInBackground(vararg params: Void?): Void? {
        try {
            val downloadResult = dbxClient.files().download(dropboxPath)
            FileOutputStream(localFile).use { outputStream ->
                downloadResult.inputStream.copyTo(outputStream)
            }
        } catch (e: DbxException) {
            error = e
        } catch (e: IOException) {
            error = DbxException("IO Error", e)
        }
        return null
    }

    override fun onPostExecute(result: Void?) {
        super.onPostExecute(result)
        if (error == null) {
            delegate.onDownloadComplete()
        } else {
            if (error is InvalidAccessTokenException) {
                delegate.onError(error as InvalidAccessTokenException)
            } else {
                delegate.onError(error!!)
            }
        }
    }
}
