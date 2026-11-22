package com.oldwomanjosiah.kord

interface KordLine {
    val lineIdx: Int

    val contents: String
    val contentsRange: IntRange
}