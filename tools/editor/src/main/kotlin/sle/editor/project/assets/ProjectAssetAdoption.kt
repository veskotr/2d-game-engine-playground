package sle.editor.project.assets

import sle.editor.files.PrefabPackages
import java.nio.file.*
import java.util.UUID

/** Copies a file that lives outside the project (for example a texture extracted from a
 * .sleprefab/.slescene package into a temporary folder) into the asset tree, so project
 * documents can reference it by UUID. Identical content already present under the chosen
 * name is reused, so saving the same scene twice never duplicates files. */
internal class ProjectAssetAdoption(private val project: Path,private val assets: Path) {
    private val root get()=assetPath(project,assets)

    fun outside(path: Path): Boolean = runCatching {
        val absolute=path.toAbsolutePath().normalize()
        Files.isRegularFile(absolute) && !absolute.toRealPath().startsWith(root)
    }.getOrDefault(false)

    /** Returns the project path that now holds [source]'s content. */
    fun adopt(source: Path,displayName: String?=null): Path = synchronized(AssetIdentity) {
        val real=source.toAbsolutePath().normalize().toRealPath()
        require(!real.startsWith(root)) { "Asset is already inside the project: $source" }
        require(!AssetIdentity.isMetadata(real)) { "Cannot adopt asset metadata: $source" }
        val bytes=PrefabPackages.readBytes(real)
        val name=safeName(displayName?.takeIf { it.isNotBlank() } ?: real.fileName.toString())
        val folder=folderFor(name).takeIf { Files.isDirectory(root.resolve(it),LinkOption.NOFOLLOW_LINKS) } ?: ""
        val directory=assetPath(project,assets,folder)
        val dot=name.lastIndexOf('.').takeIf { it>0 } ?: name.length
        val stem=name.substring(0,dot);val extension=name.substring(dot)
        var index=0
        while(true) {
            val candidate=directory.resolve(if(index==0) name else "$stem ($index)$extension")
            index++
            if(Files.exists(candidate,LinkOption.NOFOLLOW_LINKS)) {
                if(Files.isRegularFile(candidate,LinkOption.NOFOLLOW_LINKS) && Files.size(candidate)==bytes.size.toLong() &&
                    Files.readAllBytes(candidate).contentEquals(bytes)) { AssetIdentity.ensure(candidate);return candidate }
                continue
            }
            if(Files.exists(AssetIdentity.sidecar(candidate),LinkOption.NOFOLLOW_LINKS)) continue
            atomicAssetWrite(AssetIdentity.sidecar(candidate),AssetIdentity.encode(UUID.randomUUID().toString()).toByteArray(),false)
            try { atomicAssetWrite(candidate,bytes,false) }
            catch(failure: Exception) { Files.deleteIfExists(AssetIdentity.sidecar(candidate));throw failure }
            return candidate
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    companion object {
        private val images=setOf("png","jpg","jpeg","bmp","tga","gif","psd","hdr","pic","pnm","ppm","pgm")
        private val audio=setOf("wav","ogg","mp3","flac")
        fun folderFor(name: String): String = when(name.substringAfterLast('.',"").lowercase()) {
            in images -> "Textures"
            in audio -> "Audio"
            "lua" -> "Scripts"
            "xml" -> "UI"
            else -> if(name.endsWith(".json",true)) "Data" else ""
        }
        fun safeName(name: String): String {
            val cleaned=name.map { if(it.code<32 || it in "<>:\"/\\|?*") '_' else it }.joinToString("").trim().trimEnd('.')
            return if(cleaned.isBlank() || AssetIdentity.isMetadata(Path.of(cleaned))) "asset" else cleaned
        }
    }
}
