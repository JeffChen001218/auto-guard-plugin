package com.auto.guard.plugin.entensions

import java.io.File

open class AutoGuardExtension {

    /*
     * 是否查找约束布局的constraint_referenced_ids属性的值，并添加到AndResGuard的白名单中，
     * 是的话，要求你在XmlClassGuard前依赖AabResGuard插件，默认false
     */
    var findAndConstraintReferencedIds = false

    /*
     * 是否查找约束布局的constraint_referenced_ids属性的值，并添加到AabResGuard的白名单中，
     * 是的话，要求你在XmlClassGuard前依赖AabResGuard插件，默认false
     */
    var findAabConstraintReferencedIds = false

    var mappingFile: File? = null

    var packageChange = HashMap<String, String>()

    var moveDir = HashMap<String, String>()
}