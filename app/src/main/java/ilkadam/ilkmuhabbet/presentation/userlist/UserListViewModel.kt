package ilkadam.ilkmuhabbet.presentation.userlist

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ilkadam.ilkmuhabbet.R
import ilkadam.ilkmuhabbet.core.Constants
import ilkadam.ilkmuhabbet.domain.model.FriendListRegister
import ilkadam.ilkmuhabbet.domain.model.FriendListRow
import ilkadam.ilkmuhabbet.domain.model.FriendStatus
import ilkadam.ilkmuhabbet.domain.usecase.userListScreen.UserListScreenUseCases
import ilkadam.ilkmuhabbet.utils.Response
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val userListScreenUseCases: UserListScreenUseCases,
    private val application: Application
) : ViewModel() {
    var pendingFriendRequestList = mutableStateOf<List<FriendListRegister>>(listOf())
        private set

    var acceptedFriendRequestList = mutableStateOf<List<FriendListRow>>(listOf())
        private set

    var isRefreshing = mutableStateOf(false)
        private set

    var toastMessage = mutableStateOf("")
        private set

    fun refreshingFriendList() {
        viewModelScope.launch {
            isRefreshing.value = true
            loadPendingFriendRequestListFromFirebase()
            loadAcceptFriendRequestListFromFirebase()
            delay(1000)
            isRefreshing.value = false
        }
    }

    fun createFriendshipRegisterToFirebase(acceptorEmail: String) {
        //Search User -> Check Chat Room -> Create Chat Room -> Check FriendListRegister -> Create FriendListRegister
        viewModelScope.launch {
            userListScreenUseCases.searchUserFromFirebase.invoke(acceptorEmail)
                .collect { response ->
                    when (response) {
                        is Response.Loading -> {
                            toastMessage.value = ""
                        }

                        is Response.Success -> {
                            if (response.data != null) {
                                checkChatRoomExistFromFirebaseAndCreateIfNot(
                                    acceptorEmail,
                                    response.data.profileUUID,
                                    response.data.oneSignalUserId
                                )
                            }
                        }

                        is Response.Error -> {}
                    }

                }
        }
    }

    fun acceptPendingFriendRequestToFirebase(registerUUID: String) {
        viewModelScope.launch {
            userListScreenUseCases.acceptPendingFriendRequestToFirebase.invoke(registerUUID)
                .collect { response ->
                    when (response) {
                        is Response.Loading -> {
                            toastMessage.value = ""
                        }

                        is Response.Success -> {
                            toastMessage.value =
                                application.getString(R.string.friend_request_accepted)
                        }

                        is Response.Error -> {}
                    }
                }
        }
    }

    fun cancelPendingFriendRequestToFirebase(registerUUID: String) {
        viewModelScope.launch {
            userListScreenUseCases.rejectPendingFriendRequestToFirebase.invoke(registerUUID)
                .collect { response ->
                    when (response) {
                        is Response.Loading -> {
                            toastMessage.value = ""
                        }

                        is Response.Success -> {
                            toastMessage.value =
                                application.getString(R.string.friend_request_canceled)
                        }

                        is Response.Error -> {}
                    }
                }
        }
    }

    private fun loadAcceptFriendRequestListFromFirebase() {
        viewModelScope.launch {
            userListScreenUseCases.loadAcceptedFriendRequestListFromFirebase.invoke()
                .collect { response ->
                    when (response) {
                        is Response.Loading -> {}
                        is Response.Success -> {
                            if (response.data.isNotEmpty()) {
                                acceptedFriendRequestList.value = response.data
                            }
                        }

                        is Response.Error -> {}
                    }
                }
        }
    }

    private fun loadPendingFriendRequestListFromFirebase() {
        viewModelScope.launch {
            userListScreenUseCases.loadPendingFriendRequestListFromFirebase.invoke()
                .collect { response ->
                    when (response) {
                        is Response.Loading -> {}
                        is Response.Success -> {
                            pendingFriendRequestList.value = response.data
                        }

                        is Response.Error -> {}
                    }
                }
        }
    }

    private fun checkChatRoomExistFromFirebaseAndCreateIfNot(
        acceptorEmail: String,
        acceptorUUID: String,
        acceptorSignalUserId: String
    ) {
        viewModelScope.launch {
            userListScreenUseCases.checkChatRoomExistedFromFirebase.invoke(acceptorUUID)
                .collect { response ->
                    when (response) {
                        is Response.Loading -> {}
                        is Response.Success -> {
                            if (response.data == Constants.NO_CHATROOM_IN_FIREBASE_DATABASE) {
                                createChatRoomToFirebase(
                                    acceptorEmail,
                                    acceptorUUID,
                                    acceptorSignalUserId
                                )
                            } else {
                                checkFriendListRegisterIsExistFromFirebase(
                                    response.data,
                                    acceptorEmail,
                                    acceptorUUID,
                                    acceptorSignalUserId
                                )
                            }
                        }

                        is Response.Error -> {}
                    }
                }
        }
    }

    private fun createChatRoomToFirebase(
        acceptorEmail: String,
        acceptorUUID: String,
        acceptorOneSignalUserId: String
    ) {
        viewModelScope.launch {
            userListScreenUseCases.createChatRoomToFirebase.invoke(acceptorUUID)
                .collect { response ->
                    when (response) {
                        is Response.Loading -> {}
                        is Response.Success -> {
                            //Chat Room Created.
                            checkFriendListRegisterIsExistFromFirebase(
                                response.data,
                                acceptorEmail,
                                acceptorUUID,
                                acceptorOneSignalUserId
                            )
                        }

                        is Response.Error -> {}
                    }
                }
        }
    }

    private fun checkFriendListRegisterIsExistFromFirebase(
        chatRoomUUID: String,
        acceptorEmail: String,
        acceptorUUID: String,
        acceptorOneSignalUserId: String
    ) {
        viewModelScope.launch {
            userListScreenUseCases.checkFriendListRegisterIsExistedFromFirebase.invoke(
                acceptorEmail,
                acceptorUUID
            ).collect { response ->
                when (response) {
                    is Response.Loading -> {
                        toastMessage.value = ""
                    }

                    is Response.Success -> {
                        if (response.data.equals(FriendListRegister())) {
                            toastMessage.value = application.getString(R.string.friend_request_sent)
                            createFriendListRegisterToFirebase(
                                chatRoomUUID,
                                acceptorEmail,
                                acceptorUUID,
                                acceptorOneSignalUserId
                            )
                        } else if (response.data.status.equals(FriendStatus.PENDING.toString())) {
                            toastMessage.value =
                                application.getString(R.string.already_have_friend_request)
                        } else if (response.data.status.equals(FriendStatus.ACCEPTED.toString())) {
                            toastMessage.value =
                                application.getString(R.string.you_are_already_friend)
                        } else if (response.data.status.equals(FriendStatus.BLOCKED.toString())) {
                            openBlockedFriendToFirebase(response.data.registerUUID)
                        }
                    }

                    is Response.Error -> {}
                }
            }
        }
    }

    private fun createFriendListRegisterToFirebase(
        chatRoomUUID: String,
        acceptorEmail: String,
        acceptorUUID: String,
        acceptorOneSignalUserId: String
    ) {
        viewModelScope.launch {
            userListScreenUseCases.createFriendListRegisterToFirebase.invoke(
                chatRoomUUID,
                acceptorEmail,
                acceptorUUID,
                acceptorOneSignalUserId
            ).collect { response ->
                when (response) {
                    is Response.Loading -> {}
                    is Response.Success -> {
                    }

                    is Response.Error -> {}
                }

            }
        }
    }

    private fun openBlockedFriendToFirebase(registerUUID: String) {
        viewModelScope.launch {
            userListScreenUseCases.openBlockedFriendToFirebase.invoke(registerUUID)
                .collect { response ->
                    when (response) {
                        is Response.Loading -> {
                            toastMessage.value = ""
                        }

                        is Response.Success -> {
                            if (response.data) {
                                toastMessage.value =
                                    application.getString(R.string.user_block_opened_and_accept_as_friend)
                            } else {
                                toastMessage.value =
                                    application.getString(R.string.you_are_blocked_by_user)
                            }

                        }

                        is Response.Error -> {}
                    }
                }
        }
    }
}