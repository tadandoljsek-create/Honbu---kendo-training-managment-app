package com.honbu.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY name ASC")
    fun getAll(): Flow<List<Member>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(member: Member)

    @Delete
    suspend fun delete(member: Member)
}
