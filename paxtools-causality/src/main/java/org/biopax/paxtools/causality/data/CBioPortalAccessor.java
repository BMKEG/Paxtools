package org.biopax.paxtools.causality.data;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.causality.model.Alteration;
import org.biopax.paxtools.causality.model.AlterationPack;
import org.biopax.paxtools.causality.model.Change;
import org.biopax.paxtools.causality.model.Node;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class CBioPortalAccessor extends AlterationProviderAdaptor {
    private static Log log = LogFactory.getLog(CBioPortalAccessor.class);

    protected final static String PORTAL_URL = "http://www.cbioportal.org/public-portal/webservice.do?";
    protected final static String COMMAND = "cmd=";
    protected final static String DELIMITER = "\t";

    private List<CancerStudy> cancerStudies = new ArrayList<CancerStudy>();
    private CancerStudy currentCancerStudy = null;

    private Map<CancerStudy, List<GeneticProfile>> geneticProfilesCache
            = new HashMap<CancerStudy, List<GeneticProfile>>();
    private Map<CancerStudy, List<CaseList>> caseListCache = new HashMap<CancerStudy, List<CaseList>>();

    public CBioPortalAccessor() throws IOException {
        initializeStudies();
        assert !cancerStudies.isEmpty();
        setCurrentCancerStudy(cancerStudies.get(0));
    }

    private void initializeStudies() throws IOException {
        String urlStr = "getCancerStudies";

        for (String[] result : parseURL(urlStr)) {
            assert result.length == 3;
            cancerStudies.add(new CancerStudy(result[0], result[1], result[2]));
        }
    }

    public AlterationPack getAlterations(Node node, Collection<GeneticProfile> geneticProfiles) {
        String entrezGeneId = getEntrezGeneID(node);
		AlterationPack alterationPack = new AlterationPack(entrezGeneId);

        CaseList allCaseList = null;
        try {
            allCaseList = getAllCasesForCurrentStudy();
        } catch (IOException e) {
            log.error("Could not parse case lists for this study. Returning an empty alteration map.");
            return alterationPack;
        }

        List<GeneticProfile> geneticProfilesForCurrentStudy;
        try {
            geneticProfilesForCurrentStudy = getGeneticProfilesForCurrentStudy();
        } catch (IOException e) {
            geneticProfilesForCurrentStudy = new ArrayList<GeneticProfile>();
        }

        for (GeneticProfile geneticProfile : geneticProfiles) {
            if(!geneticProfilesForCurrentStudy.contains(geneticProfile)) {
                log.warn("the genetic profile "
                        + geneticProfile.getId() + " is not in the available profiles list. Skipping.");
                continue;
            }

            Change[] changes;

            try {
                changes = getDataForCurrentStudy(geneticProfile, entrezGeneId, allCaseList);
            } catch (IOException e) {
                log.error("Could not get data for genetic profile " + geneticProfile.getId()
                        + ". Skipping...");
                continue;
            }

            Alteration alteration = GeneticProfile.GENETIC_PROFILE_TYPE.convertToAlteration(geneticProfile.getType());
            Change[] altChanges = alterationPack.get(alteration);
            if(altChanges == null)
                alterationPack.put(alteration, changes);
            else
                alterationPack.put(alteration, mergeChanges(altChanges, changes));
        }

        return alterationPack;
    }

    private Change[] mergeChanges(Change[] changes1, Change[] changes2) {
        assert changes1.length == changes2.length;

        Change[] consChanges = new Change[changes1.length];

        for(int i=0; i < changes1.length; i++) {
            Change c1 = changes1[i];
            Change c2 = changes2[i];
            Change consensus;

            if(c1.equals(Change.NO_DATA))
                consensus = c2;
            else if(c2.equals(Change.NO_DATA))
                consensus = c1;
            else if(c1.equals(Change.NO_CHANGE))
                consensus = c2;
            else if(c2.equals(Change.NO_CHANGE))
                consensus = c1;
            else {
                log.warn("Conflicting values on sample " + i + ": " + c1 + " vs " + c2
                        + ". Accepting the first value.");
                consensus = c1;
            }

            consChanges[i] = consensus;
        }

        return consChanges;
    }

    private Change[] getDataForCurrentStudy(GeneticProfile geneticProfile, String entrezGeneId, CaseList caseList)
            throws IOException {
        Map<String, Change> changesMap = new HashMap<String, Change>();

        String url = "getProfileData&case_set_id=" + caseList.getId() + "&"
                + "genetic_profile_id=" + geneticProfile.getId() + "&"
                + "gene_list=" + entrezGeneId;

        List<String[]> results = parseURL(url, false);
        assert results.size() > 1;
        String[] header = results.get(0);

        for(int i=1; i < results.size(); i++) {
            String[] dataPoints = results.get(i);
            log.debug("Obtained result for "
                    + dataPoints[0] + ":" + dataPoints[1]
                    + " (" + geneticProfile.getId() + ")");

            for(int j=2; j < dataPoints.length; j++) {
                changesMap.put(header[j], inferChange(geneticProfile, dataPoints[j]));
            }
        }

        Change[] changes = new Change[caseList.getCases().length];
        int counter = 0;
        for (String aCase : caseList.getCases()) {
            Change change = changesMap.get(aCase);
            changes[counter++] =  change == null ? Change.NO_DATA : change;
        }

        return changes;
    }

    private Change inferChange(GeneticProfile geneticProfile, String dataPoint) {
        final String NaN = "NaN";
        // TODO: Discuss these steps with Ozgun
        switch (GeneticProfile.GENETIC_PROFILE_TYPE.convertToAlteration(geneticProfile.getType())) {
            case MUTATION:
                return dataPoint.equalsIgnoreCase(NaN) ? Change.NO_CHANGE : Change.INHIBITING;
            case METHYLATION:
                return dataPoint.equalsIgnoreCase(NaN)
                        ? Change.NO_DATA
                        : (Double.parseDouble(dataPoint) > .5 ? Change.INHIBITING : Change.NO_CHANGE);
            case COPY_NUMBER:
                // TODO: what to do with log2CNA?
                if(dataPoint.equalsIgnoreCase(NaN) || geneticProfile.getId().endsWith("log2CNA"))
                    return Change.NO_DATA;
                else {
                    Double value = Double.parseDouble(dataPoint);
                    if(value < -1)
                        return Change.INHIBITING;
                    else if(value > 0.5)
                        return Change.ACTIVATING;
                    else
                        return Change.NO_CHANGE;
                }
            case EXPRESSION:
                // TODO: what to do with non Zscores?
                if(dataPoint.equalsIgnoreCase(NaN) || !geneticProfile.getId().endsWith("Zscores"))
                    return Change.NO_DATA;
                else {
                    Double value = Double.parseDouble(dataPoint);

                    if(value > 2)
                        return Change.ACTIVATING;
                    else if(value < -2)
                        return Change.INHIBITING;
                    else
                        return Change.NO_CHANGE;
                }
            case PROTEIN_LEVEL:
                if(dataPoint.equalsIgnoreCase(NaN)) {
                    return Change.NO_DATA;
                } else {
                    Double value = Double.parseDouble(dataPoint);

                    if(value > 1)
                        return Change.ACTIVATING;
                    else if(value < -1)
                        return Change.INHIBITING;
                    else
                        return Change.NO_CHANGE;
                }
            // TODO: How to analyze?
            case NON_GENOMIC:
            case ANY:
        }

        return Change.NO_CHANGE;
    }

    @Override
    public AlterationPack getAlterations(Node node) {
        List<GeneticProfile> geneticProfiles;
        try {
            geneticProfiles = getGeneticProfilesForCurrentStudy();
            return getAlterations(node, geneticProfiles);
        } catch (IOException e) {
            log.error("Could not parse genetic profiles. Returning null.");
            return null;
        }
    }


    public CaseList getAllCasesForCurrentStudy() throws IOException {
        final String allPostFix = "_all";
        List<CaseList> caseLists = getCaseListsForCurrentStudy();
        for (CaseList caseList : caseLists) {
            if(caseList.getId().endsWith(allPostFix)) {
                return caseList;
            }
        }

        log.warn("Could not find the case list: "
                + getCurrentCancerStudy().getStudyId()
                + allPostFix
                + ". Returning null");

        return null;
    }

    public List<CaseList> getCaseListsForCurrentStudy() throws IOException {
        List<CaseList> caseLists = caseListCache.get(getCurrentCancerStudy());
        if(caseLists != null)
            return caseLists;

        caseLists = new ArrayList<CaseList>();
        String url = "getCaseLists&cancer_study_id=" + getCurrentCancerStudy().getStudyId();
        for (String[] results : parseURL(url)) {
            assert results.length == 5;
            String[] cases = results[4].split(" ");
            assert cases.length > 1;

            caseLists.add(new CaseList(results[0], results[1], cases));
        }

        caseListCache.put(getCurrentCancerStudy(), caseLists);
        return caseLists;
    }

    private List<String[]> parseURL(String urlPostFix) throws IOException {
        return parseURL(urlPostFix, true);
    }

    private List<String[]> parseURL(String urlPostFix, boolean skipHeader) throws IOException {
        List<String[]> list = new ArrayList<String[]>();

        String urlStr = PORTAL_URL + COMMAND + urlPostFix;
        URL url = new URL(urlStr);
        URLConnection urlConnection = url.openConnection();
        Scanner scanner = new Scanner(urlConnection.getInputStream());

        int lineNum = 0;
        while(scanner.hasNextLine()) {
            String line = scanner.nextLine();
            lineNum++;

            if(line.startsWith("#") || line.length() == 0 || (skipHeader && lineNum == 2))
                continue;

            list.add(line.split(DELIMITER));
        }

        return list;
    }

    public List<CancerStudy> getCancerStudies() {
        return cancerStudies;
    }

    public CancerStudy getCurrentCancerStudy() {
        return currentCancerStudy;
    }

    public void setCurrentCancerStudy(CancerStudy currentCancerStudy) {
        if(!cancerStudies.contains(currentCancerStudy))
            throw new IllegalArgumentException("This cancer study is not available through the initialized list.");

        this.currentCancerStudy = currentCancerStudy;
    }

    public List<GeneticProfile> getGeneticProfilesForCurrentStudy() throws IOException {
        List<GeneticProfile> geneticProfiles = geneticProfilesCache.get(getCurrentCancerStudy());
        if(geneticProfiles != null)
            return geneticProfiles;

        geneticProfiles = new ArrayList<GeneticProfile>();

        String url = "getGeneticProfiles" + "&cancer_study_id=" + getCurrentCancerStudy().getStudyId();
        for (String[] results : parseURL(url)) {
            assert results.length == 6;
            geneticProfiles.add(new GeneticProfile(results[0], results[1], results[2], results[4]));
        }

        assert !geneticProfiles.isEmpty();
        geneticProfilesCache.put(getCurrentCancerStudy(), geneticProfiles);
        return geneticProfiles;
    }
}
