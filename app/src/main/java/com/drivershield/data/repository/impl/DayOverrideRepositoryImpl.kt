package com.drivershield.data.repository.impl

import com.drivershield.data.local.db.dao.DayOverrideDao
import com.drivershield.data.local.db.entity.DayOverrideEntity
import com.drivershield.domain.model.DayOverride
import com.drivershield.domain.repository.DayOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayOverrideRepositoryImpl @Inject constructor(
    private val dao: DayOverrideDao
) : DayOverrideRepository {

    override suspend fun getOverride(date: LocalDate): DayOverride? {
        return dao.getOverride(date.toEpochDay())?.toDomain()
    }

    override fun getOverridesInRange(start: LocalDate, end: LocalDate): Flow<List<DayOverride>> {
        return dao.getOverridesInRange(start.toEpochDay(), end.toEpochDay())
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun upsertOverride(date: LocalDate, isLibranza: Boolean) {
        dao.insertOverride(
            DayOverrideEntity(
                dateEpochDay = date.toEpochDay(),
                isLibranza = isLibranza,
                manualOverride = true
            )
        )
    }

    override suspend fun deleteOverride(date: LocalDate) {
        dao.deleteOverride(date.toEpochDay())
    }

    private fun DayOverrideEntity.toDomain(): DayOverride = DayOverride(
        date = LocalDate.ofEpochDay(dateEpochDay),
        isLibranza = isLibranza
    )
}
