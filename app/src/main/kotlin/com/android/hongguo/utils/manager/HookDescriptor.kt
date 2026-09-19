package com.android.hongguo.utils.manager

/**
 * JVM 方法描述符解析器
 * "Lp06/k0;->onCreate(Landroid/os/Bundle;)V"
 *  -> className = "p06.k0", methodName = "onCreate",
 *     paramDescriptors = ["Landroid/os/Bundle;"], returnDescriptor = "V"
 */
object HookDescriptor {

    data class Target(
        val className: String,
        val methodName: String,
        val paramDescriptors: List<String>,
        val returnDescriptor: String        // ★ 新增：返回类型描述符，如 "Lcom/ss/ttvideoengine/Resolution;"
    )

    fun parse(descriptor: String?): Target? {
        if (descriptor.isNullOrEmpty() || !descriptor.contains("->")) return null
        return runCatching {
            val classPart = descriptor.substringBefore("->")            // Lp06/k0;
            val rest = descriptor.substringAfter("->")                  // onCreate(...)V
            val methodName = rest.substringBefore("(")                  // onCreate
            val afterParen = rest.substringAfter("(", "").substringBefore(")") // 参数部分
            val returnDescriptor = rest.substringAfter(")", "").trim()  // ★ 返回类型

            val className = classPart
                .removePrefix("L")
                .removeSuffix(";")
                .replace('/', '.')

            Target(className, methodName, splitParams(afterParen), returnDescriptor)
        }.getOrNull()
    }

    /** "Landroid/os/Bundle;ILjava/lang/String;" -> ["Landroid/os/Bundle;","I","Ljava/lang/String;"] */
    private fun splitParams(s: String): List<String> {
        if (s.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when (c) {
                'L' -> {
                    val end = s.indexOf(';', i)
                    if (end < 0) { out.add(s.substring(i)); break }
                    out.add(s.substring(i, end + 1))
                    i = end + 1
                }
                '[' -> {
                    val end = s.indexOf(';', i)
                    if (end < 0) { out.add(s.substring(i)); break }
                    out.add(s.substring(i, end + 1))
                    i = end + 1
                }
                else -> { out.add(c.toString()); i++ }
            }
        }
        return out
    }

    /** 描述符 -> Class，基本类型用 javaPrimitiveType */
    fun descriptorToClass(desc: String, classLoader: ClassLoader): Class<*>? {
        return when (desc) {
            "I" -> Int::class.javaPrimitiveType
            "Z" -> Boolean::class.javaPrimitiveType
            "J" -> Long::class.javaPrimitiveType
            "F" -> Float::class.javaPrimitiveType
            "D" -> Double::class.javaPrimitiveType
            "B" -> Byte::class.javaPrimitiveType
            "C" -> Char::class.javaPrimitiveType
            "S" -> Short::class.javaPrimitiveType
            "V" -> Void.TYPE
            else -> {
                if (!desc.startsWith("L")) return null
                val name = desc.removePrefix("L").removeSuffix(";").replace('/', '.')
                runCatching { classLoader.loadClass(name) }.getOrNull()
            }
        }
    }

    /** 描述符 -> 类名（"Lcom/ss/ttvideoengine/Resolution;" -> "com.ss.ttvideoengine.Resolution"），非 L 返回 null */
    fun descriptorToClassName(desc: String): String? {
        if (!desc.startsWith("L") || !desc.endsWith(";")) return null
        val internal = desc.removePrefix("L").removeSuffix(";")
        if (internal.isEmpty()) return null
        return internal.replace('/', '.')
    }
}