package com.shield2fa.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shield2fa.app.crypto.CryptoManager
import com.shield2fa.app.model.AccountItem

class VaultRepository(
    private val cryptoManager: CryptoManager,
    private val masterKey: ByteArray
) {
    private val gson = Gson()
    private val accountListType = object : TypeToken<ArrayList<AccountItem>>() {}.type

    fun getAccounts(): List<AccountItem> {
        val json = cryptoManager.loadVault(masterKey)
        return try {
            gson.fromJson<ArrayList<AccountItem>>(json, accountListType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addAccount(account: AccountItem): List<AccountItem> {
        val list = getAccounts().toMutableList()
        list.add(0, account)
        saveAccounts(list)
        return list
    }

    fun deleteAccount(accountId: String): List<AccountItem> {
        val list = getAccounts().filter { it.id != accountId }
        saveAccounts(list)
        return list
    }

    fun saveAccounts(accounts: List<AccountItem>) {
        val json = gson.toJson(accounts)
        cryptoManager.saveVault(masterKey, json)
    }

    fun resetMasterPassword(newPassword: String) {
        cryptoManager.resetPasswordWithMasterKey(masterKey, newPassword)
    }

    fun exportEncryptedBackup(): String {
        return cryptoManager.loadVault(masterKey)
    }
}
