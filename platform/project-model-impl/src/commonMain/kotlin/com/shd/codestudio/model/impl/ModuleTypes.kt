package com.shd.codestudio.model.impl

/** Compatibility aliases; both moved to `project-model-api` (see [com.shd.codestudio.model.ModuleTypeRegistry]). */
@Deprecated(
    "Moved to project-model-api",
    ReplaceWith("ModuleTypeRegistry", "com.shd.codestudio.model.ModuleTypeRegistry"),
)
typealias ModuleTypeRegistry = com.shd.codestudio.model.ModuleTypeRegistry

@Deprecated("Moved to project-model-api", ReplaceWith("UnknownModuleType", "com.shd.codestudio.model.UnknownModuleType"))
typealias UnknownModuleType = com.shd.codestudio.model.UnknownModuleType
