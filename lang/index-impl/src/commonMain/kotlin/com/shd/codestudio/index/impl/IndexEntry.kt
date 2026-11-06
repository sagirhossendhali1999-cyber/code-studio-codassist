package com.shd.codestudio.index.impl

import com.shd.codestudio.index.IndexOrigin

/** One (term, value, origin) entry handed to the indexer — the unit both the segment and the source side store. */
internal class IndexEntry(val term: String, val value: Any, val origin: IndexOrigin)
