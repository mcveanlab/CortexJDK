package uk.ac.ox.well.cortexjdk.utils.io.cortex.links;

import uk.ac.ox.well.cortexjdk.utils.io.cortex.graph.CortexKmer;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CortexLinksMap implements Map<CortexKmer, CortexLinksRecord> {
    private CortexLinksIterable cortexGraphLinks;
    private Map<CortexKmer, CortexLinksRecord> recordHash;

    public CortexLinksMap(String cortexLinksPath) { initialize(new CortexLinksIterable(cortexLinksPath)); }
    public CortexLinksMap(File cortexLinksFile) { initialize(new CortexLinksIterable(cortexLinksFile)); }

    private void initialize(CortexLinksIterable cortexGraphLinks) {
        this.cortexGraphLinks = cortexGraphLinks;

        recordHash = new HashMap<>((int) cortexGraphLinks.getNumLinks());

        for (CortexLinksRecord clr : cortexGraphLinks) {
            recordHash.put(clr.getKmer(), clr);
        }
    }

    public CortexLinksIterable getCortexGraphLinks() { return cortexGraphLinks; }

    public boolean containsKey(String kmer) { return containsKey(new CortexKmer(kmer)); }

    public boolean containsKey(byte[] kmer) { return containsKey(new CortexKmer(kmer)); }

    public CortexLinksRecord get(String kmer) { return get(new CortexKmer(kmer)); }

    public CortexLinksRecord get(byte[] kmer) { return get(new CortexKmer(kmer)); }

    @Override
    public int size() { return recordHash.size(); }

    @Override
    public boolean isEmpty() { return recordHash.isEmpty(); }

    @Override
    public boolean containsKey(Object key) { return recordHash.containsKey(key); }

    @Override
    public boolean containsValue(Object value) { return recordHash.containsValue(value); }

    @Override
    public CortexLinksRecord get(Object key) { return recordHash.get(key); }

    @Override
    public CortexLinksRecord put(CortexKmer key, CortexLinksRecord value) { return recordHash.put(key, value); }

    @Override
    public CortexLinksRecord remove(Object key) { return recordHash.remove(key); }

    @Override
    public void putAll(Map<? extends CortexKmer, ? extends CortexLinksRecord> m) { recordHash.putAll(m); }

    @Override
    public void clear() { recordHash.clear(); }

    @Override
    public Set<CortexKmer> keySet() { return recordHash.keySet(); }

    @Override
    public Collection<CortexLinksRecord> values() { return recordHash.values(); }

    @Override
    public Set<Entry<CortexKmer, CortexLinksRecord>> entrySet() { return recordHash.entrySet(); }
}