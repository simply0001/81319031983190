package com.pocketpass.app

import com.pocketpass.app.domain.repository.AchievementsRepository
import com.pocketpass.app.domain.repository.BingoRepository
import com.pocketpass.app.domain.repository.EncounterRepository
import com.pocketpass.app.domain.repository.LeaderboardRepository
import com.pocketpass.app.domain.repository.MessageRepository
import com.pocketpass.app.domain.repository.MutableFriendsRepository
import com.pocketpass.app.domain.repository.NotificationRepository
import com.pocketpass.app.domain.repository.PresenceRepository
import com.pocketpass.app.domain.repository.ProfileRepository
import com.pocketpass.app.domain.repository.SessionRepository
import com.pocketpass.app.domain.repository.ShopRepository
import com.pocketpass.app.domain.repository.SyncRepository
import com.pocketpass.app.domain.repository.WorldTourRepository

data class PocketPassRepositoryGraph(
    val session: SessionRepository,
    val profiles: ProfileRepository,
    val friends: MutableFriendsRepository,
    val conversations: MessageRepository,
    val notifications: NotificationRepository,
    val shop: ShopRepository,
    val leaderboard: LeaderboardRepository,
    val achievements: AchievementsRepository,
    val worldTour: WorldTourRepository,
    val bingo: BingoRepository,
    val encounters: EncounterRepository,
    val presence: PresenceRepository,
    val sync: SyncRepository,
)
