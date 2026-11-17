package sle.editor.project.assets

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

internal enum class AssetKind { Directory, File, Link }
internal data class AssetEntry(val path: String,val kind: AssetKind,val size: Long=0,val modified: Long=0) {
    val name get()=path.substringAfterLast('/')
    val parent get()=path.substringBeforeLast('/',"")
}
internal data class AssetSnapshot(val entries: List<AssetEntry>,val errors: List<String> = emptyList(),val idsByPath: Map<String,String> = emptyMap()) {
    // The scanner constructs these indexes on its worker; UI publication is cheap.
    val byPath=entries.associateBy { it.path }
    val pathsById=idsByPath.entries.associate { it.value to it.key }
    val directories=entries.filter { it.kind==AssetKind.Directory }
    val folders=directories.map { it.path }.toSet()+""
    val children=entries.groupBy { it.parent }
    val folderChildren=directories.groupBy { it.parent }
}

internal object AssetScanner {
    fun scan(projectRoot: Path,assetRoot: Path,cancelled: ()->Boolean={false}): AssetSnapshot = synchronized(AssetIdentity) {
        val entries=mutableListOf<AssetEntry>();val errors=mutableListOf<String>()
        val identities=mutableMapOf<String,String>();val metadata=mutableListOf<Path>()
        try {
            val projectPath=projectRoot.toAbsolutePath().normalize()
            val project=projectPath.toRealPath()
            val input=assetRoot.toAbsolutePath().normalize()
            require(input.startsWith(projectPath) && input!=projectPath) { "Asset root must be inside the project" }
            require(!Files.isSymbolicLink(input) && Files.isDirectory(input)) { "Asset folder is missing or is a link" }
            val root=input.toRealPath()
            require(root.startsWith(project) && root==project.resolve(projectPath.relativize(input))) { "Asset root points through a link" }
            fun relative(path: Path)=root.relativize(path).toString().replace('\\','/')
            val pending=pendingAssetRecoveries(project)
            val visited=mutableSetOf<Path>()
            Files.walkFileTree(root,object: SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path,attrs: BasicFileAttributes): FileVisitResult {
                    if(cancelled()) return FileVisitResult.TERMINATE
                    val real=dir.toRealPath()
                    if(!real.startsWith(root) || !visited.add(real) || real!=dir.toAbsolutePath().normalize()) {
                        if(dir==root) errors.add("Asset root is a link; links are not traversed")
                        else entries.add(AssetEntry(relative(dir),AssetKind.Link,modified=attrs.lastModifiedTime().toMillis()))
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    if(dir!=root) entries.add(AssetEntry(relative(dir),AssetKind.Directory,modified=attrs.lastModifiedTime().toMillis()))
                    return FileVisitResult.CONTINUE
                }
                override fun visitFile(file: Path,attrs: BasicFileAttributes): FileVisitResult {
                    if(cancelled()) return FileVisitResult.TERMINATE
                    if(AssetIdentity.isMetadata(file)) { metadata.add(file);return FileVisitResult.CONTINUE }
                    if(attrs.isRegularFile && relative(file) in pending) errors.add("Pending asset recovery: ${relative(file)}. Use Recover Deleted before repairing identity.")
                    else if(attrs.isRegularFile) try { identities[relative(file)]=AssetIdentity.ensure(file) }
                    catch(failure: Exception) { errors.add("Metadata ${relative(file)}: ${failure.message}") }
                    entries.add(AssetEntry(relative(file),if(attrs.isSymbolicLink || attrs.isOther) AssetKind.Link else AssetKind.File,attrs.size(),attrs.lastModifiedTime().toMillis()))
                    return FileVisitResult.CONTINUE
                }
                override fun visitFileFailed(file: Path,failure: IOException): FileVisitResult {
                    errors.add("Cannot read ${relative(file)}: ${failure.message}");return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path,failure: IOException?): FileVisitResult {
                    failure?.let { errors.add("Cannot finish reading ${relative(dir)}: ${it.message}") }
                    return if(cancelled()) FileVisitResult.TERMINATE else FileVisitResult.CONTINUE
                }
            })
            metadata.filter { !Files.exists(it.resolveSibling(it.fileName.toString().dropLast(8)),LinkOption.NOFOLLOW_LINKS) }
                .forEach { errors.add("Orphan metadata: ${relative(it)}") }
        } catch(failure: Exception) { errors.add(failure.message ?: "Cannot scan assets") }
        identities.entries.groupBy { it.value }.filterValues { it.size>1 }.forEach { (id,duplicates) ->
            errors.add("Duplicate asset UUID $id: ${duplicates.joinToString { it.key }}")
            duplicates.forEach { identities.remove(it.key) }
        }
        return AssetSnapshot(entries.sortedWith(compareBy<AssetEntry> { it.kind!=AssetKind.Directory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.path }),errors,identities.toMap())
    }
}
