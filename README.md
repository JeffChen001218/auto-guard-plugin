## auto-guard-plugin

Android自动混淆插件，基于[XmlClassGuard](https://github.com/liujingxing/XmlClassGuard)

### 更新内容

- 在不配置moveDir参数的情况下，新增自动生成混淆映射的能力（即默认全局混淆）
- 新增了一些配置参数<u>excludeClassList</u>(暂不可用)、randomFolderLevelRange、randomNameLengthRange
- 新增修改资源文件名称能力（适配layout DataBinding引用修改）
- 新增修改资源文件md5的能力。

### 完整Gradle配置示例（app模块下的`build.gradle.kts`文件）

```kotlin
import com.auto.guard.plugin.extensions.AutoGuardExtension

configure<AutoGuardExtension> {

    /**
     * （可选）
     * 自定义代码包路径映射表（暂不支持内部类）
     * 新增功能：
     *      1. 若配置为null，会自动生成映射表，并输出到mappingFile
     *      2. 会将此映射表应用到 资源xml中Class文件路径 的混淆规则
     *      example：hashMapOf(
     *          "path.of.clz1" to "a.b.c"
     *          "path.of.clz2" to "aa.bb.cc"
     *      )
     * 默认：null(Map<String,String>?)（自动全局扫描，自动生成映射）
     */
    moveDir = null

    /**
     * (可选)
     * 需要排除的类，即不参与混淆（优先级高于moveDir配置）
     *      支持 全路径类名 或 类文件名 或 包路径
     *      example:listOf("the.path.of.MyClz", "MyClz", "the.path.of")
     * 默认：emptyList()（不排除任何类，全都参与混淆）
     */
    excludes = emptyList<String>()

    /**
     * （可选）
     * 自动生成代码映射路径时，随机文件夹的层级范围
     * 默认：5 to 10
     */
    randomFolderLevelRange = 5 to 10

    /**
     * （可选）
     * 自动生成代码映射路径时，随机文件(夹)名的长度
     * 默认：5 to 10
     */
    randomNameLengthRange = 5 to 10

    /**
     * （可选）
     * 更换Manifest中的包名映射
     * 默认：null(Map<String, String>?)（不修改包名）
     */
    packageChange = null

    /**
     * （可选）
     * 混淆映射文件
     * 默认：null(File?)（在项目根目录下创建auto-guard-mapping.txt）
     */
    mappingFile = null

    /**
     * （可选）
     * 是否查找约束布局的constraint_referenced_ids属性的值，并添加到AndResGuard的白名单中，
     * 是的话，要求你在AutoGuard前依赖AabResGuard插件
     * 默认：false（不启用）
     */
    findAndConstraintReferencedIds = false

    /**
     * （可选）
     * 是否查找约束布局的constraint_referenced_ids属性的值，并添加到AabResGuard的白名单中，
     * 是的话，要求你在AutoGuard前依赖AabResGuard插件
     * 默认：false（不启用）
     */
    findAabConstraintReferencedIds = false
}
```

### TODO

- [ ] 自动插入混淆代码
- [ ] 修复excludeClassList参数不生效问题
