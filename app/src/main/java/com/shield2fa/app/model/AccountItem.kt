package com.shield2fa.app.model

import java.util.UUID

data class AccountItem(
    val id: String = UUID.randomUUID().toString(),
    val issuer: String,
    val accountName: String,
    val secret: String,
    val digits: Int = 6,
    val period: Int = 30,
    val algorithm: String = "SHA1"
)
