package ilkadam.ilkmuhabbet.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.getValue
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.onesignal.OneSignal
import ilkadam.ilkmuhabbet.core.Constants
import ilkadam.ilkmuhabbet.domain.model.ChatMessage
import ilkadam.ilkmuhabbet.domain.model.FriendListRegister
import ilkadam.ilkmuhabbet.domain.model.FriendStatus
import ilkadam.ilkmuhabbet.domain.model.User
import ilkadam.ilkmuhabbet.domain.repository.DiscoverScreenRepository
import ilkadam.ilkmuhabbet.domain.repository.ProfileScreenRepository
import ilkadam.ilkmuhabbet.domain.usecase.profileScreen.ProfileScreenUseCases
import ilkadam.ilkmuhabbet.utils.Response
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class DiscoverScreenRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val storage: FirebaseStorage,
    private val profileScreenRepository: ProfileScreenRepository
) : DiscoverScreenRepository {

    override suspend fun getRandomUserFromFirebase(): Flow<Response<User?>> = callbackFlow {
        try {
            this@callbackFlow.trySendBlocking(Response.Loading)
            val databaseReference = database.getReference("Profiles")
            databaseReference.get()
                .addOnSuccessListener { snapshot ->
                    val job = launch {
                        /*val min = System.currentTimeMillis() - 10 * 24 * 60 * 60 * 1000;
                        val max = System.currentTimeMillis()*/
                        val userList = snapshot.children.toList()
                        var selectedUser = userList.random().child("profile").getValue<User>()
                        while (selectedUser?.profileUUID.equals(auth.uid.toString())) {
                            selectedUser = userList.random().child("profile").getValue<User>()
                        }
                        this@callbackFlow.trySendBlocking(
                            Response.Success(
                                selectedUser
                            )
                        )


                        /*for (user in snapshot.children) {
                            val created = user.child("created").value.toString().toLong()

                            created?.let {
                                if (created > min && created < max) {
                                    this@callbackFlow.trySendBlocking(
                                        Response.Success(
                                            user.child("profile").getValue<User>()
                                        )
                                    )
                                } else {
                                    this@callbackFlow.trySendBlocking(
                                        Response.Success(null)
                                    )
                                }
                            }

                        }*/
                    }

                }
                .addOnFailureListener {
                    this@callbackFlow.trySendBlocking(
                        Response.Error(
                            it.message ?: Constants.ERROR_MESSAGE
                        )
                    )
                }
            awaitClose {
                channel.close()
                cancel()
            }
        } catch (exception: Exception) {
            this@callbackFlow.trySendBlocking(
                Response.Error(
                    exception.message ?: Constants.ERROR_MESSAGE
                )
            )
        }
    }

    override suspend fun checkChatRoomExistedFromFirebase(acceptorUser: User): Flow<Response<String>> =
        callbackFlow {
            try {
                this@callbackFlow.trySendBlocking(Response.Loading)

                val requesterUUID = auth.currentUser?.uid

                val hashMapOfRequesterUUIDAndAcceptorUUID = hashMapOf<String, String>()
                hashMapOfRequesterUUIDAndAcceptorUUID[requesterUUID!!] = acceptorUser.profileUUID

                val hashMapOfAcceptorUUIDAndRequesterUUID = hashMapOf<String, String>()
                hashMapOfAcceptorUUIDAndRequesterUUID[acceptorUser.profileUUID] = requesterUUID

                val gson = Gson()
                val requesterUUIDAndAcceptorUUID =
                    gson.toJson(hashMapOfRequesterUUIDAndAcceptorUUID)
                val acceptorUUIDAndRequesterUUID =
                    gson.toJson(hashMapOfAcceptorUUIDAndRequesterUUID)

                val databaseReference = database.getReference("Chat_Rooms")

                databaseReference.get().addOnSuccessListener {
                    try {
                        var keyListForControl = listOf<String>()
                        val hashMapForControl = hashMapOf<String, Any>()
                        for (i in it.children) {
                            val key = i.key as String
                            keyListForControl = keyListForControl + key
                            val hashMap: Map<String, Any> = Gson().fromJson(
                                i.key,
                                object : TypeToken<HashMap<String?, Any?>?>() {}.type
                            )

                            hashMapForControl.putAll(hashMap)
                        }

                        val chatRoomUUIDString: String?

                        if (keyListForControl.contains(requesterUUIDAndAcceptorUUID)) {

                            //ChatRoom opened by Requester
                            val hashMapOfRequesterUUIDAndAcceptorUUIDForSaveMessagesToFirebase =
                                hashMapOf<String, Any>()
                            hashMapOfRequesterUUIDAndAcceptorUUIDForSaveMessagesToFirebase[requesterUUID] =
                                acceptorUser.profileUUID

                            val gson = Gson()
                            chatRoomUUIDString = gson.toJson(
                                hashMapOfRequesterUUIDAndAcceptorUUIDForSaveMessagesToFirebase
                            )

                            this@callbackFlow.trySendBlocking(Response.Success(chatRoomUUIDString!!))

                        } else if (keyListForControl.contains(acceptorUUIDAndRequesterUUID)) {

                            //ChatRoom opened by Acceptor
                            val hashMapOfAcceptorUUIDAndRequesterUUIDForSaveMessagesToFirebase =
                                hashMapOf<String, Any>()
                            hashMapOfAcceptorUUIDAndRequesterUUIDForSaveMessagesToFirebase[acceptorUser.profileUUID] =
                                requesterUUID

                            val gson = Gson()
                            chatRoomUUIDString = gson.toJson(
                                hashMapOfAcceptorUUIDAndRequesterUUIDForSaveMessagesToFirebase
                            )

                            this@callbackFlow.trySendBlocking(Response.Success(chatRoomUUIDString!!))

                        } else {
                            this@callbackFlow.trySendBlocking(
                                Response.Success(
                                    Constants.NO_CHATROOM_IN_FIREBASE_DATABASE
                                )
                            )
                        }
                    } catch (e: JsonSyntaxException) {
                        this@callbackFlow.trySendBlocking(
                            Response.Error(
                                e.message ?: Constants.ERROR_MESSAGE
                            )
                        )
                    }
                }

                awaitClose {
                    channel.close()
                    cancel()
                }

            } catch (e: Exception) {
                this@callbackFlow.trySendBlocking(
                    Response.Error(
                        e.message ?: Constants.ERROR_MESSAGE
                    )
                )
            }
        }


    override suspend fun createChatRoomToFirebase(acceptorUser: User): Flow<Response<String>> =
        flow {
            try {
                emit(Response.Loading)

                val requesterUUID = auth.currentUser?.uid

                val hashMapOfRequesterUUIDAndAcceptorUUID = hashMapOf<String, String>()
                hashMapOfRequesterUUIDAndAcceptorUUID[requesterUUID!!] = acceptorUser.profileUUID

                val databaseReference = database.getReference("Chat_Rooms")

                val gson = Gson()
                val requesterUUIDAndAcceptorUUID =
                    gson.toJson(hashMapOfRequesterUUIDAndAcceptorUUID)

                databaseReference
                    .child(requesterUUIDAndAcceptorUUID)
                    .setValue(true)
                    .await()

                emit(Response.Success(requesterUUIDAndAcceptorUUID))

            } catch (e: Exception) {
                emit(Response.Error(e.message ?: Constants.ERROR_MESSAGE))
            }
        }

    override suspend fun checkFriendListRegisterIsExistedFromFirebase(acceptorUser: User): Flow<Response<FriendListRegister?>> =
        callbackFlow {
            try {
                this@callbackFlow.trySendBlocking(Response.Loading)
                val requesterUUID = auth.currentUser?.uid
                val databaseReference = database.getReference("Friend_List")

                databaseReference.get().addOnSuccessListener {
//                    var result = FriendListRegister()
                    var result: FriendListRegister? = null
                    val job = launch {
                        for (i in it.children) {
                            val friendListRegister = i.getValue(FriendListRegister::class.java)
                            if (friendListRegister?.requesterUUID == requesterUUID && friendListRegister?.acceptorUUID == acceptorUser.profileUUID) {
                                result = friendListRegister
                            } else if (friendListRegister?.requesterUUID == acceptorUser.profileUUID && friendListRegister.acceptorUUID == requesterUUID) {
                                result = friendListRegister
                            }
                        }
                    }

                    job.invokeOnCompletion {
                        this@callbackFlow.trySendBlocking(Response.Success(result))
                    }
                }

                awaitClose {
                    channel.close()
                    cancel()
                }

            } catch (e: Exception) {
                this@callbackFlow.trySendBlocking(Response.Error(e.message ?: "Error Message"))
            }
        }

    override suspend fun createFriendListRegisterToFirebase(
        chatRoomUUID: String,
        acceptorUser: User,
        requesterUser: User
    ): Flow<Response<Boolean>> =
        flow {
            try {
                emit(Response.Loading)

                val registerUUID = UUID.randomUUID().toString()

                val requesterUUID = auth.currentUser?.uid
                val requesterOneSignalUserId = OneSignal.getDeviceState()?.userId

                var requesterEmail = requesterUser.userEmail
                var requesterUserName = requesterUser.userName

                val databaseReference = database.getReference("Friend_List")

                val friendListRegister =
                    FriendListRegister(
                        chatRoomUUID,
                        registerUUID,
                        requesterEmail,
                        requesterUUID!!,
                        requesterOneSignalUserId!!,
                        acceptorUser.userEmail,
                        acceptorUser.profileUUID,
                        acceptorUser.oneSignalUserId,
                        FriendStatus.PENDING.toString(),
                        ChatMessage(),
                        acceptorUser.userName,
                        requesterUserName
                    )

                databaseReference
                    .child(registerUUID)
                    .setValue(friendListRegister)
                    .await()

                emit(Response.Success(true))

            } catch (e: Exception) {
                emit(Response.Error(e.message ?: Constants.ERROR_MESSAGE))
            }
        }

    override suspend fun openBlockedFriendToFirebase(registerUUID: String): Flow<Response<Boolean>> =
        callbackFlow {
            try {
                this@callbackFlow.trySendBlocking(Response.Loading)

                val myUUID = auth.currentUser?.uid

                val databaseReference =
                    database.getReference("Friend_List").child(registerUUID)


                databaseReference.get().addOnSuccessListener {

                    val result = it.value as Map<*, *>

                    if (result["blockedby"] == myUUID) {
                        val childUpdates = mutableMapOf<String, Any?>()
                        childUpdates["/status/"] = FriendStatus.ACCEPTED.toString()
                        childUpdates["/blockedby/"] = null

                        databaseReference.updateChildren(childUpdates)

                        this@callbackFlow.trySendBlocking(Response.Success(true))
                    } else {
                        this@callbackFlow.trySendBlocking(Response.Success(false))
                    }
                }
            } catch (e: Exception) {
                this@callbackFlow.trySendBlocking(
                    Response.Error(
                        e.message ?: Constants.ERROR_MESSAGE
                    )
                )
            }

            awaitClose {
                channel.close()
                cancel()
            }
        }

    /*private fun getRandomTimestamp(min: Long, max: Long) =
        floor(Math.random() * (max - min)) + min*/

}