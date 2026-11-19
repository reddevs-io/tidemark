// SPDX-License-Identifier: GPL-3.0-or-later
package io.reddevs.tidemark.explorer

import android.content.Context
import androidx.annotation.StringRes
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import io.reddevs.tidemark.R

/**
 * One Health Connect integer constant, its SDK name and its readable label. A null [label] shows
 * the humanized constant name instead, for constants added before their translation.
 */
class ConstantName(val value: Int, val constant: String, @StringRes val label: Int?)

/** A table of Health Connect integer constants with readable names and a fallback for unknown values. */
class ConstantTable(entries: List<ConstantName>, private val prefix: String) {
    private val byValue = entries.associateBy { it.value }

    /** The readable name, else the humanized constant name, else "Unknown value (n)" for values newer than this table. */
    fun label(context: Context, value: Int): String {
        val entry = byValue[value] ?: return context.getString(R.string.explorer_value_unknown_constant, value)
        return entry.label?.let(context::getString) ?: humanize(entry.constant, prefix)
    }

    /** The stored value with its SDK constant name, for example "EXERCISE_TYPE_RUNNING = 56". */
    fun raw(value: Int): String = byValue[value]?.let { "${it.constant} = $value" } ?: value.toString()

    companion object {
        /** "EXERCISE_TYPE_RUNNING_TREADMILL" with prefix "EXERCISE_TYPE_" becomes "Running treadmill". */
        fun humanize(constant: String, prefix: String = ""): String =
            constant.removePrefix(prefix).lowercase().replace('_', ' ').trim().replaceFirstChar { it.titlecase() }
    }
}

object ConstantNames {
    val exerciseTypes: List<ConstantName> = listOf(
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, "EXERCISE_TYPE_OTHER_WORKOUT", R.string.exercise_type_other_workout),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON, "EXERCISE_TYPE_BADMINTON", R.string.exercise_type_badminton),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL, "EXERCISE_TYPE_BASEBALL", R.string.exercise_type_baseball),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL, "EXERCISE_TYPE_BASKETBALL", R.string.exercise_type_basketball),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, "EXERCISE_TYPE_BIKING", R.string.exercise_type_biking),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY, "EXERCISE_TYPE_BIKING_STATIONARY", R.string.exercise_type_biking_stationary),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP, "EXERCISE_TYPE_BOOT_CAMP", R.string.exercise_type_boot_camp),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_BOXING, "EXERCISE_TYPE_BOXING", R.string.exercise_type_boxing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS, "EXERCISE_TYPE_CALISTHENICS", R.string.exercise_type_calisthenics),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_CRICKET, "EXERCISE_TYPE_CRICKET", R.string.exercise_type_cricket),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_DANCING, "EXERCISE_TYPE_DANCING", R.string.exercise_type_dancing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL, "EXERCISE_TYPE_ELLIPTICAL", R.string.exercise_type_elliptical),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS, "EXERCISE_TYPE_EXERCISE_CLASS", R.string.exercise_type_exercise_class),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_FENCING, "EXERCISE_TYPE_FENCING", R.string.exercise_type_fencing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN, "EXERCISE_TYPE_FOOTBALL_AMERICAN", R.string.exercise_type_football_american),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN, "EXERCISE_TYPE_FOOTBALL_AUSTRALIAN", R.string.exercise_type_football_australian),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC, "EXERCISE_TYPE_FRISBEE_DISC", R.string.exercise_type_frisbee_disc),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_GOLF, "EXERCISE_TYPE_GOLF", R.string.exercise_type_golf),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING, "EXERCISE_TYPE_GUIDED_BREATHING", R.string.exercise_type_guided_breathing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS, "EXERCISE_TYPE_GYMNASTICS", R.string.exercise_type_gymnastics),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL, "EXERCISE_TYPE_HANDBALL", R.string.exercise_type_handball),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, "EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING", R.string.exercise_type_high_intensity_interval_training),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_HIKING, "EXERCISE_TYPE_HIKING", R.string.exercise_type_hiking),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY, "EXERCISE_TYPE_ICE_HOCKEY", R.string.exercise_type_ice_hockey),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING, "EXERCISE_TYPE_ICE_SKATING", R.string.exercise_type_ice_skating),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS, "EXERCISE_TYPE_MARTIAL_ARTS", R.string.exercise_type_martial_arts),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_PADDLING, "EXERCISE_TYPE_PADDLING", R.string.exercise_type_paddling),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING, "EXERCISE_TYPE_PARAGLIDING", R.string.exercise_type_paragliding),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_PILATES, "EXERCISE_TYPE_PILATES", R.string.exercise_type_pilates),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL, "EXERCISE_TYPE_RACQUETBALL", R.string.exercise_type_racquetball),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING, "EXERCISE_TYPE_ROCK_CLIMBING", R.string.exercise_type_rock_climbing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY, "EXERCISE_TYPE_ROLLER_HOCKEY", R.string.exercise_type_roller_hockey),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_ROWING, "EXERCISE_TYPE_ROWING", R.string.exercise_type_rowing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE, "EXERCISE_TYPE_ROWING_MACHINE", R.string.exercise_type_rowing_machine),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_RUGBY, "EXERCISE_TYPE_RUGBY", R.string.exercise_type_rugby),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, "EXERCISE_TYPE_RUNNING", R.string.exercise_type_running),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL, "EXERCISE_TYPE_RUNNING_TREADMILL", R.string.exercise_type_running_treadmill),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SAILING, "EXERCISE_TYPE_SAILING", R.string.exercise_type_sailing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING, "EXERCISE_TYPE_SCUBA_DIVING", R.string.exercise_type_scuba_diving),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SKATING, "EXERCISE_TYPE_SKATING", R.string.exercise_type_skating),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SKIING, "EXERCISE_TYPE_SKIING", R.string.exercise_type_skiing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING, "EXERCISE_TYPE_SNOWBOARDING", R.string.exercise_type_snowboarding),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING, "EXERCISE_TYPE_SNOWSHOEING", R.string.exercise_type_snowshoeing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SOCCER, "EXERCISE_TYPE_SOCCER", R.string.exercise_type_soccer),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL, "EXERCISE_TYPE_SOFTBALL", R.string.exercise_type_softball),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SQUASH, "EXERCISE_TYPE_SQUASH", R.string.exercise_type_squash),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING, "EXERCISE_TYPE_STAIR_CLIMBING", R.string.exercise_type_stair_climbing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE, "EXERCISE_TYPE_STAIR_CLIMBING_MACHINE", R.string.exercise_type_stair_climbing_machine),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, "EXERCISE_TYPE_STRENGTH_TRAINING", R.string.exercise_type_strength_training),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING, "EXERCISE_TYPE_STRETCHING", R.string.exercise_type_stretching),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SURFING, "EXERCISE_TYPE_SURFING", R.string.exercise_type_surfing),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER, "EXERCISE_TYPE_SWIMMING_OPEN_WATER", R.string.exercise_type_swimming_open_water),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, "EXERCISE_TYPE_SWIMMING_POOL", R.string.exercise_type_swimming_pool),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS, "EXERCISE_TYPE_TABLE_TENNIS", R.string.exercise_type_table_tennis),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_TENNIS, "EXERCISE_TYPE_TENNIS", R.string.exercise_type_tennis),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL, "EXERCISE_TYPE_VOLLEYBALL", R.string.exercise_type_volleyball),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, "EXERCISE_TYPE_WALKING", R.string.exercise_type_walking),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO, "EXERCISE_TYPE_WATER_POLO", R.string.exercise_type_water_polo),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING, "EXERCISE_TYPE_WEIGHTLIFTING", R.string.exercise_type_weightlifting),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR, "EXERCISE_TYPE_WHEELCHAIR", R.string.exercise_type_wheelchair),
        ConstantName(ExerciseSessionRecord.EXERCISE_TYPE_YOGA, "EXERCISE_TYPE_YOGA", R.string.exercise_type_yoga),
    )

    val segmentTypes: List<ConstantName> = listOf(
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_UNKNOWN, "EXERCISE_SEGMENT_TYPE_UNKNOWN", R.string.exercise_segment_unknown),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL, "EXERCISE_SEGMENT_TYPE_ARM_CURL", R.string.exercise_segment_arm_curl),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION, "EXERCISE_SEGMENT_TYPE_BACK_EXTENSION", R.string.exercise_segment_back_extension),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BALL_SLAM, "EXERCISE_SEGMENT_TYPE_BALL_SLAM", R.string.exercise_segment_ball_slam),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS, "EXERCISE_SEGMENT_TYPE_BARBELL_SHOULDER_PRESS", R.string.exercise_segment_barbell_shoulder_press),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS, "EXERCISE_SEGMENT_TYPE_BENCH_PRESS", R.string.exercise_segment_bench_press),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_SIT_UP, "EXERCISE_SEGMENT_TYPE_BENCH_SIT_UP", R.string.exercise_segment_bench_sit_up),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING, "EXERCISE_SEGMENT_TYPE_BIKING", R.string.exercise_segment_biking),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY, "EXERCISE_SEGMENT_TYPE_BIKING_STATIONARY", R.string.exercise_segment_biking_stationary),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE, "EXERCISE_SEGMENT_TYPE_BURPEE", R.string.exercise_segment_burpee),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH, "EXERCISE_SEGMENT_TYPE_CRUNCH", R.string.exercise_segment_crunch),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT, "EXERCISE_SEGMENT_TYPE_DEADLIFT", R.string.exercise_segment_deadlift),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION, "EXERCISE_SEGMENT_TYPE_DOUBLE_ARM_TRICEPS_EXTENSION", R.string.exercise_segment_double_arm_triceps_extension),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_LEFT_ARM, "EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_LEFT_ARM", R.string.exercise_segment_dumbbell_curl_left_arm),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_RIGHT_ARM, "EXERCISE_SEGMENT_TYPE_DUMBBELL_CURL_RIGHT_ARM", R.string.exercise_segment_dumbbell_curl_right_arm),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_FRONT_RAISE, "EXERCISE_SEGMENT_TYPE_DUMBBELL_FRONT_RAISE", R.string.exercise_segment_dumbbell_front_raise),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_LATERAL_RAISE, "EXERCISE_SEGMENT_TYPE_DUMBBELL_LATERAL_RAISE", R.string.exercise_segment_dumbbell_lateral_raise),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW, "EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW", R.string.exercise_segment_dumbbell_row),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_LEFT_ARM, "EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_LEFT_ARM", R.string.exercise_segment_dumbbell_triceps_extension_left_arm),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_RIGHT_ARM, "EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_RIGHT_ARM", R.string.exercise_segment_dumbbell_triceps_extension_right_arm),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_TWO_ARM, "EXERCISE_SEGMENT_TYPE_DUMBBELL_TRICEPS_EXTENSION_TWO_ARM", R.string.exercise_segment_dumbbell_triceps_extension_two_arm),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_ELLIPTICAL, "EXERCISE_SEGMENT_TYPE_ELLIPTICAL", R.string.exercise_segment_elliptical),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_FORWARD_TWIST, "EXERCISE_SEGMENT_TYPE_FORWARD_TWIST", R.string.exercise_segment_forward_twist),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_FRONT_RAISE, "EXERCISE_SEGMENT_TYPE_FRONT_RAISE", R.string.exercise_segment_front_raise),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, "EXERCISE_SEGMENT_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING", R.string.exercise_segment_high_intensity_interval_training),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST, "EXERCISE_SEGMENT_TYPE_HIP_THRUST", R.string.exercise_segment_hip_thrust),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_HULA_HOOP, "EXERCISE_SEGMENT_TYPE_HULA_HOOP", R.string.exercise_segment_hula_hoop),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK, "EXERCISE_SEGMENT_TYPE_JUMPING_JACK", R.string.exercise_segment_jumping_jack),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMP_ROPE, "EXERCISE_SEGMENT_TYPE_JUMP_ROPE", R.string.exercise_segment_jump_rope),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING, "EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING", R.string.exercise_segment_kettlebell_swing),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE, "EXERCISE_SEGMENT_TYPE_LATERAL_RAISE", R.string.exercise_segment_lateral_raise),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN, "EXERCISE_SEGMENT_TYPE_LAT_PULL_DOWN", R.string.exercise_segment_lat_pull_down),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL, "EXERCISE_SEGMENT_TYPE_LEG_CURL", R.string.exercise_segment_leg_curl),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_EXTENSION, "EXERCISE_SEGMENT_TYPE_LEG_EXTENSION", R.string.exercise_segment_leg_extension),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_PRESS, "EXERCISE_SEGMENT_TYPE_LEG_PRESS", R.string.exercise_segment_leg_press),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE, "EXERCISE_SEGMENT_TYPE_LEG_RAISE", R.string.exercise_segment_leg_raise),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE, "EXERCISE_SEGMENT_TYPE_LUNGE", R.string.exercise_segment_lunge),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER, "EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER", R.string.exercise_segment_mountain_climber),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT, "EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT", R.string.exercise_segment_other_workout),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PAUSE, "EXERCISE_SEGMENT_TYPE_PAUSE", R.string.exercise_segment_pause),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PILATES, "EXERCISE_SEGMENT_TYPE_PILATES", R.string.exercise_segment_pilates),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK, "EXERCISE_SEGMENT_TYPE_PLANK", R.string.exercise_segment_plank),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP, "EXERCISE_SEGMENT_TYPE_PULL_UP", R.string.exercise_segment_pull_up),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PUNCH, "EXERCISE_SEGMENT_TYPE_PUNCH", R.string.exercise_segment_punch),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST, "EXERCISE_SEGMENT_TYPE_REST", R.string.exercise_segment_rest),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_ROWING_MACHINE, "EXERCISE_SEGMENT_TYPE_ROWING_MACHINE", R.string.exercise_segment_rowing_machine),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING, "EXERCISE_SEGMENT_TYPE_RUNNING", R.string.exercise_segment_running),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL, "EXERCISE_SEGMENT_TYPE_RUNNING_TREADMILL", R.string.exercise_segment_running_treadmill),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS, "EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS", R.string.exercise_segment_shoulder_press),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SINGLE_ARM_TRICEPS_EXTENSION, "EXERCISE_SEGMENT_TYPE_SINGLE_ARM_TRICEPS_EXTENSION", R.string.exercise_segment_single_arm_triceps_extension),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP, "EXERCISE_SEGMENT_TYPE_SIT_UP", R.string.exercise_segment_sit_up),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, "EXERCISE_SEGMENT_TYPE_SQUAT", R.string.exercise_segment_squat),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING, "EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING", R.string.exercise_segment_stair_climbing),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING_MACHINE, "EXERCISE_SEGMENT_TYPE_STAIR_CLIMBING_MACHINE", R.string.exercise_segment_stair_climbing_machine),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING, "EXERCISE_SEGMENT_TYPE_STRETCHING", R.string.exercise_segment_stretching),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BACKSTROKE, "EXERCISE_SEGMENT_TYPE_SWIMMING_BACKSTROKE", R.string.exercise_segment_swimming_backstroke),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BREASTSTROKE, "EXERCISE_SEGMENT_TYPE_SWIMMING_BREASTSTROKE", R.string.exercise_segment_swimming_breaststroke),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_BUTTERFLY, "EXERCISE_SEGMENT_TYPE_SWIMMING_BUTTERFLY", R.string.exercise_segment_swimming_butterfly),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_FREESTYLE, "EXERCISE_SEGMENT_TYPE_SWIMMING_FREESTYLE", R.string.exercise_segment_swimming_freestyle),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_MIXED, "EXERCISE_SEGMENT_TYPE_SWIMMING_MIXED", R.string.exercise_segment_swimming_mixed),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_OPEN_WATER, "EXERCISE_SEGMENT_TYPE_SWIMMING_OPEN_WATER", R.string.exercise_segment_swimming_open_water),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_OTHER, "EXERCISE_SEGMENT_TYPE_SWIMMING_OTHER", R.string.exercise_segment_swimming_other),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_POOL, "EXERCISE_SEGMENT_TYPE_SWIMMING_POOL", R.string.exercise_segment_swimming_pool),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_UPPER_TWIST, "EXERCISE_SEGMENT_TYPE_UPPER_TWIST", R.string.exercise_segment_upper_twist),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_WALKING, "EXERCISE_SEGMENT_TYPE_WALKING", R.string.exercise_segment_walking),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING, "EXERCISE_SEGMENT_TYPE_WEIGHTLIFTING", R.string.exercise_segment_weightlifting),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_WHEELCHAIR, "EXERCISE_SEGMENT_TYPE_WHEELCHAIR", R.string.exercise_segment_wheelchair),
        ConstantName(ExerciseSegment.EXERCISE_SEGMENT_TYPE_YOGA, "EXERCISE_SEGMENT_TYPE_YOGA", R.string.exercise_segment_yoga),
    )

    val sleepStages: List<ConstantName> = listOf(
        ConstantName(SleepSessionRecord.STAGE_TYPE_UNKNOWN, "STAGE_TYPE_UNKNOWN", R.string.explorer_sleep_stage_unknown),
        ConstantName(SleepSessionRecord.STAGE_TYPE_AWAKE, "STAGE_TYPE_AWAKE", R.string.explorer_sleep_stage_awake),
        ConstantName(SleepSessionRecord.STAGE_TYPE_SLEEPING, "STAGE_TYPE_SLEEPING", R.string.explorer_sleep_stage_sleeping),
        ConstantName(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, "STAGE_TYPE_OUT_OF_BED", R.string.explorer_sleep_stage_out_of_bed),
        ConstantName(SleepSessionRecord.STAGE_TYPE_LIGHT, "STAGE_TYPE_LIGHT", R.string.explorer_sleep_stage_light),
        ConstantName(SleepSessionRecord.STAGE_TYPE_DEEP, "STAGE_TYPE_DEEP", R.string.explorer_sleep_stage_deep),
        ConstantName(SleepSessionRecord.STAGE_TYPE_REM, "STAGE_TYPE_REM", R.string.explorer_sleep_stage_rem),
        ConstantName(SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, "STAGE_TYPE_AWAKE_IN_BED", R.string.explorer_sleep_stage_awake_in_bed),
    )

    val recordingMethods: List<ConstantName> = listOf(
        ConstantName(Metadata.RECORDING_METHOD_UNKNOWN, "RECORDING_METHOD_UNKNOWN", R.string.explorer_recording_method_unknown),
        ConstantName(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED, "RECORDING_METHOD_ACTIVELY_RECORDED", R.string.explorer_recording_method_active),
        ConstantName(Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED, "RECORDING_METHOD_AUTOMATICALLY_RECORDED", R.string.explorer_recording_method_automatic),
        ConstantName(Metadata.RECORDING_METHOD_MANUAL_ENTRY, "RECORDING_METHOD_MANUAL_ENTRY", R.string.explorer_recording_method_manual),
    )

    val deviceTypes: List<ConstantName> = listOf(
        ConstantName(Device.TYPE_UNKNOWN, "TYPE_UNKNOWN", R.string.explorer_device_type_unknown),
        ConstantName(Device.TYPE_WATCH, "TYPE_WATCH", R.string.explorer_device_type_watch),
        ConstantName(Device.TYPE_PHONE, "TYPE_PHONE", R.string.explorer_device_type_phone),
        ConstantName(Device.TYPE_SCALE, "TYPE_SCALE", R.string.explorer_device_type_scale),
        ConstantName(Device.TYPE_RING, "TYPE_RING", R.string.explorer_device_type_ring),
        ConstantName(Device.TYPE_HEAD_MOUNTED, "TYPE_HEAD_MOUNTED", R.string.explorer_device_type_head_mounted),
        ConstantName(Device.TYPE_FITNESS_BAND, "TYPE_FITNESS_BAND", R.string.explorer_device_type_fitness_band),
        ConstantName(Device.TYPE_CHEST_STRAP, "TYPE_CHEST_STRAP", R.string.explorer_device_type_chest_strap),
        ConstantName(Device.TYPE_SMART_DISPLAY, "TYPE_SMART_DISPLAY", R.string.explorer_device_type_smart_display),
    )

    val exerciseType = ConstantTable(exerciseTypes, "EXERCISE_TYPE_")
    val segmentType = ConstantTable(segmentTypes, "EXERCISE_SEGMENT_TYPE_")
    val sleepStage = ConstantTable(sleepStages, "STAGE_TYPE_")
    val recordingMethod = ConstantTable(recordingMethods, "RECORDING_METHOD_")
    val deviceType = ConstantTable(deviceTypes, "TYPE_")
}
