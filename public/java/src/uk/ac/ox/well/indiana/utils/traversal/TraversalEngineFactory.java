package uk.ac.ox.well.indiana.utils.traversal;

import org.jgrapht.DirectedGraph;
import uk.ac.ox.well.indiana.utils.io.cortex.graph.CortexGraph;
import uk.ac.ox.well.indiana.utils.io.cortex.links.CortexLinks;
import uk.ac.ox.well.indiana.utils.io.cortex.links.CortexLinksMap;
import uk.ac.ox.well.indiana.utils.stoppingconditions.TraversalStopper;

import java.util.Arrays;
import java.util.Collection;

public class TraversalEngineFactory {
    private TraversalEngineConfiguration configuration = new TraversalEngineConfiguration();

    public TraversalEngineFactory configuration(TraversalEngineConfiguration configuration) { this.configuration = configuration; return this; }

    public TraversalEngineFactory combinationOperator(TraversalEngineConfiguration.GraphCombinationOperator op) { configuration.setGraphCombinationOperator(op); return this; }
    public TraversalEngineFactory traversalDirection(TraversalEngineConfiguration.TraversalDirection td) { configuration.setTraversalDirection(td); return this; }
    public TraversalEngineFactory connectAllNeighbors(boolean connectAllNeighbors) { configuration.setConnectAllNeighbors(connectAllNeighbors); return this; }

    public TraversalEngineFactory traversalColor(int color) { configuration.setTraversalColor(color); return this; }
    public TraversalEngineFactory traversalColor() { configuration.setTraversalColor(-1); return this; }

    public TraversalEngineFactory joiningColors(int... colors) { Arrays.stream(colors).forEach(c -> configuration.getJoiningColors().add(c)); return this; }
    public TraversalEngineFactory joiningColors(Collection<Integer> colors) { configuration.getJoiningColors().addAll(colors); return this; }
    public TraversalEngineFactory joiningColors() { configuration.getJoiningColors().clear(); return this; }

    public TraversalEngineFactory recruitmentColors(int... colors) { Arrays.stream(colors).forEach(c -> configuration.getRecruitmentColors().add(c)); return this; }
    public TraversalEngineFactory recruitmentColors(Collection<Integer> colors) { configuration.getRecruitmentColors().addAll(colors); return this; }
    public TraversalEngineFactory recruitmentColors() { configuration.getRecruitmentColors().clear(); return this; }

    public TraversalEngineFactory displayColors(int... colors) { Arrays.stream(colors).forEach(c -> configuration.getJoiningColors().add(c)); return this; }
    public TraversalEngineFactory displayColors(Collection<Integer> colors) { configuration.getJoiningColors().addAll(colors); return this; }
    public TraversalEngineFactory displayColors() { configuration.getJoiningColors().clear(); return this; }

    public TraversalEngineFactory previousTraversal(DirectedGraph<CortexVertex, CortexEdge> previousTraversal) { configuration.setPreviousTraversal(previousTraversal); return this; }

    public TraversalEngineFactory stopper(Class<? extends TraversalStopper<CortexVertex, CortexEdge>> stoppingRule) { configuration.setStoppingRule(stoppingRule); return this; }

    public TraversalEngineFactory graph(CortexGraph clean) { configuration.setGraph(clean); return this; }

    public TraversalEngineFactory rois(CortexGraph rois) { configuration.setRois(rois); return this; }

    public TraversalEngineFactory links(CortexLinksMap lm) {
        int linkColor = configuration.getGraph().getColorForSampleName(lm.getCortexLinks().getColor(0).getSampleName());

        configuration.getLinks().put(linkColor, lm);

        return this;
    }

    public TraversalEngine make() { return new TraversalEngine(configuration); }
}