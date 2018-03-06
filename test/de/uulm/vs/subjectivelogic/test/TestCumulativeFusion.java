package de.uulm.vs.subjectivelogic.test;

import no.uio.subjective_logic.opinion.Opinion;
import no.uio.subjective_logic.opinion.OpinionBase;
import no.uio.subjective_logic.opinion.SubjectiveOpinion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestCumulativeFusion {
    private final Logger l = LogManager.getLogger(getClass());

    private SubjectiveOpinion soVacuous;
    private SubjectiveOpinion soNeutral;
    private SubjectiveOpinion soP;
    private SubjectiveOpinion soPP;
    private SubjectiveOpinion soPD;
    private SubjectiveOpinion soN;
    private SubjectiveOpinion soNN;
    private SubjectiveOpinion soND;

    private final double ATOMICITY = 0.5; // fixed baserate
    private final double NEUTRAL = 0.0;
    private final double SLIGHTLY = 0.3;
    private final double VERY = 0.7;
    private final double DOGMATCIC = 1.0;


    @Before
    public void setUp() {
        l.info("Setting up opinions...");
        soVacuous = new SubjectiveOpinion(0.0, 0.0, 1.0, ATOMICITY);
        soNeutral = new SubjectiveOpinion(SLIGHTLY, SLIGHTLY, 1 - 2 * SLIGHTLY, ATOMICITY);
        soP = new SubjectiveOpinion(SLIGHTLY, 0, 1 - SLIGHTLY, ATOMICITY);
        soPP = new SubjectiveOpinion(VERY, 0, 1 - VERY, ATOMICITY);
        soN = new SubjectiveOpinion(0, SLIGHTLY, 1 - SLIGHTLY, ATOMICITY);
        soNN = new SubjectiveOpinion(0, VERY, 1 - VERY, ATOMICITY);
        soPD = new SubjectiveOpinion(1, true);
        soND = new SubjectiveOpinion(0, true);
        l.debug(String.format("Vacuous opinion: %s", soVacuous.toString()));
        l.debug(String.format("Neutral opinion: %s", soNeutral.toString()));
        l.debug(String.format("Slightly positive opinion: %s", soP.toString()));
        l.debug(String.format("Very positive opinion: %s", soPP.toString()));
        l.debug(String.format("Slightly negative opinion: %s", soN.toString()));
        l.debug(String.format("Very negative opinion: %s", soNN.toString()));
        l.debug(String.format("Dogmatic positive opinion: %s", soPD.toString()));
        l.debug(String.format("Dogmatic negative opinion: %s", soND.toString()));
    }

    @Test
    public void testCumulativeFusion_2_opinions() {
        l.info("Testing SubjectiveLogic.cumulativeFuse(o)..");

        SubjectiveOpinion nonZero1 = new SubjectiveOpinion(soP);
        SubjectiveOpinion nonZero2 = new SubjectiveOpinion(soN);

        SubjectiveOpinion vacuous1 = new SubjectiveOpinion(soVacuous);
        SubjectiveOpinion vacuous2 = new SubjectiveOpinion(soVacuous);

        SubjectiveOpinion zero1 = new SubjectiveOpinion(soPD);
        SubjectiveOpinion zero2 = new SubjectiveOpinion(soND);

        //Case 1: u^A_X != 0 or u^B_X != 0

        // b = (b^A u^B + b^B u^A) / (u^A + u^B - u^au^B)
        // u = u^A u^B / (u^A + u^B - u^au^B)

        //Case 1A: u^A != 1 or u^B != 1
        // a = (a^Au^B + a^Bu^A - (a^A + a^B)u^Au^B)/(u^A + u^B - u^au^B)
        SubjectiveOpinion res = nonZero1.cumulativeFuse(nonZero2);

        Assert.assertTrue(res.isConsistent());
        double bA = nonZero1.getBelief();
        double dA = nonZero1.getDisbelief();
        double uA = nonZero1.getUncertainty();
        double aA = nonZero1.getAtomicity();

        double bB = nonZero2.getBelief();
        double dB = nonZero2.getDisbelief();
        double uB = nonZero2.getUncertainty();
        double aB = nonZero2.getAtomicity();

        double expectedBelief = (bA*uB + bB*uA)/(uA+uB-uA*uB);
        Assert.assertEquals(expectedBelief, res.getBelief(), OpinionBase.TOLERANCE);

        double expectedDisbelief = (dA*uB + dB*uA)/(uA+uB-uA*uB);
        Assert.assertEquals(expectedDisbelief, res.getDisbelief(), OpinionBase.TOLERANCE);

        double expectedUncertainty = uA*uB/(uA+uB-uA*uB);
        Assert.assertEquals(expectedUncertainty, res.getUncertainty(), OpinionBase.TOLERANCE);

        double expectedBaseRate = (aA*uB+aB*uA-(aA+aB)*uA*uB)/(uA+uB-2*uA*uB);
        Assert.assertEquals(expectedBaseRate, res.getAtomicity(), OpinionBase.TOLERANCE);

        //Case 1B: u^A = u^B = 1
        // a = (a^A + a^B)/2
        SubjectiveOpinion vac = vacuous1.cumulativeFuse(vacuous2);

        Assert.assertTrue(vac.isConsistent());
        bA = vacuous1.getBelief();
        dA = vacuous1.getDisbelief();
        uA = vacuous1.getUncertainty();
        aA = vacuous1.getAtomicity();

        dB = vacuous2.getDisbelief();
        bB = vacuous2.getBelief();
        uB = vacuous2.getUncertainty();
        aB = vacuous2.getAtomicity();

        expectedBelief = (bA*uB + bB*uA)/(uA+uB-uA*uB);
        Assert.assertEquals(expectedBelief, vac.getBelief(), OpinionBase.TOLERANCE);

        expectedDisbelief = (dA*uB + dB*uA)/(uA+uB-uA*uB);
        Assert.assertEquals(expectedDisbelief, vac.getDisbelief(), OpinionBase.TOLERANCE);

        expectedUncertainty = uA*uB/(uA+uB-uA*uB);
        Assert.assertEquals(expectedUncertainty, vac.getUncertainty(), OpinionBase.TOLERANCE);

        expectedBaseRate = (aA + aB)/2.0D;
        Assert.assertEquals(expectedBaseRate, vac.getAtomicity(), OpinionBase.TOLERANCE);

        //Case 2: u^A_X = u^B_X = 0
        SubjectiveOpinion dogmatic = zero1.cumulativeFuse(zero2);

        Assert.assertTrue(dogmatic.isConsistent());
        bA = zero1.getBelief();
        dA = zero1.getDisbelief();
        uA = zero1.getUncertainty();
        aA = zero1.getAtomicity();

        dB = zero2.getDisbelief();
        bB = zero2.getBelief();
        uB = zero2.getUncertainty();
        aB = zero2.getAtomicity();

        //note, book says: In case of dogmatic arguments it can be assumed that the limits in Eq.(12.15) are defined as γ X A = γ X B = 0.5.
        double gammaA = 0.5D;
        double gammaB = 0.5D;

        expectedBelief = gammaA*bA + gammaB*bB;
        Assert.assertEquals(expectedBelief, dogmatic.getBelief(), OpinionBase.TOLERANCE);

        expectedDisbelief = gammaA*dA + gammaB*dB;
        Assert.assertEquals(expectedDisbelief, dogmatic.getDisbelief(), OpinionBase.TOLERANCE);

        expectedUncertainty = 0.0D;
        Assert.assertEquals(expectedUncertainty, dogmatic.getUncertainty(), OpinionBase.TOLERANCE);

        expectedBaseRate = gammaA*aA + gammaB*aB;
        Assert.assertEquals(expectedBaseRate, dogmatic.getAtomicity(), OpinionBase.TOLERANCE);
    }

    @Test
    public void testCumulativeFusion_N_opinions_nonDogmatic() {
        l.info("Testing SubjectiveLogic.cumulativeFuse(o)..");

        SubjectiveOpinion nonZero1 = new SubjectiveOpinion(soPP);
        SubjectiveOpinion nonZero2 = new SubjectiveOpinion(soP);
        SubjectiveOpinion nonZero3 = new SubjectiveOpinion(soN);
        List<SubjectiveOpinion> opinionList = new ArrayList<>();
        opinionList.add(nonZero1); opinionList.add(nonZero2); opinionList.add(nonZero3);

        SubjectiveOpinion batchFuse = SubjectiveOpinion.cumulativeFuse(opinionList);

        SubjectiveOpinion seqFuse123 = nonZero1.cumulativeFuse(nonZero2).cumulativeFuse(nonZero3);
        Assert.assertEquals(seqFuse123, batchFuse);
        SubjectiveOpinion seqFuse132 = nonZero1.cumulativeFuse(nonZero3).cumulativeFuse(nonZero2);
        Assert.assertEquals(seqFuse132, batchFuse);
        SubjectiveOpinion seqFuse213 = nonZero2.cumulativeFuse(nonZero1).cumulativeFuse(nonZero3);
        Assert.assertEquals(seqFuse213, batchFuse);
        SubjectiveOpinion seqFuse231 = nonZero2.cumulativeFuse(nonZero3).cumulativeFuse(nonZero1);
        Assert.assertEquals(seqFuse231, batchFuse);
        SubjectiveOpinion seqFuse321 = nonZero3.cumulativeFuse(nonZero2).cumulativeFuse(nonZero1);
        Assert.assertEquals(seqFuse321, batchFuse);
        SubjectiveOpinion seqFuse312 = nonZero3.cumulativeFuse(nonZero1).cumulativeFuse(nonZero2);
        Assert.assertEquals(seqFuse312, batchFuse);
    }

    @Test
    public void testCumulativeBatchFusion_nonDogmatic() {
        l.info("Testing SubjectiveLogic.cumulativeCollectionFuse(o)..");

        SubjectiveOpinion C1 = new SubjectiveOpinion(0.1, 0.3, 0.6, 0.5);
        SubjectiveOpinion C2 = new SubjectiveOpinion(0.4, 0.2, 0.4, 0.5);
        SubjectiveOpinion C3 = new SubjectiveOpinion(0.7, 0.1, 0.2, 0.5);
        List<Opinion> opinionList = new ArrayList<>();
        opinionList.add(C1);
        opinionList.add(C2);
        opinionList.add(C3);

        SubjectiveOpinion batchFuse = SubjectiveOpinion.cumulativeCollectionFuse(opinionList);

        Assert.assertEquals(0.651, batchFuse.getBelief(), 0.001);
        Assert.assertEquals(0.209, batchFuse.getDisbelief(), 0.001);
        Assert.assertEquals(0.140, batchFuse.getUncertainty(), 0.001);

        Assert.assertEquals(SubjectiveOpinion.cumulativeFuse(opinionList), batchFuse);
    }

    @Test
    public void testCumulativeBatchFusion_mixed() {
        l.info("Testing SubjectiveLogic.cumulativeCollectionFuse(o)..");

        SubjectiveOpinion nonZero1 = new SubjectiveOpinion(soPD);
        SubjectiveOpinion nonZero2 = new SubjectiveOpinion(soND);
        SubjectiveOpinion nonZero3 = new SubjectiveOpinion(soP);
        List<Opinion> opinionList = new ArrayList<>();
        opinionList.add(nonZero1);
        opinionList.add(nonZero2);
        opinionList.add(nonZero3);

        SubjectiveOpinion batchFuse = SubjectiveOpinion.cumulativeCollectionFuse(opinionList);

        Assert.assertTrue(batchFuse.isConsistent());
    }

    @Test
    public void testCumulativeBatchFusion_dogmatic() {
        l.info("Testing SubjectiveLogic.cumulativeCollectionFuse(o)..");

        SubjectiveOpinion nonZero1 = new SubjectiveOpinion(soPD);
        SubjectiveOpinion nonZero2 = new SubjectiveOpinion(soPD);
        SubjectiveOpinion nonZero3 = new SubjectiveOpinion(soND);
        List<Opinion> opinionList = new ArrayList<>();
        opinionList.add(nonZero1);
        opinionList.add(nonZero2);
        opinionList.add(nonZero3);

        SubjectiveOpinion batchFuse = SubjectiveOpinion.cumulativeCollectionFuse(opinionList);

        Assert.assertTrue(batchFuse.isConsistent());
    }

    @Test
    public void testCumulativeFusion_N_opinions_dogmatic() {
        l.info("Testing SubjectiveLogic.cumulativeFuse(o)..");

        SubjectiveOpinion nonZero1 = new SubjectiveOpinion(soPD);
        SubjectiveOpinion nonZero2 = new SubjectiveOpinion(soND);
        SubjectiveOpinion nonZero3 = new SubjectiveOpinion(soND);
        List<SubjectiveOpinion> opinionList = new ArrayList<>();
        opinionList.add(nonZero1); opinionList.add(nonZero2); opinionList.add(nonZero3);

        SubjectiveOpinion batchFuse = SubjectiveOpinion.cumulativeFuse(opinionList);

        SubjectiveOpinion seqFuse123 = nonZero1.cumulativeFuse(nonZero2).cumulativeFuse(nonZero3);
        Assert.assertEquals(seqFuse123, batchFuse);
        SubjectiveOpinion seqFuse132 = nonZero1.cumulativeFuse(nonZero3).cumulativeFuse(nonZero2);
        Assert.assertEquals(seqFuse132, batchFuse);
        SubjectiveOpinion seqFuse213 = nonZero2.cumulativeFuse(nonZero1).cumulativeFuse(nonZero3);
        Assert.assertEquals(seqFuse213, batchFuse);
        SubjectiveOpinion seqFuse231 = nonZero2.cumulativeFuse(nonZero3).cumulativeFuse(nonZero1);
        Assert.assertEquals(seqFuse231, batchFuse);
        SubjectiveOpinion seqFuse312 = nonZero3.cumulativeFuse(nonZero1).cumulativeFuse(nonZero2);
        Assert.assertEquals(seqFuse312, batchFuse);
        SubjectiveOpinion seqFuse321 = nonZero3.cumulativeFuse(nonZero2).cumulativeFuse(nonZero1);
        Assert.assertEquals(seqFuse321, batchFuse);
    }

    @Test
    public void testRelativeWeights() {
        SubjectiveOpinion nondogmatic = new SubjectiveOpinion(soPP);

        SubjectiveOpinion nonZero1 = new SubjectiveOpinion(soPD);
        SubjectiveOpinion nonZero2 = new SubjectiveOpinion(soPD);
        SubjectiveOpinion nonZero3 = new SubjectiveOpinion(soND);
        List<SubjectiveOpinion> opinionList = new ArrayList<>();
        opinionList.add(nonZero1);
        opinionList.add(nonZero2);
        opinionList.add(nonZero3);

        SubjectiveOpinion intermediate = nonZero1.cumulativeFuse(nonZero2);

        Assert.assertEquals(intermediate.cumulativeFuse(nonZero3),
                nonZero3.cumulativeFuse(intermediate));

        SubjectiveOpinion final_opinion = intermediate.cumulativeFuse(nonZero3);

        //nondogmatic + dogmatic = dogmatic
        Assert.assertEquals(nondogmatic.cumulativeFuse(nonZero1), nonZero1);
        Assert.assertEquals(nondogmatic.cumulativeFuse(nonZero2), nonZero2);
        Assert.assertEquals(nondogmatic.cumulativeFuse(nonZero3), nonZero3);
        Assert.assertEquals(nondogmatic.cumulativeFuse(intermediate), intermediate);
        Assert.assertEquals(nondogmatic.cumulativeFuse(final_opinion), final_opinion);

        SubjectiveOpinion nondogmaticIntermediate = nondogmatic.cumulativeFuse(nonZero1);

        //(nondogmatic + dogmatic) + dogmatic = dogmatic + dogmatic
        Assert.assertEquals(nondogmaticIntermediate.cumulativeFuse(nonZero1), nonZero1);
        Assert.assertEquals(nondogmaticIntermediate.cumulativeFuse(nonZero2), nonZero1.cumulativeFuse(nonZero2));
        Assert.assertEquals(nondogmaticIntermediate.cumulativeFuse(intermediate), intermediate);

        //broken: (cases where not all dogmatic opinions agree..)
        Assert.assertEquals(nondogmaticIntermediate.cumulativeFuse(nonZero3), nonZero1.cumulativeFuse(nonZero3));
        Assert.assertEquals(nondogmaticIntermediate.cumulativeFuse(final_opinion), final_opinion.cumulativeFuse(nonZero1));
    }

    @Test
    public void testCumulativeFusion() {
        l.info("Testing Cumulative Fusion");

        List<Opinion> opinions = pastMisbehavior();
        SubjectiveOpinion result = SubjectiveOpinion.cumulativeFuse(opinions);
        l.info(String.format("Cumulative fused opinion: %s", result.toString()));

        List<Opinion> opinions2 = new ArrayList<>();
        opinions2.add(new SubjectiveOpinion(soP));
        opinions2.add(new SubjectiveOpinion(soN));
        opinions2.add(new SubjectiveOpinion(soN));
        opinions2.add(new SubjectiveOpinion(soP));
        SubjectiveOpinion result2 = SubjectiveOpinion.cumulativeFuse(opinions2);
        l.info(String.format("Cumulative fused opinion: %s", result2.toString()));

        List<Opinion> opinions3 = new ArrayList<>();
        opinions3.add(new SubjectiveOpinion(soP));
        opinions3.add(new SubjectiveOpinion(soN));
        opinions3.add(new SubjectiveOpinion(soP));
        opinions3.add(new SubjectiveOpinion(soN));
        SubjectiveOpinion result3 = SubjectiveOpinion.cumulativeFuse(opinions3);

        Assert.assertEquals(result2, result3);

        List<Opinion> opinions4 = new ArrayList<>();
        opinions4.add(new SubjectiveOpinion(soPD));
        opinions4.add(new SubjectiveOpinion(soND));
        opinions4.add(new SubjectiveOpinion(soP));
        opinions4.add(new SubjectiveOpinion(soN));
        opinions4.add(new SubjectiveOpinion(soND));
        SubjectiveOpinion result4 = SubjectiveOpinion.cumulativeFuse(opinions4);

        List<Opinion> opinions5 = new ArrayList<>();
        opinions5.add(new SubjectiveOpinion(soND));
        opinions5.add(new SubjectiveOpinion(soP));
        opinions5.add(new SubjectiveOpinion(soN));
        opinions5.add(new SubjectiveOpinion(soPD));
        opinions5.add(new SubjectiveOpinion(soND));
        SubjectiveOpinion result5 = SubjectiveOpinion.cumulativeFuse(opinions5);

        SubjectiveOpinion realRes4 = SubjectiveOpinion.cumulativeCollectionFuse(opinions4);
        SubjectiveOpinion realRes5 = SubjectiveOpinion.cumulativeCollectionFuse(opinions5);
        Assert.assertEquals(realRes4, realRes5);
        Assert.assertEquals(result4, result5);
        Assert.assertEquals(result4, realRes4);
        Assert.assertEquals(result5, realRes5);
    }

    @Test
    public void compareFusionOperators() {
        SubjectiveOpinion o1, o2;

        o1 = new SubjectiveOpinion(soP);
        o2 = new SubjectiveOpinion(soN);
        l.info("----- Slight belief and slight disbelief -----");
        l.info(String.format("Fusing opinions    %s and %s", o1.toString(), o2.toString()));
        l.info(String.format("Cumulative result: %s", o1.cumulativeFuse(o2).toString()));
        l.info(String.format("WBF result:        %s", o1.wbFuse(o2).toString()));
        l.info(String.format("CC result:         %s", o1.ccFuse(o2).toString()));


        o1 = new SubjectiveOpinion(soPP);
        o2 = new SubjectiveOpinion(soN);
        l.info("----- High belief and slight disbelief -----");
        l.info(String.format("Fusing opinions    %s and %s", o1.toString(), o2.toString()));
        l.info(String.format("Cumulative result: %s", o1.cumulativeFuse(o2).toString()));
        l.info(String.format("WBF result:        %s", o1.wbFuse(o2).toString()));
        l.info(String.format("CC result:         %s", o1.ccFuse(o2).toString()));

        l.info("----- High belief and slight belief -----");
        o1 = new SubjectiveOpinion(soPP);
        o2 = new SubjectiveOpinion(soP);
        l.info(String.format("Fusing opinions    %s and %s", o1.toString(), o2.toString()));
        l.info(String.format("Cumulative result: %s", o1.cumulativeFuse(o2).toString()));
        l.info(String.format("WBF result:        %s", o1.wbFuse(o2).toString()));
        l.info(String.format("CC result:         %s", o1.ccFuse(o2).toString()));

        o1 = new SubjectiveOpinion(soPD);
        o2 = new SubjectiveOpinion(soNN);
        l.info("----- Dogmatic belief and high disbelief -----");
        l.info(String.format("Fusing opinions    %s and %s", o1.toString(), o2.toString()));
        l.info(String.format("Cumulative result: %s", o1.cumulativeFuse(o2).toString()));
        l.info(String.format("WBF result:        %s", o1.wbFuse(o2).toString()));
        l.info(String.format("CC result:         %s", o1.ccFuse(o2).toString()));

        o1 = new SubjectiveOpinion(soPD);
        o2 = new SubjectiveOpinion(soND);
        l.info("----- Two contradicting dogmatic opinions -----");
        l.info(String.format("Fusing opinions    %s and %s", o1.toString(), o2.toString()));
        l.info(String.format("Cumulative result: %s", o1.cumulativeFuse(o2).toString()));
        l.info(String.format("WBF result:        %s", o1.wbFuse(o2).toString()));
        l.info(String.format("CC result:         %s", o1.ccFuse(o2).toString())); // TODO: this result does not seem right

    }

    private List<Opinion> pastMisbehavior() {
        List<Opinion> opinions = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            opinions.add(new SubjectiveOpinion(soP));
        }
        opinions.add(new SubjectiveOpinion(soNN));
        return opinions;
    }
}
