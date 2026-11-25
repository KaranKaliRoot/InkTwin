package com.app.inktwin.core.glyph

/**
 * Normalizes drawn glyph strokes to a standard 1000x1000 em-square coordinate system.
 * Preserves relative vertical positioning for proper baseline alignment.
 */
object GlyphNormalizer {

    data class NormalizationResult(
        val normalizedCommands: List<PathCommand>,
        val advanceWidth: Float,
        val leftBearing: Float,
        val baselineY: Float = 750f
    )

    /**
     * Normalizes a list of path commands to fit within a 1000x1000 em-square.
     * Trims horizontal bounds (minX to maxX) while keeping vertical coordinates
     * aligned relative to the pad height (0..1000 em-square) so baseline alignment is preserved.
     *
     * @param commands The original path commands from user drawing
     * @param drawingHeight The height of the drawing area (for baseline calculation, default 1000f)
     * @param baselineY The Y coordinate of the baseline within the drawing area (default 750f)
     */
    fun normalize(
        commands: List<PathCommand>,
        drawingHeight: Float = 1000f,
        baselineY: Float = 750f
    ): NormalizationResult {
        if (commands.isEmpty()) {
            return NormalizationResult(emptyList(), 0f, 0f, baselineY)
        }

        // Collect all points to compute bounding box
        val allPoints = mutableListOf<Pair<Float, Float>>()
        for (cmd in commands) {
            for (i in cmd.points.indices step 2) {
                if (i + 1 < cmd.points.size) {
                    allPoints.add(cmd.points[i] to cmd.points[i + 1])
                }
            }
        }

        if (allPoints.isEmpty()) {
            return NormalizationResult(emptyList(), 0f, 0f, baselineY)
        }

        // Compute horizontal bounds
        val minX = allPoints.minOf { it.first }
        val maxX = allPoints.maxOf { it.first }
        val maxY = allPoints.maxOf { it.second }

        val effHeight = if (drawingHeight > 0f) drawingHeight else maxOf(1000f, maxY)
        val scale = 1000f / effHeight

        val leftBearing = 50f
        val charWidth = (maxX - minX) * scale
        val advanceWidth = charWidth + (leftBearing * 2)
        val normalizedBaselineY = baselineY * scale

        // Transform all commands: trim X, scale Y relative to pad height
        val normalizedCommands = commands.map { cmd ->
            val normalizedPoints = cmd.points.mapIndexed { index, value ->
                if (index % 2 == 0) {
                    // X coordinate: trim horizontal bounds and add left bearing
                    (value - minX) * scale + leftBearing
                } else {
                    // Y coordinate: preserve vertical position relative to pad height (0..1000)
                    value * scale
                }
            }
            cmd.copy(points = normalizedPoints)
        }

        return NormalizationResult(
            normalizedCommands = normalizedCommands,
            advanceWidth = advanceWidth,
            leftBearing = leftBearing,
            baselineY = normalizedBaselineY
        )
    }

    /**
     * Smooths a sequence of points using quadratic Bézier curves.
     * Converts raw touch points into smooth path commands.
     */
    fun smoothPath(rawPoints: List<Pair<Float, Float>>): List<PathCommand> {
        if (rawPoints.isEmpty()) return emptyList()
        if (rawPoints.size == 1) {
            return listOf(PathCommand(type = "moveTo", points = listOf(rawPoints[0].first, rawPoints[0].second)))
        }

        val commands = mutableListOf<PathCommand>()

        // Start at first point
        commands.add(PathCommand(
            type = "moveTo",
            points = listOf(rawPoints[0].first, rawPoints[0].second)
        ))

        if (rawPoints.size == 2) {
            // Just a line for two points
            commands.add(PathCommand(
                type = "lineTo",
                points = listOf(rawPoints[1].first, rawPoints[1].second)
            ))
            return commands
        }

        // For 3+ points, use quadratic curves
        var i = 1
        while (i < rawPoints.size - 1) {
            val current = rawPoints[i]
            val next = rawPoints[i + 1]

            // Control point is current, end point is midpoint to next for smoother curves
            val midX = (current.first + next.first) / 2f
            val midY = (current.second + next.second) / 2f

            commands.add(PathCommand(
                type = "quadTo",
                points = listOf(current.first, current.second, midX, midY)
            ))

            i += 1
        }

        // Finish with lineTo the final point
        val last = rawPoints.last()
        commands.add(PathCommand(
            type = "lineTo",
            points = listOf(last.first, last.second)
        ))

        return commands
    }
}
