package ilkadam.ilksohbet.domain.usecase.chatScreen

import ilkadam.ilksohbet.domain.repository.ChatScreenRepository

class LoadMessageFromFirebase(
    private val chatScreenRepository: ChatScreenRepository
) {
    suspend operator fun invoke(
        chatRoomUUID: String,
        opponentUUID: String,
        registerUUID: String
    ) = chatScreenRepository.loadMessagesFromFirebase(chatRoomUUID, opponentUUID, registerUUID)
}