package uk.ac.ox.well.indiana.utils.traversal;

import org.jgrapht.DirectedGraph;
import uk.ac.ox.well.indiana.utils.io.cortex.graph.CortexRecord;

import java.util.Set;

/**
 * Created by kiran on 24/03/2017.
 */
public class NovelKmerAggregationStopper extends AbstractTraversalStopper<AnnotatedVertex, AnnotatedEdge> {
    @Override
    public boolean hasTraversalSucceeded(CortexRecord cr, DirectedGraph<AnnotatedVertex, AnnotatedEdge> g, int junctions, int size, int edges, Set<Integer> childColors, Set<Integer> parentColors) {
        return cr != null && (cr.getInDegree(0) == 0 || cr.getOutDegree(0) == 0);
    }

    @Override
    public boolean hasTraversalFailed(CortexRecord cr, DirectedGraph<AnnotatedVertex, AnnotatedEdge> g, int junctions, int size, int edges, Set<Integer> childColors, Set<Integer> parentColors) {
        return false;
    }

    @Override
    public int maxJunctionsAllowed() {
        return 0;
    }
}