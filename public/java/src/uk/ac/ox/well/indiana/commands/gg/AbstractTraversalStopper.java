package uk.ac.ox.well.indiana.commands.gg;

import org.jgrapht.DirectedGraph;
import uk.ac.ox.well.indiana.utils.io.cortex.graph.CortexRecord;

public abstract class AbstractTraversalStopper<V, E> implements TraversalStopper<V, E> {
    public boolean keepGoing(CortexRecord cr, DirectedGraph<V, E> g, int junctions, int size) {
        return !hasTraversalSucceeded(cr, g, junctions, size) && !hasTraversalFailed(cr, g, junctions, size);
    }
}
