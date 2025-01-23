package com.xml.guard.utils

import com.auto.guard.plugin.utils.KtFileParser
import org.junit.Test
import java.io.File

/**
 * User: ljx
 * Date: 2023/8/11
 * Time: 16:46
 */
class KtFileParserTest {

    @Test
    fun getTopClassOrFunOrFieldNames() {
        KtFileParser(File("/src/main/test/com/xml/guard/utils/RVItemDecoration.txt"),"RVItemDecoration")
    }
}