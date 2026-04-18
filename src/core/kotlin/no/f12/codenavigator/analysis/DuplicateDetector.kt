package no.f12.codenavigator.analysis

data class TokenizedFile(
    val file: String,
    val tokens: List<SourceToken>,
)

data class DuplicateLocation(
    val file: String,
    val startLine: Int,
    val endLine: Int,
)

data class DuplicateGroup(
    val tokenCount: Int,
    val locations: List<DuplicateLocation>,
)

object DuplicateDetector {

    fun detect(files: List<TokenizedFile>, minTokens: Int): List<DuplicateGroup> {
        data class TokenPos(val fileIndex: Int, val offset: Int)
        data class RawMatch(val a: TokenPos, val b: TokenPos, val length: Int)

        // Step 1: Build hash index
        val hashIndex = mutableMapOf<Long, MutableList<TokenPos>>()

        for ((fileIndex, file) in files.withIndex()) {
            val tokens = file.tokens
            if (tokens.size < minTokens) continue
            for (offset in 0..tokens.size - minTokens) {
                val hash = hashWindow(tokens, offset, minTokens)
                hashIndex.getOrPut(hash) { mutableListOf() }.add(TokenPos(fileIndex, offset))
            }
        }

        // Step 2: Find all raw matches at minTokens, extend, and deduplicate
        // Key insight: offset+1 will produce the same extended match minus 1 from start.
        // So we only keep the match that starts earliest.
        val matchMap = mutableMapOf<Pair<TokenPos, TokenPos>, Int>() // canonical pair -> length

        for ((_, positions) in hashIndex) {
            if (positions.size < 2) continue
            for (i in positions.indices) {
                for (j in i + 1 until positions.size) {
                    val a = positions[i]
                    val b = positions[j]

                    val tokensA = files[a.fileIndex].tokens
                    val tokensB = files[b.fileIndex].tokens

                    if (!tokensMatch(tokensA, a.offset, tokensB, b.offset, minTokens)) continue

                    // Canonical: normalize to earliest offset first
                    val (ca, cb) = if (a.fileIndex < b.fileIndex ||
                        (a.fileIndex == b.fileIndex && a.offset <= b.offset)) Pair(a, b) else Pair(b, a)

                    val key = Pair(ca, cb)

                    // Only keep the earliest start for this pair of files at these approximate positions
                    if (key in matchMap) continue

                    val matchLen = extendMatch(tokensA, ca.offset, tokensB, cb.offset)

                    // Skip overlapping within same file
                    if (ca.fileIndex == cb.fileIndex) {
                        val dist = cb.offset - ca.offset
                        if (dist < matchLen) continue
                    }

                    matchMap[key] = matchLen
                }
            }
        }

        // Step 3: Merge matches that represent the same extended duplicate.
        // Two matches are "the same" if one is a sub-range of the other.
        // Sort by length descending, keep only non-subsumed matches.
        val allMatches = matchMap.entries
            .map { (k, len) -> RawMatch(k.first, k.second, len) }
            .sortedByDescending { it.length }

        val result = mutableListOf<RawMatch>()
        val coveredRanges = mutableSetOf<Triple<Int, Int, Int>>() // (fileIndex, start, end)

        for (match in allMatches) {
            val rangeA = Triple(match.a.fileIndex, match.a.offset, match.a.offset + match.length)
            val rangeB = Triple(match.b.fileIndex, match.b.offset, match.b.offset + match.length)

            val aSubsumed = coveredRanges.any { it.first == rangeA.first && it.second <= rangeA.second && it.third >= rangeA.third }
            val bSubsumed = coveredRanges.any { it.first == rangeB.first && it.second <= rangeB.second && it.third >= rangeB.third }

            if (aSubsumed && bSubsumed) continue

            coveredRanges.add(rangeA)
            coveredRanges.add(rangeB)
            result.add(match)
        }

        return result.map { match ->
            val locA = locationOf(files[match.a.fileIndex], match.a.offset, match.length)
            val locB = locationOf(files[match.b.fileIndex], match.b.offset, match.length)
            DuplicateGroup(match.length, listOf(locA, locB))
        }.sortedByDescending { it.tokenCount }
    }

    private fun hashWindow(tokens: List<SourceToken>, offset: Int, length: Int): Long {
        var hash = 0L
        for (i in offset until offset + length) {
            hash = hash * 31L + tokenFingerprint(tokens[i])
        }
        return hash
    }

    private fun tokenFingerprint(token: SourceToken): Long =
        token.type.ordinal.toLong() * 1000003L + token.text.hashCode().toLong()

    private fun tokensMatch(
        a: List<SourceToken>, offsetA: Int,
        b: List<SourceToken>, offsetB: Int,
        length: Int,
    ): Boolean {
        for (i in 0 until length) {
            if (a[offsetA + i].type != b[offsetB + i].type || a[offsetA + i].text != b[offsetB + i].text) return false
        }
        return true
    }

    private fun extendMatch(
        a: List<SourceToken>, offsetA: Int,
        b: List<SourceToken>, offsetB: Int,
    ): Int {
        var len = 0
        while (offsetA + len < a.size && offsetB + len < b.size) {
            if (a[offsetA + len].type != b[offsetB + len].type || a[offsetA + len].text != b[offsetB + len].text) break
            len++
        }
        return len
    }

    private fun locationOf(file: TokenizedFile, offset: Int, length: Int): DuplicateLocation {
        val startLine = file.tokens[offset].line
        val endLine = file.tokens[offset + length - 1].line
        return DuplicateLocation(file.file, startLine, endLine)
    }
}
