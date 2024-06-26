package ilkadam.ilksohbet.presentation.profile

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ilkadam.ilksohbet.R
import ilkadam.ilksohbet.domain.model.User
import ilkadam.ilksohbet.domain.usecase.profileScreen.ProfileScreenUseCases
import ilkadam.ilksohbet.utils.Response
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val useCases: ProfileScreenUseCases,
    private val application: Application
) : ViewModel() {
    var toastMessage = mutableStateOf("")
        private set

    var isLoading = mutableStateOf(false)
        private set

    var isUserSignOutState = mutableStateOf(false)
        private set

    var userDataStateFromFirebase = mutableStateOf(User())
        private set

    init {
        loadProfileFromFirebase()
    }

    //PUBLIC FUNCTIONS

    /*fun setUserStatusToFirebaseAndSignOut(userStatus: UserStatus){
        viewModelScope.launch {
            useCases.setUserStatusToFirebase(userStatus).collect{ response ->
                when(response){
                    is Response.Loading -> {}
                    is Response.Success -> {
                        if(response.data){
                            signOut()
                        }else{
                            //Auth.currentuser null.
                        }

                    }
                    is Response.Error -> {}
                }
            }
        }
    }*/

    fun uploadPictureToFirebase(uri: Uri) {
        viewModelScope.launch {
            useCases.uploadPictureToFirebase(uri).collect { response ->
                when (response) {
                    is Response.Loading -> {
                        isLoading.value = true
                    }

                    is Response.Success -> {
                        //Picture Uploaded
                        isLoading.value = false
                        updateProfileToFirebase(
                            User(userProfilePictureUrl = response.data)
                        )
                    }

                    is Response.Error -> {}
                }

            }
        }
    }

    fun updateProfileToFirebase(myUser: User) {
        viewModelScope.launch {
            useCases.createOrUpdateProfileToFirebase(myUser).collect { response ->
                when (response) {
                    is Response.Loading -> {
                        toastMessage.value = ""
                        isLoading.value = true
                    }

                    is Response.Success -> {
                        isLoading.value = false
                        if (response.data) {
                            toastMessage.value = application.getString(R.string.profile_updated)
                        } else {
                            toastMessage.value = application.getString(R.string.profile_saved)
                        }
                        loadProfileFromFirebase()
                    }

                    is Response.Error -> {
                        toastMessage.value = application.getString(R.string.update_failed)
                    }
                }
            }
        }
    }


    //PRIVATE FUNCTIONS

    /*private fun signOut() {
        viewModelScope.launch {
            useCases.signOut().collect { response ->
                when(response) {
                    is Response.Loading -> {
                        toastMessage.value = ""
                    }
                    is Response.Success -> {
                        isUserSignOutState.value = response.data
                        toastMessage.value = application.getString(R.string.sign_out)
                    }
                    is Response.Error -> Log.d(ContentValues.TAG, response.message)
                }

            }
        }
    }*/

    private fun loadProfileFromFirebase() {
        viewModelScope.launch {
            useCases.loadProfileFromFirebase().collect { response ->
                when (response) {
                    is Response.Loading -> {
                        isLoading.value = true
                    }

                    is Response.Success -> {
                        userDataStateFromFirebase.value = response.data
                        //delay(500)
                        isLoading.value = false
                    }

                    is Response.Error -> {
//                        toastMessage.value = response.message
                    }
                }
            }
        }
    }
}