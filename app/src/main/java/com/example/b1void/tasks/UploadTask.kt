package com.example.b1void.tasks

import android.os.AsyncTask
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class UploadTask(
    private val dbxClient: DbxClientV2,
    private val file: File,
    private val dropboxPath: String,
    private val delegate: TaskDelegate
) : AsyncTask<Void, Void, Void>() {
    interface TaskDelegate {
        fun onUploadComplete()
        fun onError(error: DbxException)
    }

    private var error: DbxException? = null

    override fun doInBackground(vararg params: Void?): Void? {
        try {
            val inputStream = FileInputStream(file)
            dbxClient.files().uploadBuilder(dropboxPath)
                .withMode(WriteMode.OVERWRITE)
                .uploadAndFinish(inputStream)
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
            delegate.onUploadComplete()
        } else {
            if (error is InvalidAccessTokenException) {
                delegate.onError(error as InvalidAccessTokenException)
            } else {
                delegate.onError(error!!)
            }
        }
    }
}
