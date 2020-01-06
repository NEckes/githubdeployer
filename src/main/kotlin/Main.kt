import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.core.isSuccessful
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.moshi.defaultMoshi
import com.github.kittinunf.fuel.moshi.responseObject
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.file.Files

object Main {
    private val repo by Config.string.notNull()
    private val org by Config.string.notNull()
    private val token by Config.string.notNull()
    private val tag by Config.string.notNull()
    private val title by Config.string.notNull()
    private val artifact by Config.path.notNull()
    private val desc by Config.string
    private val draft by Config.bool.or(false)
    private val prerelease by Config.bool.or(false)
    private val target_commitish by Config.string

    @JvmStatic
    fun main(args: Array<String>) {
        Config.load(args)
        defaultMoshi.add(KotlinJsonAdapterFactory())

        println("Creating release")
        val (_, resp, res) = "https://api.github.com/repos/$org/$repo/releases"
            .httpPost().authentication().bearer(token)
            .jsonBody(
                mutableMapOf(
                    "tag_name" to tag,
                    "name" to title,
                    "draft" to draft,
                    "prerelease" to prerelease
                ).apply {
                    desc?.let { put("body", it) }
                    target_commitish?.let { put("target_commitish", it) }
                }
            ).responseObject<CreationResponse>()
        if (!resp.isSuccessful) throw RuntimeException("Release Creation failed!\n" + resp.body().asString("text/*"))
        val uploadUrl = res.get().upload_url.substringBefore('{')

        println("Uploading artifacts")

        var lastPercent: Int = -1
        Files.list(artifact).filter { it.toFile().extension == "jar" }.forEach {
            println("Uploading $it")
            uploadUrl.httpPost(listOf("name" to it.fileName.toString())
            ).authentication().bearer(token)
                .appendHeader("Content-Type", "application/java-archive")
                .body(it.toFile()).requestProgress { readBytes, totalBytes ->
                    val percent = (readBytes.toDouble() / totalBytes * 100).toInt()
                    if (lastPercent != percent) {
                        println("Upload: $percent%")
                        lastPercent = percent
                    }
                }.response()
        }
    }
}

class CreationResponse(
    val upload_url: String
)

inline fun <reified T : Any> Request.jsonBody(body: T) =
    jsonBody(defaultMoshi.build().adapter<T>(T::class.java).toJson(body))