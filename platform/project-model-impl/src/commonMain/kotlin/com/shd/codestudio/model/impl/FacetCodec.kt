package com.shd.codestudio.model.impl

/**
 * Compatibility aliases. [com.shd.codestudio.model.FacetCodecRegistry] and [com.shd.codestudio.model.FacetData] moved into
 * `project-model-api` so an externally-packaged plugin can reach them: `project-model-impl` is not a
 * published artifact, so a registry that lived here was host-only by construction.
 */
@Deprecated(
    "Moved to project-model-api",
    ReplaceWith("FacetCodecRegistry", "com.shd.codestudio.model.FacetCodecRegistry"),
)
typealias FacetCodecRegistry = com.shd.codestudio.model.FacetCodecRegistry

@Deprecated("Moved to project-model-api", ReplaceWith("FacetData", "com.shd.codestudio.model.FacetData"))
typealias FacetData = com.shd.codestudio.model.FacetData
