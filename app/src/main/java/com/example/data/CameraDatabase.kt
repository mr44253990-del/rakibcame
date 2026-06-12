package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(entities = [CustomCommand::class, CapturedMedia::class], version = 1, exportSchema = false)
abstract class CameraDatabase : RoomDatabase() {

    abstract fun cameraDao(): CameraDao

    private class CameraDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    val dao = database.cameraDao()

                    // Prefill default custom voice commands
                    dao.insertCustomCommand(
                        CustomCommand(
                            phrase = "rakib selfie",
                            cameraSelection = "FRONT",
                            timerSeconds = 3,
                            filterType = "BEAUTY",
                            resolution = "1080P",
                            isSystem = true
                        )
                    )
                    dao.insertCustomCommand(
                        CustomCommand(
                            phrase = "cinematic video",
                            cameraSelection = "BACK",
                            timerSeconds = 0,
                            filterType = "CINEMATIC",
                            resolution = "4K",
                            frameRate = 60,
                            stabilization = true,
                            isSystem = true
                        )
                    )
                    dao.insertCustomCommand(
                        CustomCommand(
                            phrase = "action sports",
                            cameraSelection = "BACK",
                            timerSeconds = 0,
                            filterType = "HDR",
                            resolution = "1080P",
                            frameRate = 60,
                            stabilization = true,
                            isSystem = true
                        )
                    )

                    // Prefill default high-resolution beautiful images with AI metadata for immediate gallery experience
                    val defaultPhotos = listOf(
                        CapturedMedia(
                            name = "IMG_Mountain_Sunset.jpg",
                            uriPath = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1000",
                            isVideo = false,
                            detectedObjects = "Mountain, Tree, Sky, Lake",
                            detectedScene = "Sunset",
                            isFavorite = true
                        ),
                        CapturedMedia(
                            name = "IMG_Playful_Puppy.jpg",
                            uriPath = "https://images.unsplash.com/photo-1543466835-00a7907e9de1?w=1000",
                            isVideo = false,
                            detectedObjects = "Dog, Grass, Ball, Pet",
                            detectedScene = "Outdoor"
                        ),
                        CapturedMedia(
                            name = "IMG_Paris_Portrait.jpg",
                            uriPath = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=1000",
                            isVideo = false,
                            detectedObjects = "Person, Woman, Smile",
                            detectedScene = "Portrait",
                            isFavorite = true
                        ),
                        CapturedMedia(
                            name = "IMG_Gourmet_Pizza.jpg",
                            uriPath = "https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=1000",
                            isVideo = false,
                            detectedObjects = "Pizza, Food, Plate, Table",
                            detectedScene = "Food"
                        ),
                        CapturedMedia(
                            name = "IMG_Sports_Car.jpg",
                            uriPath = "https://images.unsplash.com/photo-1503376780353-7e6692767b70?w=1000",
                            isVideo = false,
                            detectedObjects = "Car, Vehicle, Street, Wheel",
                            detectedScene = "Landscape"
                        ),
                        CapturedMedia(
                            name = "VID_Cinematic_River.mp4",
                            uriPath = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1000",
                            isVideo = true,
                            detectedObjects = "River, Forest, Sky, Tree",
                            detectedScene = "Landscape"
                        )
                    )

                    for (photo in defaultPhotos) {
                        dao.insertMedia(photo)
                    }
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: CameraDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): CameraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CameraDatabase::class.java,
                    "camera_database"
                )
                .addCallback(CameraDatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
