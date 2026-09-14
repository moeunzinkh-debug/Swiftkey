/*
 * ឧបករណ៍រោគវិនិច្ឆ័យបណ្តោះអាសន្ន — សាក InlineSmaliCompiler ជាមួយស្នាម smali ពិត
 * របស់ MicroG Account Permissions ដើម្បីរកមូលហេតុនៃ "Collection is empty."។
 * លទ្ធផលត្រូវបានបោះជា ::notice:: ដើម្បីឲ្យអានបានពី check-run annotations។
 */

package util

import app.morphe.patcher.util.smali.InlineSmaliCompiler

private const val TARGET =
    "Lapp/morphe/extension/swiftkey/GmsSupport;->ensureAccountPermissions(Landroid/app/Activity;)V"

fun main() {
    val jar = InlineSmaliCompiler::class.java.protectionDomain?.codeSource?.location
    println("::notice::patcher jar = $jar")

    val snippets = linkedMapOf(
        "p0" to "invoke-static {p0}, $TARGET",
        "v0" to "invoke-static {v0}, $TARGET",
        "p0+nl" to "invoke-static {p0}, $TARGET\n",
    )
    val paramSets = linkedMapOf(
        "none" to "",
        "Bundle" to "Landroid/os/Bundle;",
    )

    for ((label, snippet) in snippets) {
        for ((paramLabel, params) in paramSets) {
            for (static in listOf(false, true)) {
                val results = (1..6).map { registers ->
                    val outcome = try {
                        "${InlineSmaliCompiler.compile(snippet, params, registers, static).size}ins"
                    } catch (throwable: Throwable) {
                        throwable.javaClass.simpleName
                    }
                    "r$registers=$outcome"
                }
                println(
                    "::notice::smali[$label params=$paramLabel static=$static] " + results.joinToString(" "),
                )
            }
        }
    }
}
