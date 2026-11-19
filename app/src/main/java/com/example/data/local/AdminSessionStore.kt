package com.example.data.local

import android.content.Context
import androidx.annotation.WorkerThread

/**
 * Secure storage boundary for a future backend-issued administrator session.
 *
 * The value is opaque: this class does not define a token format, authenticate a
 * user, create a pairing code, refresh a session, or call any network endpoint.
 * A backend integration may save a credential here only after validating the
 * server's documented pairing/session contract.
 */
class AdminSessionStore(context: Context) {

    private val credentialStore = SecureCredentialStore(context.applicationContext)

    @WorkerThread
    fun saveBackendIssuedSession(opaqueSessionCredential: String) {
        require(opaqueSessionCredential.isNotBlank()) {
            "A non-empty backend-issued session credential is required."
        }
        credentialStore.put(ADMIN_SESSION_CREDENTIAL_ID, opaqueSessionCredential)
    }

    @WorkerThread
    fun readBackendIssuedSession(): String? =
        credentialStore.get(ADMIN_SESSION_CREDENTIAL_ID)

    @WorkerThread
    fun clearSession() {
        credentialStore.remove(ADMIN_SESSION_CREDENTIAL_ID)
    }

    private companion object {
        const val ADMIN_SESSION_CREDENTIAL_ID = "admin_backend_session"
    }
}
