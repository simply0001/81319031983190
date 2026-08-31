package com.pocketpass.app.data.supabase

import com.pocketpass.app.logPlatformWarning
import com.pocketpass.app.data.repository.remote.AchievementsRemoteDataSource
import com.pocketpass.app.data.repository.remote.BingoRemoteDataSource
import com.pocketpass.app.data.repository.remote.FriendsRemoteDataSource
import com.pocketpass.app.data.repository.remote.WorldTourRemoteDataSource
import com.pocketpass.app.data.repository.remote.EncounterRemoteDataSource
import com.pocketpass.app.data.repository.remote.MessageRemoteDataSource
import com.pocketpass.app.data.repository.remote.NotificationRemoteDataSource
import com.pocketpass.app.data.repository.remote.ProductionRemoteDataSources
import com.pocketpass.app.data.repository.remote.LeaderboardRemoteDataSource
import com.pocketpass.app.data.repository.remote.ProfileRemoteDataSource
import com.pocketpass.app.data.repository.remote.ShopRemoteDataSource
import com.pocketpass.app.data.supabase.dto.AchievementDto
import com.pocketpass.app.data.supabase.dto.BingoCellDto
import com.pocketpass.app.data.supabase.dto.ConversationDto
import com.pocketpass.app.data.supabase.dto.WorldTourRegionDto
import com.pocketpass.app.data.supabase.dto.ConversationMemberDto
import com.pocketpass.app.data.supabase.dto.FriendCodeDto
import com.pocketpass.app.data.supabase.dto.FriendshipDto
import com.pocketpass.app.data.supabase.dto.MessageDto
import com.pocketpass.app.data.supabase.dto.DeleteMessageRpc
import com.pocketpass.app.data.supabase.dto.AddGroupMembersRpc
import com.pocketpass.app.data.supabase.dto.CreateGroupConversationRpc
import com.pocketpass.app.data.supabase.dto.LeaveGroupConversationRpc
import com.pocketpass.app.data.supabase.dto.RemoveGroupMemberRpc
import com.pocketpass.app.data.supabase.dto.RenameGroupConversationRpc
import com.pocketpass.app.data.supabase.dto.EditMessageRpc
import com.pocketpass.app.data.supabase.dto.MarkConversationReadRpc
import com.pocketpass.app.data.supabase.dto.MarkAllNotificationsReadRpc
import com.pocketpass.app.data.supabase.dto.MarkNotificationReadRpc
import com.pocketpass.app.data.supabase.dto.DeleteNotificationRpc
import com.pocketpass.app.data.supabase.dto.NotificationDto
import com.pocketpass.app.data.supabase.dto.ProfileDto
import com.pocketpass.app.data.supabase.dto.BuyShopItemRpc
import com.pocketpass.app.data.supabase.dto.RemoveFriendRpc
import com.pocketpass.app.data.supabase.dto.ResolveFriendCodeRpc
import com.pocketpass.app.data.supabase.dto.LeaderboardEntryDto
import com.pocketpass.app.data.supabase.dto.ResolvedFriendCodeDto
import com.pocketpass.app.data.supabase.dto.ShopCategoryDto
import com.pocketpass.app.data.supabase.dto.OwnedShopItemDto
import com.pocketpass.app.data.supabase.dto.SupporterStatusDto
import com.pocketpass.app.data.supabase.dto.ShopItemDto
import com.pocketpass.app.data.supabase.dto.ShopPurchaseReceiptDto
import com.pocketpass.app.data.supabase.dto.TokenBalanceDto
import com.pocketpass.app.data.supabase.dto.RespondToFriendRequestRpc
import com.pocketpass.app.data.supabase.dto.SendFriendRequestRpc
import com.pocketpass.app.data.supabase.dto.SendMessageRpc
import com.pocketpass.app.data.supabase.dto.SetUserBlockRpc
import com.pocketpass.app.data.supabase.dto.toDomain
import com.pocketpass.app.domain.model.AchievementState
import com.pocketpass.app.domain.model.AvatarReference
import com.pocketpass.app.domain.model.BingoCell
import com.pocketpass.app.domain.model.WorldTourRegion
import com.pocketpass.app.domain.model.ConversationId
import com.pocketpass.app.domain.model.ConversationKind
import com.pocketpass.app.domain.model.ConversationSummary
import com.pocketpass.app.domain.model.AddGroupMembersCommand
import com.pocketpass.app.domain.model.CreateGroupConversationCommand
import com.pocketpass.app.domain.model.LeaveGroupConversationCommand
import com.pocketpass.app.domain.model.RemoveGroupMemberCommand
import com.pocketpass.app.domain.model.RenameGroupConversationCommand
import com.pocketpass.app.domain.model.groupMessagePreview
import com.pocketpass.app.domain.model.DeleteMessageCommand
import com.pocketpass.app.domain.model.DeleteNotificationCommand
import com.pocketpass.app.domain.model.EditMessageCommand
import com.pocketpass.app.domain.model.Friend
import com.pocketpass.app.domain.model.FriendCode
import com.pocketpass.app.domain.model.FriendRequestNotificationStatus
import com.pocketpass.app.domain.model.FriendshipStatus
import com.pocketpass.app.domain.model.EncounterId
import com.pocketpass.app.domain.model.IssuedNearbyCredential
import com.pocketpass.app.domain.model.Message
import com.pocketpass.app.domain.model.MessageId
import com.pocketpass.app.domain.model.MarkConversationReadCommand
import com.pocketpass.app.domain.model.MarkAllNotificationsReadCommand
import com.pocketpass.app.domain.model.MarkNotificationReadCommand
import com.pocketpass.app.domain.model.NotificationId
import com.pocketpass.app.domain.model.NotificationKind
import com.pocketpass.app.domain.model.NearbyEncounter
import com.pocketpass.app.domain.model.PocketPassNotification
import com.pocketpass.app.domain.model.OwnedShopItem
import com.pocketpass.app.domain.model.PurchaseShopItemCommand
import com.pocketpass.app.domain.model.RemoveFriendCommand
import com.pocketpass.app.domain.model.RespondToFriendRequestCommand
import com.pocketpass.app.domain.model.SendFriendRequestCommand
import com.pocketpass.app.domain.model.SendMessageCommand
import com.pocketpass.app.domain.model.SetUserBlockCommand
import com.pocketpass.app.domain.model.SubmitNearbyEncounterCommand
import com.pocketpass.app.domain.model.AccountSetupCommand
import com.pocketpass.app.domain.model.UpdateProfileCommand
import com.pocketpass.app.domain.model.RenameProfileCommand
import com.pocketpass.app.domain.model.UserId
import com.pocketpass.app.domain.model.LeaderboardEntry
import com.pocketpass.app.domain.model.LeaderboardScope
import com.pocketpass.app.domain.model.ShopCategory
import com.pocketpass.app.domain.model.ShopItem
import com.pocketpass.app.domain.model.ShopPurchaseOutcome
import com.pocketpass.app.domain.model.ShopPurchaseRejection
import com.pocketpass.app.domain.model.UserProfile
import com.pocketpass.app.domain.state.RepositoryFailure
import com.pocketpass.app.domain.state.RepositoryFailureKind
import com.pocketpass.app.domain.state.RepositoryResult
import com.pocketpass.app.domain.model.ClientOperationId
import com.pocketpass.app.domain.model.FriendProfileStats
import com.pocketpass.app.domain.repository.AccountDeleter
import com.pocketpass.app.domain.repository.FriendProfileStatsSource
import com.pocketpass.app.mii.MII_HAT_NOT_OWNED_HINT
import com.pocketpass.app.mii.MII_HAT_NOT_OWNED_MESSAGE
import com.pocketpass.app.mii.MiiActiveSlotPublisher
import com.pocketpass.app.mii.MiiAppearance
import com.pocketpass.app.mii.MiiHatNotOwnedException
import com.pocketpass.app.mii.MiiProfileFetcher
import com.pocketpass.app.mii.MiiProfilePublication
import com.pocketpass.app.mii.MiiProfilePublisher
import com.pocketpass.app.mii.MiiProfileSnapshot
import com.pocketpass.app.mii.MiiSlotDeleter
import com.pocketpass.app.mii.PublishMiiProfileCommand
import com.pocketpass.app.data.supabase.dto.AppInfoDto
import com.pocketpass.app.data.supabase.dto.AppInfoRpc
import com.pocketpass.app.data.supabase.dto.ConnectedAppsDto
import com.pocketpass.app.data.supabase.dto.OAuthAuthorizationDetailsDto
import com.pocketpass.app.data.supabase.dto.OAuthConsentDecisionDto
import com.pocketpass.app.data.supabase.dto.OAuthConsentResultDto
import com.pocketpass.app.data.supabase.dto.RevokeAppRpc
import com.pocketpass.app.data.supabase.dto.RevokeAppResultDto
import com.pocketpass.app.domain.model.ConnectedApp
import com.pocketpass.app.domain.model.OAuthConsentRequest
import com.pocketpass.app.domain.model.OAuthConsentScope
import com.pocketpass.app.domain.repository.ConnectedAppsSource
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.putJsonObject

class SupabaseProductionRemoteDataSources(
    private val client: SupabaseClient,
) : ProfileRemoteDataSource,
    FriendsRemoteDataSource,
    MessageRemoteDataSource,
    NotificationRemoteDataSource,
    EncounterRemoteDataSource,
    MiiProfilePublisher,
    MiiProfileFetcher,
    MiiActiveSlotPublisher,
    MiiSlotDeleter,
    AccountDeleter,
    ShopRemoteDataSource,
    LeaderboardRemoteDataSource,
    AchievementsRemoteDataSource,
    WorldTourRemoteDataSource,
    BingoRemoteDataSource,
    FriendProfileStatsSource,
    ConnectedAppsSource {
    val sources: ProductionRemoteDataSources = ProductionRemoteDataSources(
        profiles = this,
        friends = this,
        messages = this,
        notifications = this,
        encounters = this,
        shop = this,
        leaderboard = this,
        achievements = this,
        worldTour = this,
        bingo = this,
    )

    private fun requireActiveSession(accountId: UserId) {
        val sessionUserId = client.auth.currentSessionOrNull()?.user?.id
        val normalizedSession = sessionUserId?.let(::canonicalUuidOrNull)
        val normalizedAccount = canonicalUuidOrNull(accountId.value)
        check(normalizedSession != null && normalizedSession == normalizedAccount) {
            "Session-scoped fetch refused: the active session does not match the requested account"
        }
    }

    override suspend fun fetchCatalog(): RepositoryResult<List<ShopCategory>> = remoteResult {
        val categories = client
            .from(SHOP_CATEGORIES_TABLE)
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<ShopCategoryDto>()
        val items = client
            .from(SHOP_ITEMS_TABLE)
            .select { order("sort_order", Order.ASCENDING) }
            .decodeList<ShopItemDto>()
        val itemsByCategory = items.groupBy(ShopItemDto::categoryId)
        categories.map { category ->
            ShopCategory(
                id = category.id,
                slug = category.slug,
                title = category.title,
                subtitle = category.subtitle,
                iconKey = category.iconKey,
                items = itemsByCategory[category.id].orEmpty().map { item ->
                    ShopItem(
                        id = item.id,
                        slug = item.slug,
                        name = item.name,
                        priceTokens = item.priceTokens,
                        imageKey = item.imageKey,
                        miiHatType = item.miiHatType,
                    )
                },
            )
        }
    }

    override suspend fun fetchTokenBalance(accountId: UserId): RepositoryResult<Int> =
        remoteResult {
            client
                .from(TOKEN_BALANCES_TABLE)
                .select {
                    filter { eq("user_id", accountId.value) }
                }
                .decodeSingleOrNull<TokenBalanceDto>()
                ?.balance
                ?: 0
        }

    override suspend fun fetchOwnedItems(
        accountId: UserId,
    ): RepositoryResult<List<OwnedShopItem>> = remoteResult {
        client
            .from(USER_SHOP_ITEMS_TABLE)
            .select {
                filter { eq("user_id", accountId.value) }
                order("purchased_at", Order.ASCENDING)
            }
            .decodeList<OwnedShopItemDto>()
            .map { owned ->
                OwnedShopItem(
                    itemId = owned.itemId,
                    purchasedAt = parseSupabaseInstant(owned.purchasedAt),
                    pricePaid = owned.pricePaid,
                    pending = false,
                )
            }
    }

    override suspend fun fetchSupporterStatus(accountId: UserId): RepositoryResult<Instant?> =
        remoteResult {
            client
                .from(SUPPORTER_STATUS_TABLE)
                .select {
                    filter { eq("user_id", accountId.value) }
                }
                .decodeSingleOrNull<SupporterStatusDto>()
                ?.let { parseSupabaseInstant(it.activeUntil) }
        }

    override suspend fun purchaseItem(
        command: PurchaseShopItemCommand,
    ): RepositoryResult<ShopPurchaseOutcome> = remoteResult {
        try {
            val receipt = client.postgrest.rpc(
                function = "buy_shop_item",
                parameters = BuyShopItemRpc(
                    itemId = command.itemId,
                    clientOperationId = command.clientOperationId.value,
                ),
            ).decodeSingle<ShopPurchaseReceiptDto>()
            require(receipt.userId == command.accountId.value) {
                "Purchase receipt belongs to another account"
            }
            require(receipt.itemId == command.itemId) { "Purchase receipt names another item" }
            ShopPurchaseOutcome.Completed(
                itemId = receipt.itemId,
                balance = receipt.balance,
                purchasedAt = parseSupabaseInstant(receipt.purchasedAt),
            )
        } catch (error: PostgrestRestException) {
            when (error.purchaseRejection()) {
                ShopPurchaseRejection.InsufficientTokens.code ->
                    ShopPurchaseOutcome.Rejected(ShopPurchaseRejection.InsufficientTokens)

                ShopPurchaseRejection.ItemUnavailable.code ->
                    ShopPurchaseOutcome.Rejected(ShopPurchaseRejection.ItemUnavailable)

                PURCHASE_ALREADY_OWNED -> ShopPurchaseOutcome.AlreadyOwned
                else -> throw error
            }
        }
    }

    override suspend fun issueCredentials(
        accountId: UserId,
        signingPublicKeys: List<String>,
    ): RepositoryResult<List<IssuedNearbyCredential>> = remoteResult {
        require(signingPublicKeys.isNotEmpty())
        client.postgrest.rpc(
            function = "issue_nearby_credentials",
            parameters = IssueNearbyCredentialsRpc(signingPublicKeys),
        ).decodeList<IssuedNearbyCredentialDto>().map { credential ->
            IssuedNearbyCredential(
                token = credential.token,
                signingPublicKey = credential.signingPublicKey,
                expiresAt = parseSupabaseInstant(credential.expiresAt),
            )
        }
    }

    override suspend fun fetchEncounters(
        accountId: UserId,
    ): RepositoryResult<List<NearbyEncounter>> = remoteResult {
        requireActiveSession(accountId)
        client.postgrest.rpc(
            function = "get_nearby_encounters",
            parameters = buildJsonObject { },
        ).decodeList<NearbyEncounterDto>().map { row ->
            row.toDomain(accountId)
        }
    }

    override suspend fun submitEncounter(
        command: SubmitNearbyEncounterCommand,
    ): RepositoryResult<NearbyEncounter> = remoteResult {
        client.postgrest.rpc(
            function = "submit_nearby_encounter",
            parameters = SubmitNearbyEncounterRpc(
                encounterId = command.encounterId.value,
                reporterOperationId = command.clientOperationId.value,
                ownToken = command.ownToken,
                peerToken = command.peerToken,
                ownSigningPublicKey = command.ownSigningPublicKey,
                peerSigningPublicKey = command.peerSigningPublicKey,
                transcriptHash = command.transcriptHash,
                ownSignature = command.ownSignature,
                peerSignature = command.peerSignature,
                occurredAt = command.occurredAt.toString(),
            ),
        ).decodeSingle<NearbyEncounterDto>().toDomain(command.accountId)
    }

    override suspend fun fetchProfile(
        userId: UserId,
    ): RepositoryResult<UserProfile?> = remoteResult {
        client
            .from(PROFILES_TABLE)
            .select {
                filter { eq("user_id", userId.value) }
                limit(1)
            }
            .decodeSingleOrNull<ProfileDto>()
            ?.toDomain(::authenticatedAvatarUrl)
    }

    override suspend fun updateProfile(
        command: UpdateProfileCommand,
    ): RepositoryResult<UserProfile> = remoteResult {
        val patch = ProfilePatchDto(
            bio = command.profile.bio,
            avatarPath = avatarPath(command.profile.avatar),
            age = command.profile.age,
            countryCode = command.profile.countryCode,
        )
        client
            .from(PROFILES_TABLE)
            .update(patch) {
                select()
                filter { eq("user_id", command.accountId.value) }
            }
            .decodeSingle<ProfileDto>()
            .toDomain(::authenticatedAvatarUrl)
    }

    override suspend fun completeAccountSetup(
        command: AccountSetupCommand,
    ): RepositoryResult<UserProfile> = remoteResult {
        val patch = AccountSetupPatchDto(
            username = command.username,
            displayName = command.displayName,
            bio = command.bio,
            age = command.age,
            countryCode = command.countryCode,
        )
        client
            .from(PROFILES_TABLE)
            .update(patch) {
                select()
                filter { eq("user_id", command.accountId.value) }
            }
            .decodeSingle<ProfileDto>()
            .toDomain(::authenticatedAvatarUrl)
    }

    override suspend fun renameProfile(
        command: RenameProfileCommand,
    ): RepositoryResult<UserProfile> = remoteResult {
        val patch = RenameProfilePatchDto(
            username = command.name,
            displayName = command.name,
        )
        client
            .from(PROFILES_TABLE)
            .update(patch) {
                select()
                filter { eq("user_id", command.accountId.value) }
            }
            .decodeSingle<ProfileDto>()
            .toDomain(::authenticatedAvatarUrl)
    }

    override suspend fun touchLastSeen(): RepositoryResult<Instant> = remoteResult {
        parseSupabaseInstant(client.postgrest.rpc("touch_last_seen").decodeAs<String>())
    }

    override suspend fun publishMiiProfile(
        command: PublishMiiProfileCommand,
    ): RepositoryResult<MiiProfilePublication> = remoteResult {
        val avatarPath = miiAvatarPath(
            accountId = command.accountId,
            revision = command.revision,
            clientOperationId = command.clientOperationId.value,
        )
        client.storage.from(AVATAR_BUCKET).upload(
            path = avatarPath,
            data = command.portraitPng,
        ) {
            upsert = true
            contentType = ContentType.Image.PNG
        }

        val appearance = MiiPublicationJson
            .encodeToJsonElement(command.appearance)
            .jsonObject
        val publication = try {
            client.postgrest.rpc(
                function = "save_profile_mii_slot",
                parameters = SaveProfileMiiSlotRpc(
                    clientOperationId = command.clientOperationId.value,
                    revision = command.revision,
                    schemaVersion = command.appearance.schemaVersion,
                    appearance = appearance,
                    avatarPath = avatarPath,
                    canonicalMiicBase64 = command.canonicalMiic?.let(Base64.Default::encode),
                    slot = command.slot,
                ),
            ).decodeSingle<MiiProfilePublicationDto>()
        } catch (error: PostgrestRestException) {
            if (error.hint == MII_HAT_NOT_OWNED_HINT) throw MiiHatNotOwnedException()
            throw error
        }

        require(publication.userId == canonicalUuid(command.accountId.value)) {
            "Mii publication returned another account"
        }
        require(publication.revision == command.revision) {
            "Mii publication returned another revision"
        }
        require(publication.avatarPath == avatarPath) {
            "Mii publication returned another avatar"
        }
        publication.supersededAvatarPath
            ?.takeIf { it.isNotBlank() && it != avatarPath }
            ?.let { stalePath ->
                runCatching {
                    client.storage.from(AVATAR_BUCKET).delete(listOf(stalePath))
                }
            }
        MiiProfilePublication(
            accountId = command.accountId,
            appearanceSchemaVersion = publication.schemaVersion,
            revision = publication.revision,
            avatar = AvatarReference.Remote(
                authenticatedAvatarUrl(publication.avatarPath),
            ),
            publishedAt = parseSupabaseInstant(publication.updatedAt),
        )
    }

    override suspend fun deleteMiiSlot(
        accountId: UserId,
        slot: Int,
    ): RepositoryResult<Unit> = remoteResult {
        requireActiveSession(accountId)
        val response = client.postgrest.rpc(
            function = "delete_profile_mii_slot",
            parameters = DeleteProfileMiiSlotRpc(slot = slot),
        ).decodeAs<DeleteProfileMiiSlotDto>()
        response.avatarPath
            ?.takeIf(String::isNotBlank)
            ?.let { stalePath ->
                runCatching {
                    client.storage.from(AVATAR_BUCKET).delete(listOf(stalePath))
                }
            }
        Unit
    }

    override suspend fun fetchMiiProfiles(
        accountId: UserId,
    ): RepositoryResult<List<MiiProfileSnapshot>> = remoteResult {
        requireActiveSession(accountId)
        client
            .from(PROFILE_MIIS_TABLE)
            .select {
                filter { eq("user_id", accountId.value) }
            }
            .decodeList<ProfileMiiRowDto>()
            .map { row ->
                MiiProfileSnapshot(
                    slot = row.slot,
                    appearance = MiiPublicationJson
                        .decodeFromJsonElement<MiiAppearance>(row.appearance)
                        .normalized(),
                    revision = row.revision,
                    isActive = row.isActive,
                    portraitPng = runCatching {
                        client.storage
                            .from(AVATAR_BUCKET)
                            .downloadAuthenticated(row.avatarPath)
                    }.getOrNull(),
                    savedAt = parseSupabaseInstant(row.updatedAt),
                )
            }
    }

    override suspend fun fetchFriendProfileStats(
        friendUserId: UserId,
    ): RepositoryResult<FriendProfileStats> = remoteResult {
        val stats = client.postgrest.rpc(
            function = "get_friend_profile_stats",
            parameters = FriendProfileStatsRpc(friendUserId = friendUserId.value),
        ).decodeSingle<FriendProfileStatsDto>()
        FriendProfileStats(
            encounterCount = stats.encounterCount.toInt().coerceAtLeast(0),
            trophyCount = stats.trophyCount.toInt().coerceAtLeast(0),
        )
    }

    override suspend fun fetchLeaderboard(
        accountId: UserId,
        scope: LeaderboardScope,
    ): RepositoryResult<List<LeaderboardEntry>> = remoteResult {
        requireActiveSession(accountId)
        client.postgrest.rpc(
            function = "get_leaderboard",
            parameters = LeaderboardRpc(scope = scope.key),
        )
            .decodeList<LeaderboardEntryDto>()
            .map { entry -> entry.toDomain(::authenticatedAvatarUrl) }
    }

    override suspend fun fetchAchievements(
        accountId: UserId,
    ): RepositoryResult<List<AchievementState>> = remoteResult {
        requireActiveSession(accountId)
        client.postgrest.rpc(function = "get_achievements")
            .decodeList<AchievementDto>()
            .map(AchievementDto::toDomain)
    }

    override suspend fun fetchRegions(
        accountId: UserId,
    ): RepositoryResult<List<WorldTourRegion>> = remoteResult {
        requireActiveSession(accountId)
        client.postgrest.rpc(function = "get_world_tour")
            .decodeList<WorldTourRegionDto>()
            .map(WorldTourRegionDto::toDomain)
    }

    override suspend fun fetchBoard(
        accountId: UserId,
    ): RepositoryResult<List<BingoCell>> = remoteResult {
        requireActiveSession(accountId)
        client.postgrest.rpc(function = "get_bingo_card")
            .decodeList<BingoCellDto>()
            .map(BingoCellDto::toDomain)
    }

    override suspend fun openDirectConversation(
        friendUserId: UserId,
        clientOperationId: ClientOperationId,
    ): RepositoryResult<ConversationId> = remoteResult {
        val conversationId = client.postgrest.rpc(
            function = "get_or_create_direct_conversation",
            parameters = DirectConversationRpc(
                otherUserId = friendUserId.value,
                clientOperationId = clientOperationId.value,
            ),
        ).decodeAs<String>()
        ConversationId(canonicalUuid(conversationId))
    }

    override suspend fun deleteAccount(
        accountId: UserId,
    ): RepositoryResult<Unit> = remoteResult {
        val bucket = client.storage.from(AVATAR_BUCKET)
        val folder = accountId.value
        val storedAvatars = bucket.list("$folder/").map { item -> "$folder/${item.name}" }
        if (storedAvatars.isNotEmpty()) {
            bucket.delete(storedAvatars)
        }
        client.postgrest.rpc(function = "delete_my_account")
        Unit
    }

    override suspend fun fetchConnectedApps(): RepositoryResult<List<ConnectedApp>> = remoteResult {
        client.postgrest.rpc(function = "api_connected_apps")
            .decodeAs<ConnectedAppsDto>()
            .items
            .map { it.toDomain() }
    }

    override suspend fun revokeConnectedApp(clientId: String): RepositoryResult<Boolean> = remoteResult {
        client.postgrest.rpc(function = "api_revoke_app", parameters = RevokeAppRpc(clientId))
            .decodeAs<RevokeAppResultDto>()
            .revoked
    }

    override suspend fun fetchOAuthConsent(
        authorizationId: String,
    ): RepositoryResult<OAuthConsentRequest> = remoteResult {
        val details = OAuthJson.decodeFromString<OAuthAuthorizationDetailsDto>(
            oauthServerRequest("GET", "/auth/v1/oauth/authorizations/${encodePathSegment(authorizationId)}", null),
        )
        val approvedRedirect = details.redirectUrl?.takeIf { it.isNotBlank() }
        if (approvedRedirect != null) {
            return@remoteResult OAuthConsentRequest(
                authorizationId = authorizationId,
                appName = details.client?.name.orEmpty(),
                website = null,
                ownerDisplayName = null,
                scopes = emptyList(),
                extraClaims = emptyList(),
                unknownScopes = emptyList(),
                returnHost = returnHost(details.redirectUri),
                suspended = false,
                infoError = null,
                redirectUrl = approvedRedirect,
            )
        }
        val oauthClient = details.client
        val clientId = oauthClient?.id?.lowercase()?.takeIf { UUID_PATTERN.matches(it) }
        var info: AppInfoDto? = null
        var suspended = false
        var infoError: String? = null
        if (clientId == null) {
            infoError = "This app is not registered with PocketPass."
        } else {
            try {
                info = client.postgrest.rpc(function = "api_app_info", parameters = AppInfoRpc(clientId))
                    .decodeAs<AppInfoDto>()
            } catch (error: PostgrestRestException) {
                if (error.hint == APP_SUSPENDED_HINT || error.statusCode == 403) {
                    suspended = true
                } else {
                    infoError = "This app is not registered with PocketPass."
                }
            }
        }
        if (info?.status != null && info.status != "active") suspended = true
        val requested = details.scope.orEmpty()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it != "openid" }
        val oidcDescriptions = info?.oidcScopeDescriptions.orEmpty()
        OAuthConsentRequest(
            authorizationId = authorizationId,
            appName = oauthClient?.name?.takeIf { it.isNotBlank() }
                ?: info?.name?.takeIf { it.isNotBlank() }
                ?: "Unknown app",
            website = httpsOnly(oauthClient?.uri ?: info?.website),
            ownerDisplayName = info?.ownerDisplayName?.takeIf { it.isNotBlank() },
            scopes = info?.scopes.orEmpty().map { OAuthConsentScope(it.key, it.description) },
            extraClaims = requested.mapNotNull { oidcDescriptions[it] },
            unknownScopes = requested.filterNot { it in oidcDescriptions },
            returnHost = returnHost(details.redirectUri),
            suspended = suspended,
            infoError = infoError ?: if (info == null && !suspended) "This app is not available." else null,
            redirectUrl = null,
        )
    }

    override suspend fun decideOAuthConsent(
        authorizationId: String,
        approve: Boolean,
    ): RepositoryResult<String> = remoteResult {
        val result = OAuthJson.decodeFromString<OAuthConsentResultDto>(
            oauthServerRequest(
                "POST",
                "/auth/v1/oauth/authorizations/${encodePathSegment(authorizationId)}/consent",
                OAuthJson.encodeToString(OAuthConsentDecisionDto(if (approve) "approve" else "deny")),
            ),
        )
        result.redirectUrl?.takeIf { it.isNotBlank() } ?: error("The consent response carried no redirect_url")
    }

    private suspend fun oauthServerRequest(method: String, path: String, body: String?): String {
        val accessToken = client.auth.currentAccessTokenOrNull()
            ?: throw OAuthServerException(401, "Session required")
        val response = oauthHttpClient.request(client.supabaseHttpUrl + path) {
            this.method = HttpMethod.parse(method)
            header("apikey", client.supabaseKey)
            header("Authorization", "Bearer $accessToken")
            header("Accept", "application/json")
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw OAuthServerException(response.status.value, text.take(300))
        }
        return text
    }

    private val oauthHttpClient: HttpClient by lazy { HttpClient() }

    private fun encodePathSegment(value: String): String = value.encodeURLParameter()

    // Ktor's Url invents a scheme and host rather than failing, so the https prefix and the
    // authority are checked on the raw string before the parse is trusted.
    private fun httpsOnly(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        val authority = trimmed.substring("https://".length)
        if (authority.isEmpty() || authority.startsWith('/')) return null
        val url = runCatching { Url(trimmed) }.getOrNull() ?: return null
        return if (url.host.isNotBlank()) trimmed else null
    }

    private fun returnHost(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return "the app"
        val url = runCatching { Url(trimmed) }.getOrNull() ?: return "the app"
        return url.host.takeIf { it.isNotBlank() }
            ?: url.protocol.name.takeIf { it.isNotBlank() }?.let { "$it://" }
            ?: "the app"
    }

    override suspend fun setActiveMiiSlot(
        accountId: UserId,
        slot: Int,
        clientOperationId: ClientOperationId,
    ): RepositoryResult<Unit> = remoteResult {
        val activation = client.postgrest.rpc(
            function = "set_active_mii_slot",
            parameters = SetActiveMiiSlotRpc(
                clientOperationId = clientOperationId.value,
                slot = slot,
            ),
        ).decodeSingle<ActiveMiiSlotDto>()

        require(activation.userId == canonicalUuid(accountId.value)) {
            "Mii slot activation returned another account"
        }
        require(activation.slot == slot) {
            "Mii slot activation returned another slot"
        }
    }

    override suspend fun fetchFriends(
        accountId: UserId,
    ): RepositoryResult<List<Friend>> = remoteResult {
        val friendships = client
            .from(FRIENDSHIPS_TABLE)
            .select {
                filter {
                    or {
                        eq("user_low", accountId.value)
                        eq("user_high", accountId.value)
                    }
                }
            }
            .decodeList<FriendshipDto>()
        val relatedUserIds = friendships
            .mapTo(linkedSetOf()) { friendship ->
                friendship.otherUserId(accountId.value)
            }
        val profiles = fetchProfiles(relatedUserIds)
        friendships
            .mapNotNull { friendship ->
                val friendUserId = friendship.otherUserId(accountId.value)
                val profile = profiles[friendUserId] ?: return@mapNotNull null
                Friend(
                    ownerId = accountId,
                    profile = profile,
                    status = FriendshipStatus.Accepted,
                    lastInteractionAt = parseSupabaseInstant(friendship.createdAt),
                    isOnline = false,
                )
            }
            .sortedWith(
                compareByDescending<Friend> { it.isOnline }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.profile.displayName }
                .thenBy { it.profile.userId.value },
            )
    }

    override suspend fun fetchMyFriendCode(
        accountId: UserId,
    ): RepositoryResult<FriendCode> = remoteResult {
        requireActiveSession(accountId)
        client.postgrest.rpc(
            function = "get_my_friend_code",
        ).decodeSingle<FriendCodeDto>().let { FriendCode(it.code) }
    }

    override suspend fun resolveFriendCode(
        accountId: UserId,
        friendCode: FriendCode,
    ): RepositoryResult<UserProfile> = remoteResult {
        val resolved = client.postgrest.rpc(
            function = "resolve_friend_code",
            parameters = ResolveFriendCodeRpc(friendCode.value),
        ).decodeSingleOrNull<ResolvedFriendCodeDto>()
            ?: throw RemoteNotFoundException()
        UserProfile(
            userId = UserId(resolved.userId),
            displayName = resolved.displayName,
            avatar = resolved.avatarPath?.let {
                AvatarReference.Remote(authenticatedAvatarUrl(it))
            },
            bio = resolved.bio,
            age = resolved.age,
            countryCode = resolved.countryCode,
            lastSeenAt = resolved.lastSeenAt?.let(::parseSupabaseInstant),
            updatedAt = parseSupabaseInstant(resolved.updatedAt),
        )
    }

    override suspend fun sendFriendRequest(
        command: SendFriendRequestCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "send_friend_request",
            parameters = SendFriendRequestRpc(
                addresseeId = command.addressee.userId.value,
                clientOperationId = command.clientOperationId.value,
            ),
        )
        Unit
    }

    override suspend fun respondToFriendRequest(
        command: RespondToFriendRequestCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "respond_to_friend_request",
            parameters = RespondToFriendRequestRpc(
                requestId = command.requestId,
                accept = command.accept,
                clientOperationId = command.clientOperationId.value,
            ),
        )
        Unit
    }

    override suspend fun removeFriend(
        command: RemoveFriendCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "remove_friend",
            parameters = RemoveFriendRpc(
                friendId = command.friendUserId.value,
                clientOperationId = command.clientOperationId.value,
            ),
        )
        Unit
    }

    override suspend fun setUserBlocked(
        command: SetUserBlockCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "set_user_block",
            parameters = SetUserBlockRpc(
                userId = command.targetUserId.value,
                blocked = command.blocked,
                clientOperationId = command.clientOperationId.value,
            ),
        )
        Unit
    }

    override suspend fun fetchConversations(
        accountId: UserId,
    ): RepositoryResult<List<ConversationSummary>> = remoteResult {
        val memberships = client
            .from(CONVERSATION_MEMBERS_TABLE)
            .select {
                filter {
                    eq("user_id", accountId.value)
                    exact("left_at", null)
                }
            }
            .decodeList<ConversationMemberDto>()
        if (memberships.isEmpty()) return@remoteResult emptyList()

        val conversationIds = memberships.map(ConversationMemberDto::conversationId)
        val conversations = selectIn<ConversationDto>(CONVERSATIONS_TABLE, "id", conversationIds)
        val recentMessages = client
            .from(MESSAGES_TABLE)
            .select {
                filter { isIn("conversation_id", conversationIds) }
                order("created_at", Order.DESCENDING)
                limit(MAX_SUMMARY_MESSAGES)
            }
            .decodeList<MessageDto>()
        val activeMembers = selectIn<ConversationMemberDto>(
            CONVERSATION_MEMBERS_TABLE,
            "conversation_id",
            conversationIds,
        ).filter { it.leftAt == null }
        val profiles = fetchProfiles(activeMembers.map(ConversationMemberDto::userId).toSet())
        val membersByConversation = activeMembers.groupBy { it.conversationId }
        val membershipByConversation = memberships.associateBy { it.conversationId }
        val messagesByConversation = recentMessages.groupBy { it.conversationId }

        conversations.map { conversation ->
            val isGroup = conversation.kind == GROUP_CONVERSATION_KIND
            val members = membersByConversation[conversation.id]
                .orEmpty()
                .sortedWith(
                    compareBy<ConversationMemberDto> { parseSupabaseInstant(it.joinedAt) }
                        .thenBy { it.userId },
                )
                .map { member -> member.toDomain(profiles[member.userId]) }
            val otherProfile = members
                .firstOrNull { it.userId.value != accountId.value }
                ?.let { profiles[it.userId.value] }
                ?: conversation.otherDirectUserId(accountId.value)?.let(profiles::get)
            val conversationMessages = messagesByConversation[conversation.id]
                .orEmpty()
                .filter { it.deletedAt == null }
            val latest = conversationMessages.maxByOrNull {
                parseSupabaseInstant(it.createdAt)
            }
            val lastReadAt = membershipByConversation[conversation.id]
                ?.lastReadAt
                ?.let(::parseSupabaseInstant)
            ConversationSummary(
                id = ConversationId(conversation.id),
                title = when {
                    isGroup -> conversation.title?.takeIf(String::isNotBlank) ?: "Group chat"
                    else -> otherProfile?.displayName ?: conversation.title ?: "Conversation"
                },
                avatar = if (isGroup) null else otherProfile?.avatar,
                latestMessagePreview = when {
                    latest == null -> ""
                    isGroup -> groupMessagePreview(
                        body = latest.body,
                        senderId = UserId(latest.senderId),
                        accountId = accountId,
                        members = members,
                    )
                    else -> latest.body
                },
                latestMessageAt = latest?.createdAt?.let(::parseSupabaseInstant),
                unreadCount = conversationMessages.count { message ->
                    message.senderId != accountId.value &&
                        lastReadAt?.let {
                            parseSupabaseInstant(message.createdAt) > it
                        } != false
                },
                kind = if (isGroup) ConversationKind.Group else ConversationKind.Direct,
                members = members,
            )
        }.sortedWith(
            compareByDescending<ConversationSummary> { it.latestMessageAt }
                .thenBy { it.id.value },
        )
    }

    private suspend inline fun <reified T : Any> selectIn(
        table: String,
        column: String,
        ids: List<String>,
    ): List<T> = ids.distinct().chunked(MAX_IN_FILTER_IDS).flatMap { chunk ->
        client
            .from(table)
            .select {
                filter { isIn(column, chunk) }
            }
            .decodeList<T>()
    }

    override suspend fun createGroupConversation(
        command: CreateGroupConversationCommand,
    ): RepositoryResult<ConversationId> = remoteResult {
        val conversation = client.postgrest.rpc(
            function = "create_group_conversation",
            parameters = CreateGroupConversationRpc(
                title = command.title,
                memberIds = command.memberIds.map(UserId::value),
                clientOperationId = command.clientOperationId.value,
            ),
        ).decodeAs<ConversationDto>()
        ConversationId(canonicalUuid(conversation.id))
    }

    override suspend fun addGroupMembers(
        command: AddGroupMembersCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "add_group_members",
            parameters = AddGroupMembersRpc(
                conversationId = command.conversationId.value,
                memberIds = command.memberIds.map(UserId::value),
                clientOperationId = command.clientOperationId.value,
            ),
        )
        Unit
    }

    override suspend fun removeGroupMember(
        command: RemoveGroupMemberCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "remove_group_member",
            parameters = RemoveGroupMemberRpc(
                conversationId = command.conversationId.value,
                userId = command.userId.value,
                clientOperationId = command.clientOperationId.value,
            ),
        )
        Unit
    }

    override suspend fun leaveGroupConversation(
        command: LeaveGroupConversationCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "leave_group_conversation",
            parameters = LeaveGroupConversationRpc(
                conversationId = command.conversationId.value,
                clientOperationId = command.clientOperationId.value,
            ),
        )
        Unit
    }

    override suspend fun renameGroupConversation(
        command: RenameGroupConversationCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "rename_group_conversation",
            parameters = RenameGroupConversationRpc(
                conversationId = command.conversationId.value,
                title = command.title,
                clientOperationId = command.clientOperationId.value,
            ),
        )
        Unit
    }

    override suspend fun fetchMessages(
        accountId: UserId,
        conversationId: ConversationId,
    ): RepositoryResult<List<Message>> = remoteResult {
        client
            .from(MESSAGES_TABLE)
            .select {
                filter { eq("conversation_id", conversationId.value) }
                order("created_at", Order.ASCENDING)
                limit(MAX_MESSAGES_PER_REFRESH)
            }
            .decodeList<MessageDto>()
            .map { it.toDomain(::authenticatedMessageMediaUrl) }
    }

    override suspend fun sendMessage(
        command: SendMessageCommand,
    ): RepositoryResult<Message> = remoteResult {
        val attachment = command.attachment
        val remotePath = if (attachment?.localPath != null) {
            val path = messageMediaPath(
                accountId = command.accountId,
                conversationId = command.conversationId,
                messageId = command.messageId,
                mimeType = attachment.mimeType,
            )
            client.storage.from(MESSAGE_MEDIA_BUCKET).upload(
                path = path,
                data = SystemFileSystem.source(Path(attachment.localPath))
                    .buffered()
                    .use { source -> source.readByteArray() },
            ) {
                upsert = true
                contentType = ContentType.parse(attachment.mimeType)
            }
            path
        } else {
            attachment?.remotePath
        }

        client.postgrest.rpc(
            function = "send_message",
            parameters = SendMessageRpc(
                messageId = command.messageId.value,
                conversationId = command.conversationId.value,
                clientOperationId = command.clientOperationId.value,
                body = command.body,
                replyToId = null,
                metadata = buildJsonObject {
                    if (remotePath != null && attachment != null) {
                        putJsonObject(MESSAGE_ATTACHMENT_KEY) {
                            put("path", JsonPrimitive(remotePath))
                            put("mime_type", JsonPrimitive(attachment.mimeType))
                        }
                    }
                },
            ),
        ).decodeAs<MessageDto>().toDomain(::authenticatedMessageMediaUrl)
    }

    override suspend fun editMessage(
        command: EditMessageCommand,
    ): RepositoryResult<Message> = remoteResult {
        client.postgrest.rpc(
            function = "edit_message",
            parameters = EditMessageRpc(
                messageId = command.messageId.value,
                body = command.body,
            ),
        ).decodeAs<MessageDto>().toDomain(::authenticatedMessageMediaUrl)
    }

    override suspend fun deleteMessage(
        command: DeleteMessageCommand,
    ): RepositoryResult<Message> = remoteResult {
        client.postgrest.rpc(
            function = "delete_message",
            parameters = DeleteMessageRpc(messageId = command.messageId.value),
        ).decodeAs<MessageDto>().toDomain(::authenticatedMessageMediaUrl)
    }

    override suspend fun markConversationRead(
        command: MarkConversationReadCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "mark_conversation_read",
            parameters = MarkConversationReadRpc(
                conversationId = command.conversationId.value,
                readAt = command.readAt.toString(),
            ),
        )
        Unit
    }

    override suspend fun fetchNotifications(
        accountId: UserId,
    ): RepositoryResult<List<PocketPassNotification>> = remoteResult {
        val rows = client
            .from(NOTIFICATIONS_TABLE)
            .select {
                filter {
                    eq("recipient_id", accountId.value)
                    exact("deleted_at", null)
                }
                order("updated_at", Order.DESCENDING)
                limit(MAX_NOTIFICATIONS_PER_REFRESH)
            }
            .decodeList<NotificationDto>()
        val actors = fetchProfiles(rows.mapNotNull(NotificationDto::actorId).toSet())
        rows.map { row ->
            PocketPassNotification(
                id = NotificationId(row.id),
                recipientId = UserId(row.recipientId),
                kind = when (row.kind) {
                    "friend_request" -> NotificationKind.FriendRequest
                    "friend_accepted" -> NotificationKind.FriendAccepted
                    "message" -> NotificationKind.Message
                    "nearby_encounter" -> NotificationKind.NearbyEncounter
                    else -> NotificationKind.System
                },
                actor = row.actorId?.let(actors::get),
                friendRequestId = row.friendRequestId,
                friendRequestStatus = row.friendRequestStatus?.let { status ->
                    when (status) {
                        "pending" -> FriendRequestNotificationStatus.Pending
                        "accepted" -> FriendRequestNotificationStatus.Accepted
                        "declined" -> FriendRequestNotificationStatus.Declined
                        else -> null
                    }
                },
                conversationId = row.conversationId?.let(::ConversationId),
                title = row.title,
                body = row.body,
                eventCount = row.eventCount,
                createdAt = parseSupabaseInstant(row.createdAt),
                updatedAt = parseSupabaseInstant(row.updatedAt),
                readAt = row.readAt?.let(::parseSupabaseInstant),
                deletedAt = row.deletedAt?.let(::parseSupabaseInstant),
            )
        }
    }

    override suspend fun markNotificationRead(
        command: MarkNotificationReadCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "mark_notification_read",
            parameters = MarkNotificationReadRpc(
                notificationId = command.notificationId.value,
                readAt = command.readAt.toString(),
            ),
        )
        Unit
    }

    override suspend fun markAllNotificationsRead(
        command: MarkAllNotificationsReadCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "mark_all_notifications_read",
            parameters = MarkAllNotificationsReadRpc(command.readAt.toString()),
        )
        Unit
    }

    override suspend fun deleteNotification(
        command: DeleteNotificationCommand,
    ): RepositoryResult<Unit> = remoteResult {
        client.postgrest.rpc(
            function = "delete_notification",
            parameters = DeleteNotificationRpc(
                notificationId = command.notificationId.value,
                deletedAt = command.deletedAt.toString(),
            ),
        )
        Unit
    }

    private suspend fun fetchProfiles(
        userIds: Set<String>,
    ): Map<String, UserProfile> {
        if (userIds.isEmpty()) return emptyMap()
        return selectIn<ProfileDto>(PROFILES_TABLE, "user_id", userIds.toList())
            .associate { profile ->
                profile.userId to profile.toDomain(::authenticatedAvatarUrl)
            }
    }

    private fun authenticatedAvatarUrl(path: String): String =
        client.storage.from(AVATAR_BUCKET).authenticatedUrl(path)

    private fun authenticatedMessageMediaUrl(path: String): String =
        client.storage.from(MESSAGE_MEDIA_BUCKET).authenticatedUrl(path)

    private fun avatarPath(avatar: AvatarReference?): String? = when (avatar) {
        null, is AvatarReference.Bundled -> null
        is AvatarReference.Remote -> {
            val value = avatar.url
            when {
                AUTHENTICATED_AVATAR_MARKER in value ->
                    value.substringAfter(AUTHENTICATED_AVATAR_MARKER).substringBefore('?')

                AVATAR_PATH_PATTERN.matches(value) -> value
                else -> null
            }
        }
    }

    private fun NearbyEncounterDto.toDomain(accountId: UserId): NearbyEncounter =
        NearbyEncounter(
            id = EncounterId(encounterId),
            ownerId = accountId,
            profile = UserProfile(
                userId = UserId(remoteUserId),
                displayName = displayName,
                avatar = avatarPath?.let { path ->
                    AvatarReference.Remote(authenticatedAvatarUrl(path))
                },
                bio = bio,
                age = age,
                countryCode = countryCode,
                locationLabel = locationLabel,
                lastSeenAt = lastSeenAt?.let(::parseSupabaseInstant),
                updatedAt = parseSupabaseInstant(profileUpdatedAt),
            ),
            occurredAt = parseSupabaseInstant(occurredAt),
            resolvedAt = parseSupabaseInstant(resolvedAt),
        )

    private companion object {
        const val PROFILES_TABLE = "profiles"
        const val PROFILE_MIIS_TABLE = "profile_miis"
        const val FRIENDSHIPS_TABLE = "friendships"
        const val CONVERSATIONS_TABLE = "conversations"
        const val CONVERSATION_MEMBERS_TABLE = "conversation_members"
        const val MESSAGES_TABLE = "messages"
        const val NOTIFICATIONS_TABLE = "notifications"
        const val SHOP_CATEGORIES_TABLE = "shop_categories"
        const val SHOP_ITEMS_TABLE = "shop_items"
        const val TOKEN_BALANCES_TABLE = "token_balances"
        const val USER_SHOP_ITEMS_TABLE = "user_shop_items"
        const val SUPPORTER_STATUS_TABLE = "supporter_status"
        const val AVATAR_BUCKET = "avatars"
        const val AUTHENTICATED_AVATAR_MARKER = "/object/authenticated/avatars/"
        const val MESSAGE_MEDIA_BUCKET = "message-media"
        const val MAX_SUMMARY_MESSAGES = 1_000L
        const val MAX_MESSAGES_PER_REFRESH = 1_000L
        const val MAX_IN_FILTER_IDS = 100
        const val GROUP_CONVERSATION_KIND = "group"
        const val MAX_NOTIFICATIONS_PER_REFRESH = 1_000L
        val AVATAR_PATH_PATTERN =
            Regex("""^[0-9a-f-]{36}/[A-Za-z0-9][A-Za-z0-9._-]{0,127}$""")
    }
}

internal fun miiAvatarPath(
    accountId: UserId,
    revision: Long,
    clientOperationId: String,
): String {
    require(revision > 0L) { "Mii revision must be positive" }
    val canonicalAccountId = canonicalUuid(accountId.value)
    val canonicalOperationId = canonicalUuid(clientOperationId)
    require(accountId.value.equals(canonicalAccountId, ignoreCase = true)) {
        "Mii account id must be a canonical UUID"
    }
    require(clientOperationId.equals(canonicalOperationId, ignoreCase = true)) {
        "Mii client operation id must be a canonical UUID"
    }
    return "$canonicalAccountId/mii-r$revision-$canonicalOperationId.png"
}


internal fun messageMediaPath(
    accountId: UserId,
    conversationId: ConversationId,
    messageId: MessageId,
    mimeType: String,
): String {
    val account = canonicalUuid(accountId.value)
    val conversation = canonicalUuid(conversationId.value)
    val message = canonicalUuid(messageId.value)
    val extension = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    return "$account/$conversation/$message.$extension"
}

private class RemoteNotFoundException : IllegalStateException()

@Serializable
private data class SaveProfileMiiSlotRpc(
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
    @SerialName("p_revision")
    val revision: Long,
    @SerialName("p_schema_version")
    val schemaVersion: Int,
    @SerialName("p_appearance")
    val appearance: JsonObject,
    @SerialName("p_avatar_path")
    val avatarPath: String,
    @SerialName("p_canonical_miic_base64")
    val canonicalMiicBase64: String?,
    @SerialName("p_slot")
    val slot: Int,
)

@Serializable
private data class SetActiveMiiSlotRpc(
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
    @SerialName("p_slot")
    val slot: Int,
)

@Serializable
private data class DeleteProfileMiiSlotRpc(
    @SerialName("p_slot")
    val slot: Int,
)

@Serializable
private data class DeleteProfileMiiSlotDto(
    val deleted: Boolean = false,
    @SerialName("avatar_path")
    val avatarPath: String? = null,
)

@Serializable
private data class LeaderboardRpc(
    @SerialName("p_scope")
    val scope: String,
)

@Serializable
private data class FriendProfileStatsRpc(
    @SerialName("p_friend_user_id")
    val friendUserId: String,
)

@Serializable
private data class FriendProfileStatsDto(
    @SerialName("encounter_count")
    val encounterCount: Long,
    @SerialName("trophy_count")
    val trophyCount: Long,
)

@Serializable
private data class DirectConversationRpc(
    @SerialName("p_other_user_id")
    val otherUserId: String,
    @SerialName("p_client_operation_id")
    val clientOperationId: String,
)

@Serializable
private data class ActiveMiiSlotDto(
    @SerialName("user_id")
    val userId: String,
    val slot: Int,
    @SerialName("avatar_path")
    val avatarPath: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
private data class MiiProfilePublicationDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("schema_version")
    val schemaVersion: Int,
    val revision: Long,
    @SerialName("avatar_path")
    val avatarPath: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("superseded_avatar_path")
    val supersededAvatarPath: String? = null,
)

@Serializable
private data class ProfileMiiRowDto(
    val slot: Int,
    val appearance: JsonObject,
    val revision: Long,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("avatar_path")
    val avatarPath: String,
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
private data class IssueNearbyCredentialsRpc(
    @SerialName("p_signing_public_keys")
    val signingPublicKeys: List<String>,
)

@Serializable
private data class IssuedNearbyCredentialDto(
    val token: String,
    @SerialName("signing_public_key")
    val signingPublicKey: String,
    @SerialName("expires_at")
    val expiresAt: String,
)

@Serializable
private data class SubmitNearbyEncounterRpc(
    @SerialName("p_encounter_id")
    val encounterId: String,
    @SerialName("p_reporter_operation_id")
    val reporterOperationId: String,
    @SerialName("p_own_token")
    val ownToken: String,
    @SerialName("p_peer_token")
    val peerToken: String,
    @SerialName("p_own_signing_public_key")
    val ownSigningPublicKey: String,
    @SerialName("p_peer_signing_public_key")
    val peerSigningPublicKey: String,
    @SerialName("p_transcript_hash")
    val transcriptHash: String,
    @SerialName("p_own_signature")
    val ownSignature: String,
    @SerialName("p_peer_signature")
    val peerSignature: String,
    @SerialName("p_occurred_at")
    val occurredAt: String,
)

@Serializable
private data class NearbyEncounterDto(
    @SerialName("encounter_id")
    val encounterId: String,
    @SerialName("remote_user_id")
    val remoteUserId: String,
    @SerialName("display_name")
    val displayName: String,
    val bio: String,
    @SerialName("avatar_path")
    val avatarPath: String?,
    val age: Int?,
    @SerialName("country_code")
    val countryCode: String?,
    @SerialName("location_label")
    val locationLabel: String?,
    @SerialName("last_seen_at")
    val lastSeenAt: String?,
    @SerialName("profile_updated_at")
    val profileUpdatedAt: String,
    @SerialName("occurred_at")
    val occurredAt: String,
    @SerialName("resolved_at")
    val resolvedAt: String,
)

@Serializable
private data class ProfilePatchDto(
    val bio: String,
    @SerialName("avatar_path")
    val avatarPath: String?,
    val age: Int?,
    @SerialName("country_code")
    val countryCode: String?,
)

@Serializable
private data class AccountSetupPatchDto(
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    val bio: String,
    val age: Int?,
    @SerialName("country_code")
    val countryCode: String,
)

@Serializable
private data class RenameProfilePatchDto(
    val username: String,
    @SerialName("display_name")
    val displayName: String,
)

private fun FriendshipDto.otherUserId(accountId: String): String =
    if (userLow == accountId) userHigh else userLow

private fun ConversationDto.otherDirectUserId(accountId: String): String? =
    when (accountId) {
        directUserLow -> directUserHigh
        directUserHigh -> directUserLow
        else -> null
    }

private suspend fun <T> remoteResult(
    operation: suspend () -> T,
): RepositoryResult<T> = try {
    RepositoryResult.Success(operation())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    logPlatformWarning(
        "PocketPassRemote",
        "Remote adapter failure: ${error::class.qualifiedName ?: "unknown"}; " +
            "cause=${error.cause?.let { it::class.qualifiedName } ?: "none"}",
    )
    RepositoryResult.Failure(error.toRemoteFailure())
}

private const val PURCHASE_ALREADY_OWNED = "ALREADY_OWNED"

private val PURCHASE_REJECTION_HINTS = setOf(
    ShopPurchaseRejection.InsufficientTokens.code,
    ShopPurchaseRejection.ItemUnavailable.code,
    PURCHASE_ALREADY_OWNED,
)

private fun PostgrestRestException.purchaseRejection(): String? =
    hint?.takeIf { it in PURCHASE_REJECTION_HINTS } ?: when (code ?: "PT$statusCode") {
        "PT402" -> ShopPurchaseRejection.InsufficientTokens.code
        "PT409" -> PURCHASE_ALREADY_OWNED
        "PT410" -> ShopPurchaseRejection.ItemUnavailable.code
        else -> null
    }

private class OAuthServerException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

private const val APP_SUSPENDED_HINT = "APP_SUSPENDED"

private val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

private fun canonicalUuidOrNull(value: String): String? =
    value.trim().lowercase().takeIf(UUID_PATTERN::matches)

private fun canonicalUuid(value: String): String =
    requireNotNull(canonicalUuidOrNull(value)) { "Malformed UUID" }

private val OAuthJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun Int.toRemoteFailureKind(): RepositoryFailureKind = when (this) {
    400, 422 -> RepositoryFailureKind.Validation
    401 -> RepositoryFailureKind.Unauthorized
    403 -> RepositoryFailureKind.Forbidden
    404, 410 -> RepositoryFailureKind.NotFound
    409 -> RepositoryFailureKind.Conflict
    429 -> RepositoryFailureKind.RateLimited
    408, 425, in 500..599 -> RepositoryFailureKind.Unavailable
    else -> RepositoryFailureKind.Unknown
}

private fun Throwable.toRemoteFailure(): RepositoryFailure {
    val kind = when (this) {
        is RestException -> statusCode.toRemoteFailureKind()
        is OAuthServerException -> statusCode.toRemoteFailureKind()
        is MiiHatNotOwnedException -> RepositoryFailureKind.Forbidden

        is IOException -> RepositoryFailureKind.Offline
        is RemoteNotFoundException -> RepositoryFailureKind.NotFound
        is IllegalArgumentException -> RepositoryFailureKind.Validation
        else -> RepositoryFailureKind.Unknown
    }
    return RepositoryFailure(
        kind = kind,
        message = if (this is MiiHatNotOwnedException) MII_HAT_NOT_OWNED_MESSAGE else when (kind) {
            RepositoryFailureKind.Offline -> "PocketPass is offline"
            RepositoryFailureKind.Unauthorized -> "Authentication is required"
            RepositoryFailureKind.Forbidden -> "This account cannot perform that action"
            RepositoryFailureKind.NotFound -> "The requested data was not found"
            RepositoryFailureKind.Conflict -> "The operation conflicts with current data"
            RepositoryFailureKind.Validation -> "The operation contains invalid data"
            RepositoryFailureKind.RateLimited -> "Too many requests"
            RepositoryFailureKind.Unavailable -> "The PocketPass service is temporarily unavailable"
            RepositoryFailureKind.Misconfigured -> "The PocketPass service is not configured"
            RepositoryFailureKind.Unknown -> "An unexpected backend error occurred"
        },
        retryable = kind == RepositoryFailureKind.Offline ||
            kind == RepositoryFailureKind.RateLimited ||
            kind == RepositoryFailureKind.Unavailable,
    )
}

private val MiiPublicationJson = Json {
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}
