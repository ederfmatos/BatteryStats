package dev.ederfmatos.batterystats.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.ederfmatos.batterystats.domain.update.InstallFailure
import dev.ederfmatos.batterystats.domain.update.InstallStep
import dev.ederfmatos.batterystats.domain.update.UpdateAttempt

/**
 * Uma tentativa de instalação, com o degrau em que ela aconteceu.
 *
 * É o que responde "onde travou?" quando uma atualização não passa — sem isso, a única informação
 * disponível seria um código numérico do PackageInstaller já perdido.
 */
@Entity(
    tableName = "update_attempt",
    indices = [Index(value = ["timestampMs"])],
)
data class UpdateAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val versionCode: Long,
    val step: String,
    val succeeded: Boolean,
    val failure: String?,
    val detail: String?,
    val timestampMs: Long,
)

fun UpdateAttemptEntity.toAttempt(): UpdateAttempt = UpdateAttempt(
    versionCode = versionCode,
    step = runCatching { InstallStep.valueOf(step) }.getOrDefault(InstallStep.DIRECT_LINK),
    succeeded = succeeded,
    failure = failure?.let { name -> runCatching { InstallFailure.valueOf(name) }.getOrNull() },
    detail = detail,
    timestampMs = timestampMs,
)

fun UpdateAttempt.toEntity(): UpdateAttemptEntity = UpdateAttemptEntity(
    versionCode = versionCode,
    step = step.name,
    succeeded = succeeded,
    failure = failure?.name,
    detail = detail,
    timestampMs = timestampMs,
)
