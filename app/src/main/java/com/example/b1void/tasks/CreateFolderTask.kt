package com.example.b1void.tasks

import android.os.AsyncTask
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.Metadata

class CreateFolderTask(
    private val dbxClient: DbxClientV2,
    private val delegate: TaskDelegate,
    private val dropboxPath: String
) : AsyncTask<Void, Void, Metadata?>() {

    interface TaskDelegate {
        fun onFolderCreated()
        fun onError(error: DbxException)
    }

    private var error: DbxException? = null

    override fun doInBackground(vararg params: Void?): Metadata? {
        return try {
            dbxClient.files().createFolder(dropboxPath) // Используем createFolder
        } catch (e: DbxException) {
            error = e
            null
        }
    }

    override fun onPostExecute(result: Metadata?) {
        super.onPostExecute(result)
        if (error == null) {
            delegate.onFolderCreated()
        } else {
            if (error is InvalidAccessTokenException) {
                delegate.onError(error!!)
            } else {
                delegate.onError(error!!)
            }

        }
    }
}
