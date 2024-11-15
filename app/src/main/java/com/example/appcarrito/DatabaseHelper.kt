import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

class FirebaseDatabaseHelper {

    private val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("users")
    private val sessionsDatabase: DatabaseReference = FirebaseDatabase.getInstance().getReference("user_sessions")
    private val usageDatabase: DatabaseReference = FirebaseDatabase.getInstance().getReference("user_usage")
    private val profilesDatabase: DatabaseReference = FirebaseDatabase.getInstance().getReference("user_profiles")


    fun registerUser(email: String, password: String) {
        val userId = database.push().key ?: return
        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val user = User(
            id = userId,
            email = email,
            password = password,
            registrationDate = currentDateTime,
            deviceModel = Build.MODEL
        )
        database.child(userId).setValue(user)
    }

    fun recordLoginSession(userId: String) {
        val sessionId = sessionsDatabase.child(userId).push().key ?: return
        val currentDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        sessionsDatabase.child(userId).child(sessionId).setValue(currentDateTime)
    }

    fun updateUsageData(userId: String, distance: Double, timeInMinutes: Double) {
        usageDatabase.child(userId).get().addOnSuccessListener { dataSnapshot ->
            val currentDistance = dataSnapshot.child("totalDistance").getValue(Double::class.java) ?: 0.0
            val currentTime = dataSnapshot.child("totalTime").getValue(Double::class.java) ?: 0.0

            val newDistance = currentDistance + distance
            val newTime = currentTime + timeInMinutes

            val usageData = UsageData(
                totalDistance = newDistance,
                totalTime = newTime
            )

            usageDatabase.child(userId).setValue(usageData)
        }
    }
    fun saveProfileData(userId: String, profileIndex: Int, distance: Int, frequency: Int) {
        val profileData = ProfileData(distance, frequency)
        profilesDatabase.child(userId).child("profile_$profileIndex").setValue(profileData)
    }

    fun loadProfileData(userId: String, profileIndex: Int, callback: (ProfileData?) -> Unit) {
        profilesDatabase.child(userId).child("profile_$profileIndex").get()
            .addOnSuccessListener { dataSnapshot ->
                val profileData = dataSnapshot.getValue(ProfileData::class.java)
                callback(profileData)
            }
            .addOnFailureListener {
                callback(null)
            }
    }

}

data class User(
    val id: String,
    val email: String,
    val password: String,
    val registrationDate: String,
    val deviceModel: String
)

data class UsageData(
    val totalDistance: Double = 0.0,
    val totalTime: Double = 0.0
)

data class ProfileData(
    val distance: Int = 0,
    val frequency: Int = 0
)
