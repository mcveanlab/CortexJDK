package uk.ac.ox.well.indiana.commands.gg;

import com.google.common.base.Joiner;
import htsjdk.samtools.util.Interval;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import org.jgrapht.DirectedGraph;
import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import uk.ac.ox.well.indiana.commands.Module;
import uk.ac.ox.well.indiana.utils.alignment.kmer.KmerLookup;
import uk.ac.ox.well.indiana.utils.arguments.Argument;
import uk.ac.ox.well.indiana.utils.arguments.Output;
import uk.ac.ox.well.indiana.utils.containers.ContainerUtils;
import uk.ac.ox.well.indiana.utils.exceptions.IndianaException;
import uk.ac.ox.well.indiana.utils.io.cortex.graph.CortexGraph;
import uk.ac.ox.well.indiana.utils.io.cortex.graph.CortexKmer;
import uk.ac.ox.well.indiana.utils.io.cortex.graph.CortexRecord;
import uk.ac.ox.well.indiana.utils.io.table.TableReader;
import uk.ac.ox.well.indiana.utils.io.table.TableWriter;
import uk.ac.ox.well.indiana.utils.sequence.CortexUtils;
import uk.ac.ox.well.indiana.utils.sequence.SequenceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class GenotypeGraph extends Module {
    @Argument(fullName="graph", shortName="g", doc="Graph")
    public CortexGraph GRAPH;

    @Argument(fullName="graphRaw", shortName="r", doc="Graph (raw)", required=false)
    public CortexGraph GRAPH_RAW;

    @Argument(fullName="novelGraph", shortName="n", doc="Graph of novel kmers")
    public CortexGraph NOVEL;

    @Argument(fullName="ref1", shortName="r1", doc="Fasta file for first parent")
    public File REF1;

    @Argument(fullName="ref2", shortName="r2", doc="Fasta file for second parent")
    public File REF2;

    @Argument(fullName="bed", shortName="b", doc="Bed file describing variants", required=false)
    public File BED;

    @Argument(fullName="novelKmerMap", shortName="m", doc="Novel kmer map", required=false)
    public File NOVEL_KMER_MAP;

    @Argument(fullName="beAggressive", shortName="a", doc="Be aggressive in extending novel stretches")
    public Boolean AGGRESSIVE = false;

    @Output(fullName="gout", shortName="go", doc="Graph out")
    public File gout;

    @Output
    public PrintStream out;

    @Output(fullName="evalOut", shortName="eout", doc="Eval out")
    public PrintStream eout;

    private class VariantInfo {
        public String variantId;

        public String vchr;
        public int vstart;
        public int vstop;

        public String type;
        public String denovo;
        public String nahr;
        public int gcindex;
        public String ref;
        public String alt;
        public String leftFlank;
        public String rightFlank;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            VariantInfo that = (VariantInfo) o;

            if (vstart != that.vstart) return false;
            if (vstop != that.vstop) return false;
            if (gcindex != that.gcindex) return false;
            if (variantId != null ? !variantId.equals(that.variantId) : that.variantId != null) return false;
            if (vchr != null ? !vchr.equals(that.vchr) : that.vchr != null) return false;
            if (type != null ? !type.equals(that.type) : that.type != null) return false;
            if (denovo != null ? !denovo.equals(that.denovo) : that.denovo != null) return false;
            if (nahr != null ? !nahr.equals(that.nahr) : that.nahr != null) return false;
            if (ref != null ? !ref.equals(that.ref) : that.ref != null) return false;
            if (alt != null ? !alt.equals(that.alt) : that.alt != null) return false;
            if (leftFlank != null ? !leftFlank.equals(that.leftFlank) : that.leftFlank != null) return false;
            return !(rightFlank != null ? !rightFlank.equals(that.rightFlank) : that.rightFlank != null);
        }

        @Override
        public int hashCode() {
            int result = variantId != null ? variantId.hashCode() : 0;
            result = 31 * result + (vchr != null ? vchr.hashCode() : 0);
            result = 31 * result + vstart;
            result = 31 * result + vstop;
            result = 31 * result + (type != null ? type.hashCode() : 0);
            result = 31 * result + (denovo != null ? denovo.hashCode() : 0);
            result = 31 * result + (nahr != null ? nahr.hashCode() : 0);
            result = 31 * result + gcindex;
            result = 31 * result + (ref != null ? ref.hashCode() : 0);
            result = 31 * result + (alt != null ? alt.hashCode() : 0);
            result = 31 * result + (leftFlank != null ? leftFlank.hashCode() : 0);
            result = 31 * result + (rightFlank != null ? rightFlank.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return "VariantInfo{" +
                    "variantId='" + variantId + '\'' +
                    ", vchr='" + vchr + '\'' +
                    ", vstart=" + vstart +
                    ", vstop=" + vstop +
                    ", type='" + type + '\'' +
                    ", denovo='" + denovo + '\'' +
                    ", nahr='" + nahr + '\'' +
                    ", gcindex=" + gcindex +
                    ", ref='" + ref + '\'' +
                    ", alt='" + alt + '\'' +
                    ", leftFlank='" + leftFlank + '\'' +
                    ", rightFlank='" + rightFlank + '\'' +
                    '}';
        }
    }

    private Map<String, VariantInfo> loadVariantInfoMap() {
        Map<String, VariantInfo> allVariants = new HashMap<String, VariantInfo>();

        if (BED != null) {
            TableReader bed = new TableReader(BED, "vchr", "vstart", "vstop", "info");

            for (Map<String, String> be : bed) {
                VariantInfo vi = new VariantInfo();

                vi.vchr = be.get("vchr");
                vi.vstart = Integer.valueOf(be.get("vstart"));
                vi.vstop = Integer.valueOf(be.get("vstop"));

                String[] kvpairs = be.get("info").split(";");
                for (String kvpair : kvpairs) {
                    String[] kv = kvpair.split("=");

                    if (kv[0].equals("id")) { vi.variantId = kv[1]; }
                    if (kv[0].equals("type")) { vi.type = kv[1]; }
                    if (kv[0].equals("denovo")) { vi.denovo = kv[1]; }
                    if (kv[0].equals("nahr")) { vi.nahr = kv[1]; }
                    if (kv[0].equals("gcindex")) { vi.gcindex = Integer.valueOf(kv[1]); }
                    if (kv[0].equals("ref")) { vi.ref = kv[1]; }
                    if (kv[0].equals("alt")) { vi.alt = kv[1]; }
                    if (kv[0].equals("left")) { vi.leftFlank = kv[1]; }
                    if (kv[0].equals("right")) { vi.rightFlank = kv[1]; }
                }

                allVariants.put(vi.variantId, vi);
            }
        }
        return allVariants;
    }

    private Map<CortexKmer, VariantInfo> loadNovelKmerMap() {
        Map<String, VariantInfo> allVariants = loadVariantInfoMap();

        Map<CortexKmer, VariantInfo> vis = new HashMap<CortexKmer, VariantInfo>();

        if (NOVEL_KMER_MAP != null) {
            TableReader tr = new TableReader(NOVEL_KMER_MAP);

            for (Map<String, String> te : tr) {
                VariantInfo vi = allVariants.get(te.get("variantId"));
                CortexKmer kmer = new CortexKmer(te.get("kmer"));

                vis.put(kmer, vi);
            }
        }

        return vis;
    }

    private String formatAttributes(Map<String, Object> attrs) {
        List<String> attrArray = new ArrayList<String>();

        for (String attr : attrs.keySet()) {
            String value = attrs.get(attr).toString();

            attrArray.add(attr + "=\"" + value + "\"");
        }

        return Joiner.on(" ").join(attrArray);
    }

    public void printGraph(DirectedGraph<AnnotatedVertex, AnnotatedEdge> g, String prefix, boolean withText, boolean withPdf) {
        try {
            File f = new File(gout.getAbsolutePath() + "." + prefix + ".dot");
            File p = new File(gout.getAbsolutePath() + "." + prefix + ".pdf");

            PrintStream o = new PrintStream(f);

            String indent = "  ";

            o.println("digraph G {");

            if (withText) {
                o.println(indent + "node [ shape=box fontname=\"Courier New\" style=filled fillcolor=white ];");
            } else {
                o.println(indent + "node [ shape=point style=filled fillcolor=white ];");
            }

            for (AnnotatedVertex v : g.vertexSet()) {
                Map<String, Object> attrs = new TreeMap<String, Object>();

                if (!withText || g.inDegreeOf(v) == 0 || g.outDegreeOf(v) == 0) {
                    attrs.put("label", "");
                }

                if (v.isNovel()) {
                    attrs.put("color", "red");
                    attrs.put("fillcolor", "red");
                    attrs.put("shape", "circle");
                }

                if (v.flagIsSet("start") || v.flagIsSet("end")) {
                    attrs.put("label", v.getKmer());
                    attrs.put("fillcolor", v.flagIsSet("start") ? "orange" : "purple");
                    attrs.put("shape", "rect");
                }

                o.println(indent + "\"" + v.getKmer() + "\" [ " + formatAttributes(attrs) + " ];");
            }

            String[] colors = new String[] { "red", "blue", "green" };

            for (AnnotatedEdge e : g.edgeSet()) {
                String s = g.getEdgeSource(e).getKmer();
                String t = g.getEdgeTarget(e).getKmer();

                for (int c = 0; c < 3; c++) {
                    if (e.isPresent(c)) {
                        Map<String, Object> attrs = new TreeMap<String, Object>();
                        attrs.put("color", colors[c]);

                        o.println(indent + "\"" + s + "\" -> \"" + t + "\" [ " + formatAttributes(attrs) + " ];");
                    }
                }
            }

            o.println("}");

            o.close();

            if (withPdf) {
                //log.info("dot -Tpdf -o" + p.getAbsolutePath() + " " + f.getAbsolutePath());
                Runtime.getRuntime().exec("dot -Tpdf -o" + p.getAbsolutePath() + " " + f.getAbsolutePath());
            }
        } catch (FileNotFoundException e) {
            throw new IndianaException("File not found", e);
        } catch (IOException e) {
            throw new IndianaException("IO exception", e);
        }
    }

    private void addGraph(DirectedGraph<AnnotatedVertex, AnnotatedEdge> a, DirectedGraph<AnnotatedVertex, AnnotatedEdge> g, int color, Map<CortexKmer, Boolean> novelKmers) {
        for (AnnotatedVertex v : g.vertexSet()) {
            AnnotatedVertex av = new AnnotatedVertex(v.getKmer(), novelKmers.containsKey(new CortexKmer(v.getKmer())));

            a.addVertex(av);
        }

        for (AnnotatedEdge e : g.edgeSet()) {
            AnnotatedVertex s0 = g.getEdgeSource(e);
            AnnotatedVertex s1 = g.getEdgeTarget(e);

            CortexKmer ck0 = new CortexKmer(s0.getKmer());
            CortexKmer ck1 = new CortexKmer(s1.getKmer());

            AnnotatedVertex a0 = new AnnotatedVertex(s0.getKmer(), novelKmers.containsKey(ck0));
            AnnotatedVertex a1 = new AnnotatedVertex(s1.getKmer(), novelKmers.containsKey(ck1));

            if (!a.containsEdge(a0, a1)) {
                a.addEdge(a0, a1, new AnnotatedEdge());
            }

            a.getEdge(a0, a1).set(color, true);
        }
    }

    private DirectedGraph<AnnotatedVertex, AnnotatedEdge> copyGraph(DirectedGraph<AnnotatedVertex, AnnotatedEdge> a) {
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> b = new DefaultDirectedGraph<AnnotatedVertex, AnnotatedEdge>(AnnotatedEdge.class);

        for (AnnotatedVertex av : a.vertexSet()) {
            AnnotatedVertex newAv = new AnnotatedVertex(av.getKmer(), av.isNovel());

            newAv.setFlags(av.getFlags());

            b.addVertex(newAv);
        }

        for (AnnotatedEdge ae : a.edgeSet()) {
            AnnotatedVertex vs = a.getEdgeSource(ae);
            AnnotatedVertex vt = a.getEdgeTarget(ae);

            b.addEdge(vs, vt, new AnnotatedEdge(ae.getPresence()));
        }

        return b;
    }

    private DirectedGraph<AnnotatedVertex, AnnotatedEdge> simplifyGraph(DirectedGraph<AnnotatedVertex, AnnotatedEdge> a) {
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> b = copyGraph(a);

        AnnotatedVertex thisVertex;
        Set<AnnotatedVertex> usedStarts = new HashSet<AnnotatedVertex>();

        Set<AnnotatedVertex> candidateStarts = new HashSet<AnnotatedVertex>();
        Set<AnnotatedVertex> candidateEnds = new HashSet<AnnotatedVertex>();
        for (AnnotatedVertex av : b.vertexSet()) {
            if (av.flagIsSet("start")) {
                candidateStarts.add(av);
            } else if (av.flagIsSet("end")) {
                candidateEnds.add(av);
            }
        }

        do {
            thisVertex = null;

            Iterator<AnnotatedVertex> vertices = b.vertexSet().iterator();

            while (thisVertex == null && vertices.hasNext()) {
                AnnotatedVertex tv = vertices.next();

                if (!usedStarts.contains(tv)) {
                    while (b.inDegreeOf(tv) == 1) {
                        AnnotatedVertex pv = b.getEdgeSource(b.incomingEdgesOf(tv).iterator().next());

                        if (b.outDegreeOf(pv) == 1 && pv.isNovel() == tv.isNovel()) {
                            tv = pv;
                        } else {
                            break;
                        }
                    }

                    thisVertex = tv;
                }
            }

            if (thisVertex != null) {
                usedStarts.add(thisVertex);

                AnnotatedVertex nextVertex = b.outDegreeOf(thisVertex) == 0 ? null : b.getEdgeTarget(b.outgoingEdgesOf(thisVertex).iterator().next());

                while (nextVertex != null && b.outDegreeOf(thisVertex) == 1 && b.inDegreeOf(nextVertex) == 1 && thisVertex.isNovel() == nextVertex.isNovel()) {
                    AnnotatedVertex sv = new AnnotatedVertex(thisVertex.getKmer() + nextVertex.getKmer().charAt(nextVertex.getKmer().length() - 1), thisVertex.isNovel());

                    b.addVertex(sv);

                    Set<AnnotatedEdge> edgesToRemove = new HashSet<AnnotatedEdge>();

                    for (AnnotatedEdge e : b.incomingEdgesOf(thisVertex)) {
                        AnnotatedVertex pv = b.getEdgeSource(e);

                        b.addEdge(pv, sv, new AnnotatedEdge(e.getPresence()));

                        edgesToRemove.add(e);
                    }

                    for (AnnotatedEdge e : b.outgoingEdgesOf(nextVertex)) {
                        AnnotatedVertex nv = b.getEdgeTarget(e);

                        b.addEdge(sv, nv, new AnnotatedEdge(e.getPresence()));

                        edgesToRemove.add(e);
                    }

                    b.removeVertex(thisVertex);
                    b.removeVertex(nextVertex);
                    b.removeAllEdges(edgesToRemove);

                    thisVertex = sv;
                    nextVertex = b.outDegreeOf(thisVertex) == 0 ? null : b.getEdgeTarget(b.outgoingEdgesOf(thisVertex).iterator().next());
                }
            }
        } while (thisVertex != null);

        for (AnnotatedVertex av : b.vertexSet()) {
            for (AnnotatedVertex candidateStart : candidateStarts) {
                if (av.getKmer().contains(candidateStart.getKmer())) {
                    av.setFlag("start");
                }
            }

            for (AnnotatedVertex candidateEnd : candidateEnds) {
                if (av.getKmer().contains(candidateEnd.getKmer())) {
                    av.setFlag("end");
                }
            }
        }

        return b;
    }

    private DirectedGraph<AnnotatedVertex, AnnotatedEdge> removeOtherColors(DirectedGraph<AnnotatedVertex, AnnotatedEdge> a, int colorToRetain) {
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> b = copyGraph(a);

        Set<AnnotatedEdge> edgesToRemove = new HashSet<AnnotatedEdge>();

        for (AnnotatedEdge ae : b.edgeSet()) {
            for (int c = 0; c < 10; c++) {
                if (c != colorToRetain) {
                    ae.set(c, false);
                }
            }

            if (ae.isAbsent(colorToRetain)) {
                edgesToRemove.add(ae);
            }
        }

        b.removeAllEdges(edgesToRemove);

        Set<AnnotatedVertex> verticesToRemove = new HashSet<AnnotatedVertex>();
        for (AnnotatedVertex av : b.vertexSet()) {
            if (b.inDegreeOf(av) == 0 && b.outDegreeOf(av) == 0) {
                verticesToRemove.add(av);
            }
        }

        b.removeAllVertices(verticesToRemove);

        return b;
    }

    private String linearizePath(DirectedGraph<AnnotatedVertex, AnnotatedEdge> a, GraphPath<AnnotatedVertex, AnnotatedEdge> p) {
        StringBuilder sb = new StringBuilder(p.getStartVertex().getKmer());

        for (AnnotatedEdge ae : p.getEdgeList()) {
            AnnotatedVertex av = a.getEdgeTarget(ae);

            sb.append(av.getKmer().charAt(av.getKmer().length() - 1));
        }

        return sb.toString();
    }

    private List<String> getNovelStretch(String stretch, Map<CortexKmer, Boolean> novelKmers) {
        boolean[] novel = new boolean[stretch.length()];
        int kmerLength = novelKmers.keySet().iterator().next().length();

        for (int i = 0; i <= stretch.length() - kmerLength; i++) {
            String sk = stretch.substring(i, i + kmerLength);
            CortexKmer ck = new CortexKmer(sk);

            novel[i] = novelKmers.containsKey(ck);
        }

        List<String> novelStretches = new ArrayList<String>();

        StringBuilder novelStretchBuilder = new StringBuilder();

        for (int i = 0; i <= stretch.length() - kmerLength; i++) {
            String sk = stretch.substring(i, i + kmerLength);
            boolean isNovel = novel[i];

            if (!isNovel) {
                if (novelStretchBuilder.length() > 0) {
                    novelStretches.add(novelStretchBuilder.toString());
                    novelStretchBuilder = new StringBuilder();
                }
            } else {
                if (novelStretchBuilder.length() == 0) {
                    novelStretchBuilder.append(sk);
                } else {
                    novelStretchBuilder.append(sk.charAt(sk.length() - 1));
                }
            }
        }

        if (novelStretchBuilder.length() > 0) {
            novelStretches.add(novelStretchBuilder.toString());
        }

        return novelStretches;
    }

    private class PathInfo {
        public String start, stop, child, parent;

        public PathInfo(String start, String stop, String child, String parent) {
            this.start = start;
            this.stop = stop;
            this.child = child;
            this.parent = parent;
        }
    }

    private PathInfo computeBestMinWeightPath(DirectedGraph<AnnotatedVertex, AnnotatedEdge> a, int color, String stretch, Map<CortexKmer, Boolean> novelKmers) {
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> b = copyGraph(a);

        AnnotateStartsAndEnds annotateStartsAndEnds = new AnnotateStartsAndEnds(color, stretch, novelKmers, b).invoke();
        Set<AnnotatedVertex> candidateStarts = annotateStartsAndEnds.getCandidateStarts();
        Set<AnnotatedVertex> candidateEnds = annotateStartsAndEnds.getCandidateEnds();

        DirectedGraph<AnnotatedVertex, AnnotatedEdge> b0 = removeOtherColors(b, 0);
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> bc = removeOtherColors(b, color);

        List<String> novelStretches = getNovelStretch(stretch, novelKmers);

        double minPl0 = Double.MAX_VALUE, minPlc = Double.MAX_VALUE;
        String minLp0 = "", minLpc = "";
        String start = "", stop = "";

        for (AnnotatedVertex sv : candidateStarts) {
            for (AnnotatedVertex ev : candidateEnds) {
                DijkstraShortestPath<AnnotatedVertex, AnnotatedEdge> dsp0 = new DijkstraShortestPath<AnnotatedVertex, AnnotatedEdge>(b0, sv, ev);
                DijkstraShortestPath<AnnotatedVertex, AnnotatedEdge> dspc = new DijkstraShortestPath<AnnotatedVertex, AnnotatedEdge>(bc, sv, ev);

                GraphPath<AnnotatedVertex, AnnotatedEdge> p0 = dsp0.getPath();
                GraphPath<AnnotatedVertex, AnnotatedEdge> pc = dspc.getPath();

                String lp0 = p0 == null ? "" : linearizePath(b0, p0);
                String lpc = pc == null ? "" : linearizePath(bc, pc);

                if (lp0.contains(sv.getKmer()) && lp0.contains(ev.getKmer()) && lpc.contains(sv.getKmer()) && lpc.contains(ev.getKmer())) {
                    boolean stretchesArePresent = true;
                    for (String novelStretch : novelStretches) {
                        if (!lp0.contains(novelStretch)) {
                            stretchesArePresent = false;
                            break;
                        }
                    }

                    if (novelStretches.size() > 0 && stretchesArePresent) {
                        double pl0 = dsp0.getPathLength();
                        double plc = dspc.getPathLength();

                        if (pl0 < minPl0 && plc < minPlc) {
                            minPl0 = pl0;
                            minLp0 = lp0;

                            minPlc = plc;
                            minLpc = lpc;

                            start = sv.getKmer();
                            stop = ev.getKmer();
                        }
                    }
                }
            }
        }

        return new PathInfo(start, stop, minLp0, minLpc);
    }

    private boolean hasRecombinations(String stretch) {
        StringBuilder inherit = new StringBuilder();
        for (int i = 0; i <= stretch.length() - GRAPH.getKmerSize(); i++) {
            String sk = stretch.substring(i, i + GRAPH.getKmerSize());
            CortexKmer ck = new CortexKmer(sk);

            CortexRecord cr = GRAPH.findRecord(ck);

            if (cr != null) {
                int cov0 = cr.getCoverage(0);
                int cov1 = cr.getCoverage(1);
                int cov2 = cr.getCoverage(2);

                if (cov0 > 0 && cov1 == 0 && cov2 == 0) {
                    inherit.append("C");
                } else if (cov0 > 0 && cov1 > 0 && cov2 == 0) {
                    inherit.append("M");
                } else if (cov0 > 0 && cov1 == 0 && cov2 > 0) {
                    inherit.append("D");
                } else if (cov0 > 0 && cov1 > 0 && cov2 > 0) {
                    inherit.append("B");
                } else {
                    inherit.append("?");
                }
            }
        }

        for (int i = 0; i < inherit.length(); i++) {
            if (inherit.charAt(i) == 'B') {
                char prevContext = '?';
                char nextContext = '?';

                for (int j = i - 1; j >= 0; j--) {
                    if (inherit.charAt(j) == 'M' || inherit.charAt(j) == 'D') {
                        prevContext = inherit.charAt(j);
                        break;
                    }
                }

                for (int j = i + 1; j < inherit.length(); j++) {
                    if (inherit.charAt(j) == 'M' || inherit.charAt(j) == 'D') {
                        nextContext = inherit.charAt(j);
                        break;
                    }
                }

                char context = '?';
                if (prevContext == nextContext && prevContext != '?') { context = prevContext; }
                else if (prevContext != nextContext) {
                    if (prevContext != '?') {
                        context = prevContext;
                    } else {
                        context = nextContext;
                    }
                }

                inherit.setCharAt(i, context);
            }
        }

        log.debug("    - inherit:     {}", inherit.toString());

        String inheritStr = inherit.toString();
        return inheritStr.matches(".*D+.C+.M+.*") || inheritStr.matches(".*M+.C+.D+.*");
    }

    private boolean isChimeric(String stretch, KmerLookup kl) {
        List<Set<Interval>> ils = kl.findKmers(stretch);

        StringBuilder sb = new StringBuilder();

        int nextAvailableCode = 0;
        Map<String, Integer> chrCodes = new HashMap<String, Integer>();
        Map<String, Integer> chrCount = new HashMap<String, Integer>();

        for (int i = 0; i < ils.size(); i++) {
            Set<Interval> il = ils.get(i);

            if (il.size() == 1) {
                Interval interval = il.iterator().next();

                ContainerUtils.increment(chrCount, interval.getSequence());

                if (!chrCodes.containsKey(interval.getSequence())) {
                    chrCodes.put(interval.getSequence(), nextAvailableCode);
                    nextAvailableCode++;
                }

                int code = chrCodes.get(interval.getSequence());
                sb.append(code);
            } else {
                sb.append(".");
            }
        }

        log.debug("    - chimeric:    {}", sb.toString());

        return chrCount.size() > 1;
    }

    private VariantContext callVariant(DirectedGraph<AnnotatedVertex, AnnotatedEdge> a, int color, String stretch, Map<CortexKmer, Boolean> novelKmers, KmerLookup kl) {
        // Compute paths
        PathInfo p = computeBestMinWeightPath(a, color, stretch, novelKmers);

        // Trim back to reference and variant alleles
        int s, e0 = p.child.length() - 1, e1 = p.parent.length() - 1;

        for (s = 0; s < (p.child.length() < p.parent.length() ? p.child.length() : p.parent.length()) && p.child.charAt(s) == p.parent.charAt(s); s++) {}

        while (e0 > s && e1 > s && p.child.charAt(e0) == p.parent.charAt(e1)) {
            e0--;
            e1--;
        }

        String parentalAllele = p.parent == null || p.parent.equals("") || p.child.equals(p.parent) ? "A" : p.parent.substring(s, e1 + 1);
        String childAllele = p.child == null || p.child.equals("") || p.child.equals(p.parent) ? "N" : p.child.substring(s, e0 + 1);

        int e = s + parentalAllele.length() - 1;

        // Decide if the event is actually a GC or NAHR event
        log.debug("    novel stretch: {}", stretch);

        boolean hasRecombs = hasRecombinations(stretch);
        boolean isChimeric = isChimeric(stretch, kl);

        List<Set<Interval>> alignment = kl.align(CortexUtils.getSeededStretchLeft(GRAPH, p.start, color, false) + p.parent + CortexUtils.getSeededStretchRight(GRAPH, p.stop, color, false));
        List<Set<Interval>> anovel = kl.align(stretch);
        log.info("    - align {} {}", color, alignment);
        log.info("    - align {} {}", 0, anovel);

        // Build the VC
        VariantContextBuilder vcb = new VariantContextBuilder()
                .chr("unknown")
                .start(s)
                .stop(e)
                .alleles(parentalAllele, childAllele)
                .attribute("NOVEL_STRETCH", stretch)
                .attribute("CHILD_STRETCH", p.child)
                .attribute("PARENT_STRETCH", p.parent)
                .attribute("CHILD_COLOR", 0)
                .attribute("PARENT_COLOR", color)
                .attribute("DENOVO", "UNKNOWN")
                .source(String.valueOf(color));

        if (childAllele.equals("N")) {
            if (hasRecombs) {
                vcb.attribute("DENOVO", "GC");
            } else if (isChimeric) {
                vcb.attribute("DENOVO", "NAHR");
            } else {
                vcb.attribute("DENOVO", "UNKNOWN");
                vcb.filter("TRAVERSAL_INCOMPLETE");
            }
        } else {
            if (parentalAllele.length() == childAllele.length()) {
                if (parentalAllele.length() == 1) {
                    vcb.attribute("DENOVO", "SNP");
                } else if (SequenceUtils.reverseComplement(parentalAllele).equals(childAllele)) {
                    vcb.attribute("DENOVO", "INV");
                }
            } else if (parentalAllele.length() == 1 && childAllele.length() > 1) {
                vcb.attribute("DENOVO", "INS");

                String childAlleleTrimmed = childAllele;
                if (parentalAllele.length() == 1 && childAllele.charAt(0) == parentalAllele.charAt(0)) {
                    childAlleleTrimmed = childAllele.substring(1, childAllele.length());
                } else if (parentalAllele.length() == 1 && childAllele.charAt(childAllele.length() - 1) == parentalAllele.charAt(0)) {
                    childAlleleTrimmed = childAllele.substring(0, childAllele.length() - 1);
                }

                log.info("cat: {} {} {}", parentalAllele, childAllele, childAlleleTrimmed);

                String repUnit = "";
                boolean isStrExp = false;
                for (int repLength = 2; repLength <= 5 && repLength < childAlleleTrimmed.length(); repLength++) {
                    repUnit = childAlleleTrimmed.substring(0, repLength);

                    boolean fits = true;
                    for (int i = 0; i < childAlleleTrimmed.length(); i += repLength) {
                        fits &= (i + repLength <= childAllele.length()) && childAllele.substring(i, i + repLength).equals(repUnit);
                    }

                    if ( fits &&
                       ( p.parent.substring(s - repLength, s).equals(repUnit) ||
                         p.parent.substring(s, s + repLength).equals(repUnit)) ) {
                        vcb.attribute("DENOVO", "STR_EXP");
                        vcb.attribute("REPUNIT", repUnit);
                        isStrExp = true;
                    }
                }

                if ( !isStrExp && childAlleleTrimmed.length() >= 10 && childAlleleTrimmed.length() <= 50 &&
                     ((s - childAlleleTrimmed.length() >= 0 && p.parent.substring(s - childAlleleTrimmed.length(), s).equals(childAlleleTrimmed)) ||
                      (s + childAlleleTrimmed.length() <= p.parent.length() && p.parent.substring(s, s + childAlleleTrimmed.length()).equals(childAlleleTrimmed))) ) {
                    vcb.attribute("DENOVO", "TD");
                    vcb.attribute("REPUNIT", repUnit);
                }
            } else if (parentalAllele.length() > 1 && childAllele.length() == 1) {
                vcb.attribute("DENOVO", "DEL");

                String parentalAlleleTrimmed = parentalAllele;
                if (childAllele.length() == 1 && parentalAllele.charAt(0) == childAllele.charAt(0)) {
                    parentalAlleleTrimmed = parentalAllele.substring(1, parentalAllele.length());
                } else if (childAllele.length() == 1 && parentalAllele.charAt(parentalAllele.length() - 1) == childAllele.charAt(0)) {
                    parentalAlleleTrimmed = parentalAllele.substring(0, parentalAllele.length() - 1);
                }

                log.info("cat: {} {} {}", parentalAllele, childAllele, parentalAlleleTrimmed);

                String repUnit = "";
                for (int repLength = 2; repLength <= 5 && repLength < parentalAlleleTrimmed.length(); repLength++) {
                    repUnit = parentalAlleleTrimmed.substring(0, repLength);

                    boolean fits = true;
                    for (int i = 0; i < parentalAlleleTrimmed.length(); i += repLength) {
                        fits &= (i + repLength <= parentalAllele.length()) && parentalAllele.substring(i, i + repLength).equals(repUnit);
                    }

                    if ( fits &&
                            ( p.child.substring(s - repLength, s).equals(repUnit) ||
                              p.child.substring(s + parentalAlleleTrimmed.length(), s + parentalAlleleTrimmed.length() + repLength).equals(repUnit)) ) {
                        vcb.attribute("DENOVO", "STR_CON");
                        vcb.attribute("REPUNIT", repUnit);
                    }
                }
            }
        }

        return vcb.make();
    }

    private DirectedGraph<AnnotatedVertex, AnnotatedEdge> loadLocalGraph(Map<CortexKmer, Boolean> novelKmers, CortexKmer novelKmer, String stretch) {
        // first, explore each color and bring the local subgraphs into memory
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> sg0 = new DefaultDirectedGraph<AnnotatedVertex, AnnotatedEdge>(AnnotatedEdge.class);
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> sg1 = new DefaultDirectedGraph<AnnotatedVertex, AnnotatedEdge>(AnnotatedEdge.class);
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> sg2 = new DefaultDirectedGraph<AnnotatedVertex, AnnotatedEdge>(AnnotatedEdge.class);

        for (int i = 0; i <= stretch.length() - novelKmer.length(); i++) {
            String kmer = stretch.substring(i, i + novelKmer.length());
            AnnotatedVertex ak = new AnnotatedVertex(kmer);

            if (!sg0.containsVertex(ak)) {
                Graphs.addGraph(sg0, CortexUtils.dfs(GRAPH, GRAPH_RAW, kmer, 0, null, new AbstractTraversalStopper<AnnotatedVertex, AnnotatedEdge>() {
                    @Override
                    public boolean hasTraversalSucceeded(CortexRecord cr, DirectedGraph<AnnotatedVertex, AnnotatedEdge> g, int depth) {
                        return cr.getCoverage(1) > 0 || cr.getCoverage(2) > 0;
                    }

                    @Override
                    public boolean hasTraversalFailed(CortexRecord cr, DirectedGraph<AnnotatedVertex, AnnotatedEdge> g, int junctions) {
                        return false;
                    }

                    @Override
                    public int maxJunctionsAllowed() {
                        return 3;
                    }
                }));
            }
        }

        for (int c = 1; c <= 2; c++) {
            for (AnnotatedVertex ak : sg0.vertexSet()) {
                DirectedGraph<AnnotatedVertex, AnnotatedEdge> sg = (c == 1) ? sg1 : sg2;

                if (!sg.containsVertex(ak)) {
                    TraversalStopper<AnnotatedVertex, AnnotatedEdge> stopper = new AbstractTraversalStopper<AnnotatedVertex, AnnotatedEdge>() {
                        @Override
                        public boolean hasTraversalSucceeded(CortexRecord cr, DirectedGraph<AnnotatedVertex, AnnotatedEdge> g, int junctions) {
                            String fw = cr.getKmerAsString();
                            String rc = SequenceUtils.reverseComplement(fw);

                            if (g.containsVertex(new AnnotatedVertex(fw)) || g.containsVertex(new AnnotatedVertex(rc))) {
                                if (junctions < distanceToGoal) {
                                    distanceToGoal = junctions;
                                }

                                return true;
                            }

                            return false;
                        }

                        @Override
                        public boolean hasTraversalFailed(CortexRecord cr, DirectedGraph<AnnotatedVertex, AnnotatedEdge> g, int junctions) {
                            return junctions >= maxJunctionsAllowed();
                        }

                        @Override
                        public int maxJunctionsAllowed() {
                            return 5;
                        }
                    };

                    Graphs.addGraph(sg, CortexUtils.dfs(GRAPH, GRAPH_RAW, ak.getKmer(), c, sg0, stopper));
                }
            }
        }

        // Now, combine them all into an annotated graph
        DirectedGraph<AnnotatedVertex, AnnotatedEdge> ag = new DefaultDirectedGraph<AnnotatedVertex, AnnotatedEdge>(AnnotatedEdge.class);

        addGraph(ag, sg0, 0, novelKmers);
        addGraph(ag, sg1, 1, novelKmers);
        addGraph(ag, sg2, 2, novelKmers);

        return ag;
    }

    private class AnnotateStartsAndEnds {
        private int color;
        private String stretch;
        private Map<CortexKmer, Boolean> novelKmers;
        private DirectedGraph<AnnotatedVertex, AnnotatedEdge> b;
        private Set<AnnotatedVertex> candidateEnds;
        private Set<AnnotatedVertex> candidateStarts;

        public AnnotateStartsAndEnds(int color, String stretch, Map<CortexKmer, Boolean> novelKmers, DirectedGraph<AnnotatedVertex, AnnotatedEdge> b) {
            this.color = color;
            this.stretch = stretch;
            this.novelKmers = novelKmers;
            this.b = b;
        }

        public Set<AnnotatedVertex> getCandidateEnds() {
            return candidateEnds;
        }

        public Set<AnnotatedVertex> getCandidateStarts() {
            return candidateStarts;
        }

        public AnnotateStartsAndEnds invoke() {
            int kmerLength = novelKmers.keySet().iterator().next().length();

            String fk = stretch.substring(1, kmerLength + 1);
            AnnotatedVertex afk = new AnnotatedVertex(fk, novelKmers.containsKey(new CortexKmer(fk)));

            String lk = stretch.substring(stretch.length() - kmerLength - 1, stretch.length() - 1);
            AnnotatedVertex alk = new AnnotatedVertex(lk, novelKmers.containsKey(new CortexKmer(lk)));

            candidateEnds = new HashSet<AnnotatedVertex>();
            DepthFirstIterator<AnnotatedVertex, AnnotatedEdge> dfsLk = new DepthFirstIterator<AnnotatedVertex, AnnotatedEdge>(b, afk);
            while (dfsLk.hasNext()) {
                AnnotatedVertex av = dfsLk.next();

                if (b.inDegreeOf(av) > 1) {
                    Set<AnnotatedEdge> aes = b.incomingEdgesOf(av);

                    boolean adjacentToNovelty = false;
                    AnnotatedEdge edgeToNovelty = null;

                    for (AnnotatedEdge ae : aes) {
                        if (ae.isPresent(0) && ae.isAbsent(1) && ae.isAbsent(2)) {
                            adjacentToNovelty = true;
                            edgeToNovelty = ae;
                        }
                    }

                    if (adjacentToNovelty) {
                        for (AnnotatedEdge ae : aes) {
                            if (!ae.equals(edgeToNovelty)) {
                                if (ae.isPresent(color)) {
                                    candidateEnds.add(av);

                                    //av.setFlag("end");

                                    //
                                    for (AnnotatedVertex ava : b.vertexSet()) {
                                        if (av.equals(ava)) {
                                            ava.setFlag("end");
                                        }
                                    }
                                    //
                                }
                            }
                        }
                    }
                }
            }

            candidateStarts = new HashSet<AnnotatedVertex>();
            DirectedGraph<AnnotatedVertex, AnnotatedEdge> rag = new EdgeReversedGraph<AnnotatedVertex, AnnotatedEdge>(b);
            DepthFirstIterator<AnnotatedVertex, AnnotatedEdge> dfsFk = new DepthFirstIterator<AnnotatedVertex, AnnotatedEdge>(rag, alk);
            while (dfsFk.hasNext()) {
                AnnotatedVertex av = dfsFk.next();

                if (rag.inDegreeOf(av) > 1) {
                    Set<AnnotatedEdge> aes = rag.incomingEdgesOf(av);

                    boolean adjacentToNovelty = false;
                    AnnotatedEdge edgeToNovelty = null;

                    for (AnnotatedEdge ae : aes) {
                        if (ae.isPresent(0) && ae.isAbsent(1) && ae.isAbsent(2)) {
                            adjacentToNovelty = true;
                            edgeToNovelty = ae;
                        }
                    }

                    if (adjacentToNovelty) {
                        for (AnnotatedEdge ae : aes) {
                            if (!ae.equals(edgeToNovelty)) {
                                if (ae.isPresent(color)) {
                                    candidateStarts.add(av);

                                    //av.setFlag("start");

                                    //
                                    for (AnnotatedVertex ava : b.vertexSet()) {
                                        if (av.equals(ava)) {
                                            ava.setFlag("start");
                                        }
                                    }
                                    //
                                }
                            }
                        }
                    }
                }
            }
            return this;
        }
    }

    private VariantInfo evalVariant(VariantContext vc, Map<CortexKmer, VariantInfo> vis, String stretch) {
        Set<VariantInfo> relevantVis = new HashSet<VariantInfo>();
        int kmerSize = vis.keySet().iterator().next().length();

        for (int i = 0; i <= stretch.length() - kmerSize; i++) {
            CortexKmer ck = new CortexKmer(stretch.substring(i, i + kmerSize));

            if (vis.containsKey(ck)) {
                relevantVis.add(vis.get(ck));
            }
        }

        if (relevantVis.size() > 0) {
            VariantInfo vi = relevantVis.iterator().next();

            String ref = vc.getReference().getBaseString();
            String alt = vc.getAlternateAllele(0).getBaseString();

            String refStretch = vc.getAttributeAsString("PARENT_STRETCH", ref);
            String altStretch = vc.getAttributeAsString("CHILD_STRETCH", alt);

            int pos = vc.getStart();
            int refLength = vc.getReference().length();
            int altLength = vc.getAlternateAllele(0).length();
            boolean found = false;

            while (pos >= 0 && pos + refLength < refStretch.length() && pos + altLength < altStretch.length()) {
                String refFw = refStretch.substring(pos, pos + refLength);
                String refRc = SequenceUtils.reverseComplement(refFw);

                String altFw = altStretch.substring(pos, pos + altLength);
                String altRc = SequenceUtils.reverseComplement(altFw);

                if (vi.ref.equals(refFw) && vi.alt != null && vi.alt.equals(altFw)) {
                    ref = refFw;
                    alt = altFw;
                    found = true;
                    break;
                } else if (vi.ref.equals(refRc) && vi.alt != null && vi.alt.equals(altRc)) {
                    ref = refRc;
                    alt = altRc;
                    found = true;
                    break;
                }

                pos--;
            }

            if (!found) {
                pos = vc.getStart();

                while (pos >= 0 && pos + refLength < refStretch.length() && pos + altLength < altStretch.length()) {
                    String refFw = refStretch.substring(pos, pos + refLength);
                    String refRc = SequenceUtils.reverseComplement(refFw);

                    String altFw = altStretch.substring(pos, pos + altLength);
                    String altRc = SequenceUtils.reverseComplement(altFw);

                    if (vi.ref.equals(refFw) && vi.alt != null && vi.alt.equals(altFw)) {
                        ref = refFw;
                        alt = altFw;
                        found = true;
                        break;
                    } else if (vi.ref.equals(refRc) && vi.alt != null && vi.alt.equals(altRc)) {
                        ref = refRc;
                        alt = altRc;
                        found = true;
                        break;
                    }

                    pos++;
                }
            }

            String knownRef = vi.ref;
            String knownAlt = vi.alt == null ? "" : vi.alt;

            if (!found && knownRef.length() > ref.length() && knownAlt.length() > alt.length() && knownRef.length() == knownAlt.length()) {
                String refFw = ref;
                String altFw = alt;
                String refRc = SequenceUtils.reverseComplement(refFw);
                String altRc = SequenceUtils.reverseComplement(altFw);

                String refFinal = null, altFinal = null;

                if (knownRef.contains(refFw) && knownAlt.contains(altFw)) {
                    refFinal = refFw;
                    altFinal = altFw;
                } else if (knownRef.contains(refRc) && knownAlt.contains(altRc)) {
                    refFinal = refRc;
                    altFinal = altRc;
                }

                if (refFinal != null && altFinal != null) {
                    int r0index = knownRef.indexOf(refFinal);
                    int a0index = knownAlt.indexOf(altFinal);
                    int r1index = r0index + refFinal.length();
                    int a1index = a0index + altFinal.length();

                    if (r0index == a0index && r1index == a1index &&
                        knownRef.substring(0, r0index).equals(knownAlt.substring(0, a0index)) &&
                        knownRef.substring(r1index, knownRef.length()).equals(knownAlt.substring(a1index, knownAlt.length()))) {
                        knownRef = ref;
                        knownAlt = alt;
                    }
                }
            }

            log.debug("    - vi: {}", vi);
            log.debug("        known ref: {}", knownRef);
            log.debug("        known alt: {}", knownAlt);
            log.debug("        called ref: {}", ref);
            log.debug("        called alt: {}", alt);

            log.debug("    - matches: {} ({} {} {} {})", knownRef.equals(ref) && knownAlt.equals(alt), ref, alt, knownRef, knownAlt);

            return (knownRef.equals(ref) && knownAlt.equals(alt)) || vi.denovo.equals(vc.getAttributeAsString("DENOVO", "unknown")) ? vi : null;
        }

        log.debug("    - matches: {}", false);

        return null;
    }

    @Override
    public void execute() {
        log.info("Loading reference indices for fast kmer lookup...");
        KmerLookup kl1 = new KmerLookup(REF1);
        KmerLookup kl2 = new KmerLookup(REF2);

        Map<CortexKmer, VariantInfo> vis = loadNovelKmerMap();
        Map<VariantInfo, Integer> visSeen = new HashMap<VariantInfo, Integer>();
        for (CortexKmer ck : vis.keySet()) {
            visSeen.put(vis.get(ck), 0);
        }

        Map<CortexKmer, Boolean> novelKmers = new HashMap<CortexKmer, Boolean>();
        for (CortexRecord cr : NOVEL) {
            novelKmers.put(new CortexKmer(cr.getKmerAsString()), true);
        }

        TableWriter tw = new TableWriter(out);
        int totalNovelKmersUsed = 0;
        int stretchNum = 0;
        int numMatches = 0;

        log.info("Genotyping novel kmer stretches in graph...");
        for (CortexKmer novelKmer : novelKmers.keySet()) {
            if (novelKmers.get(novelKmer)) {
                int novelKmersUsed = 0;

                // Walk the graph left and right of novelKmer and extract a novel stretch
                String stretch = CortexUtils.getSeededStretch(GRAPH, novelKmer.getKmerAsString(), 0, AGGRESSIVE);
                log.info("  stretch : {} ({} bp)", stretchNum, stretch.length());

                // Fetch the local subgraph context from disk
                DirectedGraph<AnnotatedVertex, AnnotatedEdge> ag = loadLocalGraph(novelKmers, novelKmer, stretch);
                log.info("    subgraph : {} vertices, {} edges", ag.vertexSet().size(), ag.edgeSet().size());

                // See how many novel kmers we've used up
                for (AnnotatedVertex ak : ag.vertexSet()) {
                    CortexKmer ck = new CortexKmer(ak.getKmer());

                    if (novelKmers.containsKey(ck) && novelKmers.get(ck)) {
                        totalNovelKmersUsed++;
                        novelKmersUsed++;
                        novelKmers.put(ck, false);
                    }
                }

                log.info("    novelty  : novelKmersUsed: {}, totalNovelKmersUsed: {}/{}", novelKmersUsed, totalNovelKmersUsed, novelKmers.size());

                // Extract stretches
                PathInfo p1 = computeBestMinWeightPath(ag, 1, stretch, novelKmers);
                PathInfo p2 = computeBestMinWeightPath(ag, 2, stretch, novelKmers);

                log.info("    stretches:");
                log.info("    - s1: {}", p1.parent);
                log.info("          {}", p1.child);
                log.info("    - s2: {}", p2.parent);
                log.info("          {}", p2.child);

                // Call variants
                VariantContext vc1 = callVariant(ag, 1, stretch, novelKmers, kl1);
                VariantContext vc2 = callVariant(ag, 2, stretch, novelKmers, kl2);

                log.info("    variants:");
                log.info("    - vc1: {} {} {} {}", vc1.getReference(), vc1.getAlternateAllele(0), vc1.getAttributeAsString("DENOVO", "unknown"), vc1.getFilters());
                log.info("    - vc2: {} {} {} {}", vc2.getReference(), vc2.getAlternateAllele(0), vc2.getAttributeAsString("DENOVO", "unknown"), vc2.getFilters());

                Map<String, String> te = new LinkedHashMap<String, String>();
                te.put("stretchNum", String.valueOf(stretchNum));
                te.put("stretch", stretch);

                te.put("ref1", vc1.getReference().getBaseString());
                te.put("alt1", vc1.getAlternateAllele(0).getBaseString());
                te.put("type1", vc1.getType().toString());
                te.put("denovo1", vc1.getAttributeAsString("denovo", "unknown"));

                te.put("ref2", vc2.getReference().getBaseString());
                te.put("alt2", vc2.getAlternateAllele(0).getBaseString());
                te.put("type2", vc2.getType().toString());
                te.put("denovo2", vc1.getAttributeAsString("denovo", "unknown"));

                te.put("match", "NA");
                te.put("m1", "NA");
                te.put("m2", "NA");

                // Evaluate variants
                if (BED != null) {
                    log.info("    evaluate:");
                    VariantInfo m1 = evalVariant(vc1, vis, stretch);
                    VariantInfo m2 = evalVariant(vc2, vis, stretch);

                    printGraph(simplifyGraph(ag), "debug", false, false);
                    //printGraph(ag, "debugFull", false, false);

                    if (m1 != null) { visSeen.put(m1, visSeen.get(m1) + 1); }
                    else if (m2 != null) { visSeen.put(m2, visSeen.get(m2) + 1); }

                    if (m1 != null || m2 != null) {
                        te.put("m1", m1 != null ? m1.variantId : "NA");
                        te.put("m2", m2 != null ? m2.variantId : "NA");

                        log.info("    - m1: {}", m1);
                        log.info("    - m2: {}", m2);
                        log.info("    - match!");

                        numMatches++;

                        te.put("match", "true");
                    } else {
                        log.info("    - no match :(");

                        te.put("match", "false");
                    }
                }

                tw.addEntry(te);

                stretchNum++;
            }
        }

        log.info("Num stretches: {}", stretchNum - 1);
        log.info("Num matches: {}", numMatches);

        int vid = 1;
        for (VariantInfo vi : visSeen.keySet()) {
            eout.println(vid + "\t" + vi.variantId + "\t" + vi.type + "\t" + vi.denovo + "\t" + visSeen.get(vi));
            vid++;
        }
    }
}
