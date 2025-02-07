package com.example.b1void.tasks

import android.os.AsyncTask
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.Metadata

class ListFolderTask(
    private val dbxClient: DbxClientV2,
    private val delegate: TaskDelegate,
    private val dropboxPath: String
) : AsyncTask<Void, Void, List<Metadata>?>() {

    interface TaskDelegate {
        fun onFilesReceived(files: List<Metadata>)
        fun onError(error: DbxException)
    }

    private var error: DbxException? = null

    override fun doInBackground(vararg params: Void?): List<Metadata>? {
        return try {
            val result = dbxClient.files().listFolder(dropboxPath)
            return result.entries

        } catch (e: DbxException) {
            error = e
            null
        }
    }

    override fun onPostExecute(result: List<Metadata>?) {
        super.onPostExecute(result)
        if (result != null) {
            delegate.onFilesReceived(result)
        } else {
            if (error is InvalidAccessTokenException) {
                delegate.onError(error as InvalidAccessTokenException)
            } else {
                delegate.onError(error!!)
            }
        }
    }
}
