package uk.ac.ox.well.cortexjdk.utils.kmer;

import uk.ac.ox.well.cortexjdk.utils.io.graph.cortex.CortexRecord;
import uk.ac.ox.well.cortexjdk.utils.sequence.SequenceUtils;

import java.io.Serializable;
import java.util.Arrays;

public class CortexBinaryKmer implements Comparable<CortexBinaryKmer>, Serializable {
    private long[] binaryKmer;

    public CortexBinaryKmer(long[] binaryKmer) {
        this.binaryKmer = binaryKmer;
    }

    public CortexBinaryKmer(byte[] kmer) {
        this.binaryKmer = CortexRecord.encodeBinaryKmer(SequenceUtils.alphanumericallyLowestOrientation(kmer));
    }

    public long[] getBinaryKmer() {
        return binaryKmer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CortexBinaryKmer that = (CortexBinaryKmer) o;

        return Arrays.equals(binaryKmer, that.binaryKmer);

    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(binaryKmer);
    }

    @Override
    public int compareTo(CortexBinaryKmer o) {
        if (!Arrays.equals(binaryKmer, o.getBinaryKmer())) {
            for (int b = 0; b < binaryKmer.length; b++) {
                if (binaryKmer[b] != o.getBinaryKmer()[b]) {
                    return (binaryKmer[b] < o.getBinaryKmer()[b]) ? -1 : 1;
                }
            }
        }

        return 0;
    }
}
