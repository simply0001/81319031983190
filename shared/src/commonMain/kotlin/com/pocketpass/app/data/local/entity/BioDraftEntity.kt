package com.pocketpass.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile_bio_drafts")
data class BioDraftEntity(@PrimaryKey val accountId: String, val draft: String, val acceptedBio: String, val operationId: String, val error: String?)
