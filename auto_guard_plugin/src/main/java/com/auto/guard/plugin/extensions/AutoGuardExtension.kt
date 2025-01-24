package com.auto.guard.plugin.extensions

import java.io.File

open class AutoGuardExtension {

    /**
     * （可选）
     * 自定义代码包路径映射表
     * 新增功能：
     *      1. 若配置为null，会自动生成映射表，并输出到mappingFile
     *      2. 会将此映射表应用到 资源xml中Class文件路径 的混淆规则
     *      example：hashMapOf(
     *          "path.of.clz1" to "a.b.c"
     *          "path.of.clz2" to "aa.bb.cc"
     *      )
     * 默认：null(Map<String,String>?)（自动全局扫描，自动生成映射）
     */
    var moveDir: Map<String, String>? = null

    /**
     * (可选)
     * 需要排除的类，即不参与混淆（优先级高于moveDir配置）
     *      支持 全路径类名 或 类文件名 或 包路径
     *      example:listOf("the.path.of.MyClz", "MyClz", "the.path.of")
     * 默认：emptyList()（不排除任何类，全都参与混淆）
     */
    var excludes: List<String> = emptyList()

    /**
     * （可选）
     * 自动生成代码映射路径时，随机文件夹的层级范围
     * 默认：5 to 10
     */
    var randomFolderLevelRange = 5 to 10

    /**
     * （可选）
     * 自动生成代码映射路径时，随机文件(夹)名的长度
     * 默认：5 to 10
     */
    var randomNameLengthRange = 5 to 10

    /**
     * （可选）
     * 更换Manifest中的包名映射
     * 默认：null(Map<String, String>?)（不修改包名）
     */
    var packageChange: Map<String, String>? = null

    /**
     * （可选）
     * 混淆映射文件
     * 默认：null(File?)（在项目根目录下创建auto-guard-mapping.txt）
     */
    var mappingFile: File? = null

    /**
     * （可选）
     * 是否查找约束布局的constraint_referenced_ids属性的值，并添加到AndResGuard的白名单中，
     * 是的话，要求你在AutoGuard前依赖AabResGuard插件
     * 默认：false（不启用）
     */
    var findAndConstraintReferencedIds = false

    /**
     * （可选）
     * 是否查找约束布局的constraint_referenced_ids属性的值，并添加到AabResGuard的白名单中，
     * 是的话，要求你在AutoGuard前依赖AabResGuard插件
     * 默认：false（不启用）
     */
    var findAabConstraintReferencedIds = false

// ========================= internal fields ==============================

}