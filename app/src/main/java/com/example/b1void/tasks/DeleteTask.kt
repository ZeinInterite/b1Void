package com.example.b1void.tasks

import android.os.AsyncTask
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.Metadata

class DeleteTask(
    private val dbxClient: DbxClientV2,
    private val dropboxPath: String,
    private val delegate: TaskDelegate
) : AsyncTask<Void, Void, Metadata?>() {

    interface TaskDelegate {
        fun onDeleteComplete()
        fun onError(error: DbxException)
    }

    private var error: DbxException? = null

    override fun doInBackground(vararg params: Void?): Metadata? {
        return try {
            dbxClient.files().delete(dropboxPath) // Используем delete
        } catch (e: DbxException) {
            error = e
            null
        }
    }

    override fun onPostExecute(result: Metadata?) {
        super.onPostExecute(result)
        if (error == null) {
            delegate.onDeleteComplete()
        } else {
            if (error is InvalidAccessTokenException) {
                delegate.onError(error as InvalidAccessTokenException)
            } else {
                delegate.onError(error!!)
            }
        }
    }
}
