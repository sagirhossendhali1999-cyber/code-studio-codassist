package com.shd.codestudio.lang.jdt

import com.shd.codestudio.index.ClassNameValue
import com.shd.codestudio.index.IndexService
import com.shd.codestudio.testkit.defaultClassNames as kitDefaultClassNames
import com.shd.codestudio.testkit.defaultPackages as kitDefaultPackages
import com.shd.codestudio.testkit.fakeIndex as kitFakeIndex

// The deterministic fake IndexService the regression suites query for auto-import / type-position / package
// completion now lives in com.shd.codestudio.testkit (shared with index-impl and the completion tests). These thin
// re-exports keep the existing lang-jdt call sites unchanged.

fun defaultClassNames(): List<ClassNameValue> = kitDefaultClassNames()

fun defaultPackages(): List<String> = kitDefaultPackages()

fun fakeIndex(
    classNames: List<ClassNameValue> = defaultClassNames(),
    packages: List<String> = defaultPackages(),
): IndexService = kitFakeIndex(classNames, packages)
