package uk.ac.ox.well.cortexjdk.utils.traversal;

import org.jgrapht.graph.DirectedWeightedPseudograph;
import uk.ac.ox.well.cortexjdk.utils.alignment.reference.IndexedReference;
import uk.ac.ox.well.cortexjdk.utils.io.graph.ConnectivityAnnotations;
import uk.ac.ox.well.cortexjdk.utils.io.graph.DeBruijnGraph;
import uk.ac.ox.well.cortexjdk.utils.stoppingrules.TraversalStoppingRule;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by kiran on 05/05/2017.
 */
public class TraversalEngineConfiguration {
    public enum GraphCombinationOperator { AND, OR }
    public enum TraversalDirection { BOTH, FORWARD, REVERSE }

    private GraphCombinationOperator gco = GraphCombinationOperator.AND;
    private TraversalDirection td = TraversalDirection.BOTH;
    private boolean connectAllNeighbors = false;
    private boolean discardFailedBranches = true;
    private boolean markFailedBranches = false;

    private int traversalColor = -1;
    private Set<Integer> joiningColors = new TreeSet<>();
    private Set<Integer> recruitmentColors = new TreeSet<>();
    private Set<Integer> secondaryColors = new TreeSet<>();

    //private int maxLength = Integer.MAX_VALUE;
    private int maxLength = 75000;

    private Set<String> sinks = new HashSet<>();
    //private DirectedWeightedPseudograph<CortexVertex, CortexEdge> previousTraversal;

    private Class<? extends TraversalStoppingRule<CortexVertex, CortexEdge>> stoppingRule;

    private DeBruijnGraph graph;
    private DeBruijnGraph rois;
    private Set<ConnectivityAnnotations> links = new HashSet<>();
    private Set<IndexedReference> kls = new HashSet<>();

    public GraphCombinationOperator getGraphCombinationOperator() { return gco; }
    public void setGraphCombinationOperator(GraphCombinationOperator gco) { this.gco = gco; }

    public TraversalDirection getTraversalDirection() { return td; }
    public void setTraversalDirection(TraversalDirection td) { this.td = td; }

    public boolean connectAllNeighbors() { return connectAllNeighbors; }
    public void setConnectAllNeighbors(boolean connectAllNeighbors) { this.connectAllNeighbors = connectAllNeighbors; }

    public boolean discardFailedBranches() { return discardFailedBranches; }
    public void setDiscardFailedBranches(boolean discardFailedBranches) { this.discardFailedBranches = discardFailedBranches; }

    public boolean markFailedBranches() { return markFailedBranches; }
    public void setMarkFailedBranches(boolean markFailedBranches) { this.markFailedBranches = markFailedBranches; }

    public int getTraversalColor() { return traversalColor; }
    public void setTraversalColor(int traversalColor) { this.traversalColor = traversalColor; }
    public void setTraversalColor() { this.traversalColor = -1; }

    public Set<Integer> getJoiningColors() { return joiningColors; }
    public void setJoiningColors(Collection<Integer> joiningColors) { this.joiningColors = new TreeSet<>(joiningColors); }
    public void setJoiningColors() { this.joiningColors.clear(); }

    public Set<Integer> getRecruitmentColors() { return recruitmentColors; }
    public void setRecruitmentColors(Collection<Integer> recruitmentColors) { this.recruitmentColors = new TreeSet<>(recruitmentColors); }
    public void setRecruitmentColors(int recruitmentColor) { this.recruitmentColors = new TreeSet<>(); this.recruitmentColors.add(recruitmentColor); }
    public void setRecruitmentColors() { this.recruitmentColors.clear(); }

    public Set<Integer> getSecondaryColors() { return secondaryColors; }
    public void setSecondaryColors(Collection<Integer> secondaryColors) { this.secondaryColors = new TreeSet<>(secondaryColors); }
    public void setSecondaryColors() { this.secondaryColors.clear(); }

    public Set<String> getSink() { return sinks; }
    public void setSink(Set<String> sinks) { this.sinks = sinks; }

    //public DirectedWeightedPseudograph<CortexVertex, CortexEdge> getSink() { return previousTraversal; }
    //public void setSink(DirectedWeightedPseudograph<CortexVertex, CortexEdge> previousTraversal) { this.previousTraversal = previousTraversal; }

    public Class<? extends TraversalStoppingRule<CortexVertex, CortexEdge>> getStoppingRule() { return stoppingRule; }
    public void setStoppingRule(Class<? extends TraversalStoppingRule<CortexVertex, CortexEdge>> stoppingRule) { this.stoppingRule = stoppingRule; }

    public Set<ConnectivityAnnotations> getLinks() { return links; }
    public void setLinks(Set<ConnectivityAnnotations> links) { this.links = links; }

    public DeBruijnGraph getGraph() { return graph; }
    public void setGraph(DeBruijnGraph graph) { this.graph = graph; }

    public DeBruijnGraph getRois() { return rois; }
    public void setRois(DeBruijnGraph rois) { this.rois = rois; }

    public Set<IndexedReference> getReferences() { return kls; }
    public void setReferences(Set<IndexedReference> kls) { this.kls = kls; }

    public void setMaxWalkLength(int maxLength) { this.maxLength = maxLength; }
    public int getMaxBranchLength() { return maxLength; }
}
