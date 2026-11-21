package com.personalfinances.data.db

import androidx.room.TypeConverter
import com.personalfinances.domain.Frequency
import java.time.YearMonth

class Converters {
    @TypeConverter fun accountType(v: String) = AccountType.valueOf(v)
    @TypeConverter fun accountType(v: AccountType) = v.name
    @TypeConverter fun txType(v: String) = TxType.valueOf(v)
    @TypeConverter fun txType(v: TxType) = v.name
    @TypeConverter fun txSource(v: String) = TxSource.valueOf(v)
    @TypeConverter fun txSource(v: TxSource) = v.name
    @TypeConverter fun detectionStatus(v: String) = DetectionStatus.valueOf(v)
    @TypeConverter fun detectionStatus(v: DetectionStatus) = v.name
    @TypeConverter fun frequency(v: String) = Frequency.valueOf(v)
    @TypeConverter fun frequency(v: Frequency) = v.name
}

fun YearMonth.toPeriod(): Int = year * 100 + monthValue
fun Int.toYearMonth(): YearMonth = YearMonth.of(this / 100, this % 100)
