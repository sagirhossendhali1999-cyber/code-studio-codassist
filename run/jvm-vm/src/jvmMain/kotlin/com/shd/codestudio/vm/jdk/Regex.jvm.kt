package com.shd.codestudio.vm.jdk

internal actual fun groupRange(match: MatchResult, group: Int): IntRange? = match.groups[group]?.range
