package org.javacs;

public interface ReportReferencesProgress {
    void scanForPotentialReferences(int nScanned, int nFiles);

    void checkPotentialReferences(int nCompiled, int nPotential);
}
