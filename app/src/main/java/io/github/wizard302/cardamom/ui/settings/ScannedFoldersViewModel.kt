package io.github.wizard302.cardamom.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.wizard302.cardamom.data.media.LibraryRepository
import io.github.wizard302.cardamom.data.media.Track
import io.github.wizard302.cardamom.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether a folder (with its whole subtree) is in the library. */
enum class FolderInclusion { INCLUDED, EXCLUDED, PARTIAL }

/** One row in the browser: a folder that either holds music or leads to it. */
data class FolderNode(
    val path: String,
    /** Display name — may span several path segments when a chain was collapsed. */
    val name: String,
    val trackCount: Int,
    val hasSubfolders: Boolean,
    val inclusion: FolderInclusion,
)

data class ScannedFoldersUiState(
    val rootPath: String = "",
    val currentPath: String = "",
    val canGoUp: Boolean = false,
    val folders: List<FolderNode> = emptyList(),
)

/**
 * Backs the "Scanned folders" browser. Walks the folder tree derived from
 * MediaStore paths so the user can exclude any folder (with its subtree) from the
 * library. Long single-child chains (e.g. Android/media/com.x/…) are collapsed
 * into one row so stray audio is one tap away instead of six levels deep.
 * Exclusions are stored as absolute paths; a tri-state checkbox marks folders
 * whose subtree is only partially excluded.
 */
@HiltViewModel
class ScannedFoldersViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    // Navigation stack of collapsed folder paths we've descended into; empty = root.
    private val stack = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<ScannedFoldersUiState> =
        combine(library.allTracks, settings.excludedFolders, stack) { tracks, excluded, stack ->
            build(tracks, excluded, stack)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScannedFoldersUiState())

    fun openFolder(path: String) {
        stack.value = stack.value + path
    }

    fun goUp() {
        stack.value = stack.value.dropLast(1)
    }

    /** Flips a folder between fully included and fully excluded. */
    fun toggle(path: String) {
        viewModelScope.launch {
            val tree = FolderTree(library.allTracks.value)
            val excluded = settings.excludedFolders.first().toMutableSet()

            if (inclusionOf(path, excluded) == FolderInclusion.INCLUDED) {
                // Exclude: drop now-redundant descendant entries, add this folder.
                excluded.removeAll { it == path || it.startsWith("$path/") }
                excluded.add(path)
            } else {
                // Include this whole subtree again.
                excluded.removeAll { it == path || it.startsWith("$path/") }
                // If an ancestor is excluded, carve this branch out of it by
                // excluding the off-path siblings at every level down to here.
                val ancestor = excluded
                    .filter { path.startsWith("$it/") }
                    .maxByOrNull { it.length }
                if (ancestor != null) {
                    excluded.remove(ancestor)
                    var cursor: String = ancestor
                    for (segment in path.removePrefix("$ancestor/").split('/')) {
                        val onPath = "$cursor/$segment"
                        tree.childrenOf(cursor).filter { it != onPath }.forEach { excluded.add(it) }
                        cursor = onPath
                    }
                }
            }
            settings.setExcludedFolders(excluded)
        }
    }

    private fun build(
        tracks: List<Track>,
        excluded: Set<String>,
        stack: List<String>,
    ): ScannedFoldersUiState {
        if (tracks.isEmpty()) return ScannedFoldersUiState()
        val tree = FolderTree(tracks)
        val current = stack.lastOrNull()?.takeIf { it == tree.root || it.startsWith("${tree.root}/") }
            ?: tree.root
        val folders = tree.childrenOf(current)
            .map { tree.collapse(it) }
            .map { node ->
                FolderNode(
                    path = node,
                    name = node.removePrefix("$current/"),
                    trackCount = tree.subtreeCount(node),
                    hasSubfolders = tree.childrenOf(node).isNotEmpty(),
                    inclusion = inclusionOf(node, excluded),
                )
            }
            .sortedBy { it.name.lowercase() }
        return ScannedFoldersUiState(
            rootPath = tree.root,
            currentPath = current,
            canGoUp = stack.isNotEmpty(),
            folders = folders,
        )
    }

    private fun inclusionOf(path: String, excluded: Set<String>): FolderInclusion = when {
        excluded.any { path == it || path.startsWith("$it/") } -> FolderInclusion.EXCLUDED
        excluded.any { it.startsWith("$path/") } -> FolderInclusion.PARTIAL
        else -> FolderInclusion.INCLUDED
    }
}

/**
 * Folder structure derived from a track list: the folders that directly hold music
 * plus their ancestors, with helpers to walk and collapse pass-through chains.
 */
private class FolderTree(tracks: List<Track>) {
    private val musicDirs: List<String> = tracks.map { it.path.substringBeforeLast('/', "") }
    val root: String = commonRoot(musicDirs)

    private val directCounts: Map<String, Int> = musicDirs.groupingBy { it }.eachCount()
    private val children: Map<String, List<String>> = buildChildren()

    fun childrenOf(node: String): List<String> = children[node] ?: emptyList()

    fun subtreeCount(node: String): Int =
        directCounts.entries.sumOf { (dir, n) -> if (dir == node || dir.startsWith("$node/")) n else 0 }

    /** Follows a single-child, file-less chain down to its first branch or file. */
    fun collapse(node: String): String {
        var n = node
        while ((directCounts[n] ?: 0) == 0) {
            val kids = childrenOf(n)
            if (kids.size != 1) break
            n = kids.first()
        }
        return n
    }

    private fun buildChildren(): Map<String, List<String>> {
        val all = HashSet<String>()
        for (dir in musicDirs) {
            if (dir != root && !dir.startsWith("$root/")) continue
            var p = dir
            while (p != root && p.length > root.length) {
                all.add(p)
                p = p.substringBeforeLast('/')
            }
        }
        return all.groupBy { it.substringBeforeLast('/') }
    }

    private fun commonRoot(dirs: List<String>): String {
        if (dirs.isEmpty()) return "/"
        var prefix = dirs.first().split('/')
        for (dir in dirs) {
            val parts = dir.split('/')
            var i = 0
            while (i < minOf(prefix.size, parts.size) && prefix[i] == parts[i]) i++
            prefix = prefix.subList(0, i)
        }
        return prefix.joinToString("/").ifEmpty { "/" }
    }
}
