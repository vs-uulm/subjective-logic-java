package no.uio.subjective_logic.opinion;

import java.util.*;

public class SubjectiveOpinion extends OpinionBase
{
    private static final long serialVersionUID = 1373140807056715988L;
    private static final String TO_STRING_FORMAT = "(belief=%1$1.3f, disbelief=%2$1.3f, uncertainty=%3$1.3f, atomicity=%4$1.3f, e=%5$1.3f, rd=%6$1.3f)";
    public static final SubjectiveOpinion UNCERTAIN = new SubjectiveOpinion(0.0D, 0.0D, 1.0D, 0.5D);

    private double atomicity = 0.5D;

    private double belief = 0.0D;

    private double disbelief = 0.0D;

    private double cachedExpectation = 0.5D;

    private OpinionOperator lastOp = null;

    private boolean recalculate = false;

    private double relativeWeight = 1.0D;

    private double uncertainty = 1.0D;

    private static SubjectiveOpinion abduction(SubjectiveOpinion y, SubjectiveOpinion yTx, SubjectiveOpinion yFx, double baseRateX)
            throws OpinionArithmeticException
    {
        if ((baseRateX < 0.0D) || (baseRateX > 1.0D)) {
            throw new IllegalArgumentException("Base Rate x, must be: 0 <= x <= 1");
        }
        if ((y == null) || (yTx == null) || (yFx == null)) {
            throw new NullPointerException();
        }
        SubjectiveOpinion o;

        if (y.getAtomicity() == 0.0D)
        {
            o = createVacuousOpinion(baseRateX);
        }
        else
        {
            Conditionals conditionals = reverseConditionals(yTx, yFx, baseRateX);
            o = deduction(y, conditionals.getPositive().toSubjectiveOpinion(), conditionals.getNegative().toSubjectiveOpinion());
        }

        o.lastOp = OpinionOperator.Abduce;

        return o;
    }

    public static SubjectiveOpinion add(Collection<? extends Opinion> opinions) throws OpinionArithmeticException
    {
        return sum(opinions);
    }

    private static void adjustExpectation(SubjectiveOpinion x, double expectation)
    {
        synchronized (x)
        {
            x.setDependants();

            double new_e = OpinionBase.constrain(OpinionBase.adjust(expectation));

            if (Math.abs(new_e - x.getExpectation()) <= 1.0E-010D) {
                return;
            }
            if ((new_e == 0.0D) || (new_e == 1.0D))
            {
                x.setBelief(new_e, true);
            }
            else if (new_e < x.getExpectation())
            {
                double new_d = OpinionBase.adjust(((1.0D - new_e) * (x.getBelief() + x.getUncertainty()) - (1.0D - x.getAtomicity()) * x.getUncertainty()) / x.getExpectation());
                double new_u = OpinionBase.adjust(new_e * x.getUncertainty() / x.getExpectation());

                if (new_d + new_u > 1.0D) {
                    new_u = 1.0D - new_d;
                }
                x.setDisbelief(new_d, new_u);
            }
            else
            {
                double divisor = x.getDisbelief() + x.getUncertainty() - x.getAtomicity() * x.getUncertainty();
                double new_b = OpinionBase.adjust((new_e * (x.getDisbelief() + x.getUncertainty()) - x.getAtomicity() * x.getUncertainty()) / divisor);
                double new_u = OpinionBase.adjust((1.0D - new_e) * x.getUncertainty() / divisor);

                if (x.getBelief() + new_u > 1.0D) {
                    new_u = 1.0D - new_b;
                }
                x.setBelief(new_b, new_u);
            }
        }
    }

    public static SubjectiveOpinion and(Collection<? extends Opinion> opinions) throws OpinionArithmeticException
    {
        if (opinions == null) {
            throw new NullPointerException();
        }
        if (opinions.isEmpty()) {
            throw new OpinionArithmeticException("Opinions must not be empty");
        }
        SubjectiveOpinion x = null;

        for (Opinion opinion : opinions) {
            if (opinion != null)
                x = x == null ? new SubjectiveOpinion(opinion) : x.and(opinion);
        }
        return x;
    }

    public static SubjectiveOpinion average(Collection<? extends Opinion> opinions)
    {
        return smoothAverage(opinions);
    }

    private static SubjectiveOpinion coDivision(SubjectiveOpinion x, SubjectiveOpinion y, double r)
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }
        if ((r < 0.0D) || (r > 1.0D)) {
            throw new IllegalArgumentException("Limiting value, r, must be: 0<= r <=1");
        }

        x.setDependants();
        y.setDependants();
        try
        {
            double oBelief = (x.getBelief() - y.getBelief()) / (1.0D - y.getBelief());
            double oAtomicity = (x.getAtomicity() - y.getAtomicity()) / (1.0D - y.getAtomicity());

            double oUncertainty, oDisbelief;
            if (x.getAtomicity() > y.getAtomicity())
            {
                oUncertainty = ( (1.0D - x.getBelief()) / (1.0D - y.getBelief())
                                - (x.getDisbelief() + (1.0D - x.getAtomicity()) * x.getUncertainty()) /(y.getDisbelief() + (1.0D - y.getAtomicity()) * y.getUncertainty())
                                ) * (1.0D - y.getAtomicity()) / (x.getAtomicity() - y.getAtomicity());
                oDisbelief = ( (1.0D - y.getAtomicity()) *
                            (x.getDisbelief() + (1.0D - x.getAtomicity()) * x.getUncertainty()) /
                            (y.getDisbelief() + (1.0D - y.getAtomicity()) * y.getUncertainty())
                            - (1.0D - x.getAtomicity()) *
                            (1.0D - x.getBelief()) /
                            (1.0D - y.getBelief()))
                        / (x.getAtomicity() - y.getAtomicity());
            }
            else
            {
                oDisbelief = r * (1.0D - x.getBelief()) / (1.0D - y.getBelief());
                oUncertainty = (1.0D - r) * (1.0D - x.getBelief()) / (1.0D - y.getBelief());
            }

            SubjectiveOpinion o = new SubjectiveOpinion(oBelief, oDisbelief, oUncertainty, oAtomicity);
            o.checkConsistency();
            o.recalculate = true;

            o.lastOp = OpinionOperator.UnOr;

            return o;
        }
        catch (ArithmeticException e) {
            return null;
        }
    }

    private static SubjectiveOpinion complement(SubjectiveOpinion x)
    {
        if (x == null) {
            throw new NullPointerException();
        }
        synchronized (x)
        {
            SubjectiveOpinion o = new SubjectiveOpinion(x.getDisbelief(), x.getBelief(), x.getUncertainty(), 1.0D - x.getAtomicity());

            o.checkConsistency(true);

            o.lastOp = OpinionOperator.Not;

            return o;
        }
    }

    private static SubjectiveOpinion coMultiplication(SubjectiveOpinion x, SubjectiveOpinion y)
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }

        double r = x.getRelativeWeight(y, OpinionOperator.Or);

        x.setDependants();
        y.setDependants();

        double oBelief = x.getBelief() + y.getBelief() - x.getBelief() * y.getBelief();
        double oAtomicity = x.getAtomicity() + y.getAtomicity() - x.getAtomicity() * y.getAtomicity();

        double oUncertainty;
        if ( oAtomicity != 0.0D) {
            oUncertainty = x.getUncertainty() * y.getUncertainty() + (y.getAtomicity() * x.getDisbelief() * y.getUncertainty() + x.getAtomicity() * x.getUncertainty() * y.getDisbelief()) / oAtomicity;
        } else {
            oUncertainty = x.getUncertainty() * y.getUncertainty() + (x.getDisbelief() * y.getUncertainty() + r * x.getUncertainty() * y.getDisbelief()) / (r + 1.0D);
        }

        double oDisbelief = 1.0D - oBelief - oUncertainty;

        SubjectiveOpinion o = new SubjectiveOpinion(oBelief, oDisbelief, oUncertainty, oAtomicity);
        o.checkConsistency();
        o.recalculate = true;

        o.lastOp = OpinionOperator.Or;
        o.setRelativeWeight(x.relativeWeight + y.relativeWeight);

        return o;
    }

    private static double[] ccFusion_compromise(double[] resX, double[] resY){
        double XResidueBelief = resX[0], XResidueDisbelief = resX[1], Xu = resX[2];
        double YResidueBelief = resY[0], YResidueDisbelief = resY[1], Yu = resY[2];

        //compromise
        double compromiseBelief = XResidueBelief * Yu + YResidueBelief * Xu +
                1*1*XResidueBelief*YResidueBelief + //first sum; y=z=x for x=true
                0*0*XResidueBelief*YResidueBelief + //second sum; y=z=x for x=true
                0; //third sum; x=true means that the intersection of y and z must be non-empty
        double compromiseDisbelief = XResidueDisbelief * Yu + YResidueDisbelief * Xu +
                1*1*XResidueDisbelief*YResidueDisbelief + //first sum; y=z=x for x=false
                0*0*XResidueDisbelief*YResidueDisbelief + //second sum; y=z=x for x=false
                0; //third sum; x=false means that the intersection of y and z must be non-empty

        //this variable contains the belief mass for the entire domain, which is in this case
        // {true, false}. For subjective opinions, belief({true,false})=0 by definition, however
        // the compromise process introduces some belief to the entire domain. This is later used in
        // the normalization process to compute the fused uncertainty, because belief over the entire
        // domain is basically the same thing as uncertainty.
        // residual belief mass over {T,F} is 0, so the computation using eq12.32 is a lot easier,
        // since only the third sum is non-zero:
        double compromiseUncertainty = XResidueBelief * YResidueDisbelief + YResidueBelief * XResidueDisbelief;

        return new double[]{compromiseBelief, compromiseDisbelief, compromiseUncertainty};
    }

    /**
     * This method implements consensus & compromise fusion (CCF) for multiple sources, as discussed in a FUSION 2018 paper by van der Heijden et al. that is currently under review.
     *
     * For more details, refer to Chapter 12 of the Subjective Logic book by Jøsang, specifically Section 12.6, which defines CC fusion for the case of two sources.
     *
     * @param opinions a collection of opinions from different sources.
     * @return a new SubjectiveOpinion that represents the fused evidence.
     * @throws OpinionArithmeticException
     */
    public static SubjectiveOpinion ccCollectionFuse(Collection<SubjectiveOpinion> opinions) throws OpinionArithmeticException
    {
        if(opinions.contains(null) || opinions.size() < 2)
            throw new NullPointerException("Cannot fuse null opinions, or only one opinion was passed");

        double baseRate = -1;
        boolean first = true;
        for (SubjectiveOpinion so: opinions) {
            if(first) {
                baseRate = so.getAtomicity();
                first = false;
            }else if (baseRate != so.getAtomicity()) {
                throw new OpinionArithmeticException("Base rates for CC Fusion must be the same");
            }
        }

        //Step 1: consensus phase
        final double consensusBelief = opinions.stream().mapToDouble(o -> o.getBelief()).min().getAsDouble();
        final double consensusDisbelief = opinions.stream().mapToDouble(o -> o.getDisbelief()).min().getAsDouble();

        final double consensusMass = consensusBelief + consensusDisbelief;

        List<Double> residueBeliefs = new ArrayList<>(opinions.size());
        List<Double> residueDisbeliefs = new ArrayList<>(opinions.size());
        List<Double> uncertainties = new ArrayList<>(opinions.size());
        for (SubjectiveOpinion so : opinions) {
            //note: this max should not be necessary..
            residueBeliefs.add(Math.max(so.getBelief()-consensusBelief,0));
            residueDisbeliefs.add(Math.max(so.getDisbelief()-consensusDisbelief,0));
            uncertainties.add(so.getUncertainty());
        }

        //Step 2: Compromise phase

        double productOfUncertainties = opinions.stream().mapToDouble(o -> o.getUncertainty()).reduce(1.0D, (acc, u) -> acc * u);

        double compromiseBeliefAccumulator = 0;
        double compromiseDisbeliefAccumulator = 0;
        double compromiseXAccumulator = 0; //this is what will later become uncertainty

        //this computation consists of 4 sub-sums that will be added independently.
        for (int i=0; i<opinions.size(); i++) {
            double bResI = residueBeliefs.get(i);
            double dResI = residueDisbeliefs.get(i);
            double uI = uncertainties.get(i);
            double uWithoutI = productOfUncertainties / uI;

            //sub-sum 1:
            compromiseBeliefAccumulator = compromiseBeliefAccumulator + bResI * uWithoutI;
            compromiseDisbeliefAccumulator = compromiseDisbeliefAccumulator + dResI * uWithoutI;
            //note: compromiseXAccumulator is unchanged, since b^{ResI}_X() of the entire domain is 0
        }
        //sub-sums 2,3,4 are all related to different permutations of possible values
        for(List<Domain> permutation : tabulateOptions(opinions.size())){
            Domain intersection = permutation.stream().reduce(Domain.DOMAIN, (acc, p) -> acc.intersect(p));
            Domain union = permutation.stream().reduce(Domain.NIL, (acc, p) -> acc.union(p));

            //sub-sum 2: intersection of elements in permutation is x
            if(intersection.equals(Domain.TRUE)) {
                // --> add to belief
                double prod = 1;
                if(permutation.contains(Domain.DOMAIN))
                    prod = 0;
                else
                    for (int j=0; j<permutation.size();j++)
                        switch (permutation.get(j)){
                            case DOMAIN:
                                prod = 0; // multiplication by 0
                                break;
                            case TRUE:
                                prod = prod * residueBeliefs.get(j) * 1;
                                break;
                        }
                compromiseBeliefAccumulator = compromiseBeliefAccumulator + prod;
            } else if (intersection.equals(Domain.FALSE)) {
                // --> add to disbelief
                double prod = 1;
                if(permutation.contains(Domain.DOMAIN))
                    prod = 0;
                else
                    for (int j=0; j<permutation.size();j++)
                        switch (permutation.get(j)){
                            case DOMAIN:
                                prod = 0; // multiplication by 0
                                break;
                            case FALSE:
                                prod = prod * residueDisbeliefs.get(j) * 1;
                                break;
                        }
                compromiseDisbeliefAccumulator = compromiseDisbeliefAccumulator + prod;
            }

            switch (union){
                case DOMAIN:
                    if(!intersection.equals(Domain.NIL)) {
                        //sub-sum 3: union of elements in permutation is x, and intersection of elements in permutation is not NIL

                        //Note: this is always zero for binary domains, as explained by the following:
                        //double prod = 1;
                        //for (int j=0; j<permutation.size(); j++) {
                        //    switch (permutation.get(j)) {
                        //        case NIL:
                        //        case DOMAIN:
                        //            prod = 0; //because residue belief over NIL/DOMAIN is zero here
                        //            break;
                        //        case TRUE:
                        //        case FALSE:
                        //            prod = 0; //because 1-a(y|x) is zero here, since a(y|x)=1 when x=y, and this must occur, since a(x|!x) occurring implies the intersection is NIL
                        //            break;
                        //    }
                        //}

                    }
                    else {
                        //sub-sum 4: union of elements in permutation is x, and intersection of elements in permutation is NIL
                        double prod = 1;
                        for (int j=0; j<permutation.size(); j++) {
                            switch (permutation.get(j)) {
                                case NIL:
                                case DOMAIN:
                                    prod = 0; //because residue belief over NIL/DOMAIN is zero here
                                    break;
                                case TRUE:
                                    prod = prod * residueBeliefs.get(j);
                                    break;
                                case FALSE:
                                    prod = prod * residueDisbeliefs.get(j);
                                    break;
                            }
                        }
                        compromiseXAccumulator = compromiseXAccumulator + prod;
                    }
                    break;
                case NIL:
                    //union of NIL means we have nothing to add
                    //sub-sum 3: union of elements in permutation is x, and intersection of elements in permutation is not NIL
                    //sub-sum 4: union of elements in permutation is x, and intersection of elements in permutation is NIL
                    break;
                case TRUE:
                    //sub-sum 3: this is always zero for TRUE and FALSE, since 1-a(y_i|y_j)=0 in binary domains, where the relative base rate is either 1 if the union is x

                    //sub-sum 4: union of elements in permutation is x, and intersection of elements in permutation is NIL
                    if(intersection.equals(Domain.NIL)){
                        //union is true, intersection is nil --> compute the product
                        double prod = 1;
                        for (int j=0; j<permutation.size(); j++) {
                            switch (permutation.get(j)) { //other cases will not occur
                                case TRUE:
                                    prod = prod * residueBeliefs.get(j);
                                    break;
                                case FALSE:
                                    prod = prod * residueDisbeliefs.get(j);
                                    break;
                                case NIL:
                                    prod = 0;
                                    break;
                                default:
                                    throw new RuntimeException();
                            }
                        }
                        compromiseBeliefAccumulator = compromiseBeliefAccumulator + prod;
                    }
                    break;
                case FALSE:
                    //sub-sum 3: this is always zero for TRUE and FALSE, since 1-a(y_i|y_j)=0 in binary domains, where the relative base rate is either 1 if the union is x
                    //sub-sum 4: union of elements in permutation is x, and intersection of elements in permutation is NIL
                    if(intersection.equals(Domain.NIL)){
                        //union is true, intersection is nil --> compute the product
                        double prod = 1;
                        for (int j=0; j<permutation.size(); j++) {
                            switch (permutation.get(j)) { //other cases will not occur
                                case TRUE:
                                    prod = prod * residueBeliefs.get(j);
                                    break;
                                case FALSE:
                                    prod = prod * residueDisbeliefs.get(j);
                                    break;
                                case NIL:
                                    prod = 0;
                                    break;
                                default:
                                    throw new RuntimeException();
                            }
                        }
                        compromiseDisbeliefAccumulator= compromiseDisbeliefAccumulator + prod;
                    }
                    break;
                default:
                    break;

            }
        }

        double compromiseBelief = compromiseBeliefAccumulator;
        double compromiseDisbelief = compromiseDisbeliefAccumulator;
        double compromiseUncertainty = compromiseXAccumulator;

        double preliminaryUncertainty = productOfUncertainties;
        double compromiseMass = compromiseBelief + compromiseDisbelief + compromiseUncertainty;

        //Step 3: Normalization phase
        double normalizationFactor = (1-consensusMass-preliminaryUncertainty)/(compromiseMass);

        double fusedUncertainty = preliminaryUncertainty + normalizationFactor* compromiseUncertainty;
        //compromiseUncertainty = 0; --> but this variable is never used again anyway.

        double fusedBelief = consensusBelief + normalizationFactor * compromiseBelief;
        double fusedDisbelief = consensusDisbelief + normalizationFactor * compromiseDisbelief;

        SubjectiveOpinion res = new SubjectiveOpinion(fusedBelief, fusedDisbelief, fusedUncertainty, baseRate);
        res.checkConsistency(true);
        return res;
    }

    public enum Domain {
        NIL, TRUE, FALSE, DOMAIN;

        public Domain intersect(Domain d){
            switch(this){
                case NIL:
                    return NIL;
                case TRUE:
                    switch (d){
                        case NIL:
                        case FALSE:
                            return NIL;
                        case TRUE:
                        case DOMAIN:
                            return TRUE;
                        default:
                            throw new RuntimeException("unidentified domain");
                    }
                case FALSE:
                    switch (d){
                        case NIL:
                        case TRUE:
                            return NIL;
                        case FALSE:
                        case DOMAIN:
                            return FALSE;
                        default:
                            throw new RuntimeException("unidentified domain");
                    }
                case DOMAIN:
                    return d;
                default:
                    throw new RuntimeException("unidentified domain");
            }
        }

        public Domain union(Domain d){
            switch (this) {
                case DOMAIN:
                    return DOMAIN;
                case TRUE:
                    switch (d){
                        case TRUE:
                        case NIL:
                            return TRUE;
                        case FALSE:
                        case DOMAIN:
                            return DOMAIN;
                        default:
                            throw new RuntimeException("unidentified domain");
                    }
                case FALSE:
                    switch (d){
                        case FALSE:
                        case NIL:
                            return FALSE;
                        case TRUE:
                        case DOMAIN:
                            return DOMAIN;
                        default:
                            throw new RuntimeException("unidentified domain");
                    }
                case NIL:
                    return d;
                default:
                    throw new RuntimeException("unidentified domain");
            }
        }
    }

    private static Set<List<Domain>> tabulateOptions(int size) {
        if (size == 1) {
            Set<List<Domain>> result = new HashSet<List<Domain>>();
            for(Domain item : Domain.values()){
                List l = new ArrayList<Domain>();
                l.add(item);
                result.add(l);
            }
            return result;
        }
        Set<List<Domain>> result = new HashSet();
        for (List<Domain> tuple : tabulateOptions(size - 1)) {
            for (Domain d : Domain.values()) {
                List newList = new ArrayList(tuple);
                newList.add(d);
                result.add(newList);
            }
        }
        return result;
    }

    //see Section 12.6 of the Subjective Logic book: 10.1007/978-3-319-42337-1_12
    private static SubjectiveOpinion ccFusion(SubjectiveOpinion x, SubjectiveOpinion y) throws OpinionArithmeticException
    {
        if(x == null || y == null)
            throw new NullPointerException("Cannot fuse null opinions");

        if(x.getAtomicity() != y.getAtomicity())
            throw new OpinionArithmeticException("Base rates for CC Fusion must be the same");

        //consensus
        double consensusBelief = Double.min(x.getBelief(), y.getBelief());
        double consensusDisbelief = Double.min(x.getDisbelief(), y.getDisbelief());
        double consensusMass = consensusBelief + consensusDisbelief;
        //note: residue belief must be at least zero, by the definition of belief mass (see chapter 2), although this is not explicitly stated in equation 12.31
        double XResidueBelief = Math.max(x.getBelief() - consensusBelief, 0);
        double YResidueBelief = Math.max(y.getBelief() - consensusBelief, 0);
        double XResidueDisbelief = Math.max(x.getDisbelief() - consensusDisbelief, 0);
        double YResidueDisbelief = Math.max(y.getDisbelief() - consensusDisbelief, 0);

        //compromise
        double compromiseBelief = XResidueBelief * y.getUncertainty() + YResidueBelief * x.getUncertainty() +
                1*1*XResidueBelief*YResidueBelief + //first sum; y=z=x for x=true
                0*0*XResidueBelief*YResidueBelief + //second sum; y=z=x for x=true
                0; //third sum; x=true means that the intersection of y and z must be non-empty
        double compromiseDisbelief = XResidueDisbelief * y.getUncertainty() + YResidueDisbelief * x.getUncertainty() +
                1*1*XResidueDisbelief*YResidueDisbelief + //first sum; y=z=x for x=false
                0*0*XResidueDisbelief*YResidueDisbelief + //second sum; y=z=x for x=false
                0; //third sum; x=false means that the intersection of y and z must be non-empty

        //this variable contains the belief mass for the entire domain, which is in this case
        // {true, false}. For subjective opinions, belief({true,false})=0 by definition, however
        // the compromise process introduces some belief to the entire domain. This is later used in
        // the normalization process to compute the fused uncertainty, because belief over the entire
        // domain is basically the same thing as uncertainty.
        // residual belief mass over {T,F} is 0, so the computation using eq12.32 is a lot easier,
        // since only the third sum is non-zero:
        double compromiseUncertainty = XResidueBelief * YResidueDisbelief + YResidueBelief * XResidueDisbelief;

        double preliminaryUncertainty = x.getUncertainty() * y.getUncertainty();
        double compromiseMass = compromiseBelief + compromiseDisbelief + compromiseUncertainty;

        // FIXME: compromise mass is 0 for equal arguments and two contradicting dogmatic opinions
        double normalizationFactor = (1-consensusMass-preliminaryUncertainty)/(compromiseMass);

        double fusedUncertainty = preliminaryUncertainty + normalizationFactor* compromiseUncertainty;

        //merger
        double fusedBelief = consensusBelief + normalizationFactor * compromiseBelief;
        double fusedDisbelief = consensusDisbelief + normalizationFactor * compromiseDisbelief;

        SubjectiveOpinion res = new SubjectiveOpinion(fusedBelief, fusedDisbelief, fusedUncertainty, x.getAtomicity());
        res.checkConsistency(true);
        return res;
    }

    /**
     * Weighted Belief Fusion (See Josang SL book pp. 231)
     * @details "WBF is commutative, idempotent and has the vacuous opinion as neutral element.
     * Semi-associativity requires that three or more argument must first be combined together in the same operation."
     *
     * "WBF produces averaging beliefs weighted by the opinion confidences.
     * WBF is suitable for fusing (expert) agent opinions in situations where the source agent’s confidence should determine the opinion weight in the fusion process."
     * @param x Opinion of source A
     * @param y Opinion of source B
     * @return WBF fused opinion
     * @throws OpinionArithmeticException
     * @deprecated
     */
    private static SubjectiveOpinion wbFusion(SubjectiveOpinion x, SubjectiveOpinion y) throws OpinionArithmeticException {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }

        double b = 0.0D;
        double u = 0.0D;
        double a = 0.5D;

        // case 1: (u_A != 0 || u_B != 0) && (u_A != 1 || u_B != 1)
        if ((x.getUncertainty() != 0 || y.getUncertainty() != 0) && (x.getUncertainty() != 1 || y.getUncertainty() != 1)) {
            b = (x.getBelief() *(1- x.getUncertainty())* y.getUncertainty() + y.getBelief() *(1- y.getUncertainty())* x.getUncertainty()) / (x.getUncertainty() + y.getUncertainty() - 2* x.getUncertainty() * y.getUncertainty());
            u = ((2- x.getUncertainty() - y.getUncertainty()) * x.getUncertainty() * y.getUncertainty()) / (x.getUncertainty() + y.getUncertainty() - 2* x.getUncertainty() * y.getUncertainty());
            a = (x.getAtomicity() *(1- x.getUncertainty()) + y.getAtomicity() *(1- y.getUncertainty())) / (2- x.getUncertainty() - y.getUncertainty());
        }

        // case 2: (u_A == 0 && u_B == 0)
        if (x.getUncertainty() == 0 && y.getUncertainty() == 0) {
            // In case of dogmatic arguments assume the limits in Eq.(12.24) to be γ_X^A = γ_X^B = 0.5 (p. 232)
            double gammaA = 0.5;
            double gammaB = 0.5;

            b = gammaA* x.getBelief() + gammaB* y.getBelief();
            u = 0.0D;
            a = gammaA* x.getAtomicity() + gammaB* y.getAtomicity();
        }

        // case 3: (u_A == 1 && u_B == 1)
        if (x.getUncertainty() == 1 && y.getUncertainty() == 1) {
            b = 0.0D;
            u = 1.0D;
            a = (x.getAtomicity() + y.getAtomicity()) / 2;
        }

        SubjectiveOpinion res = new SubjectiveOpinion(b, u);
        a = OpinionBase.adjust(OpinionBase.constrain(a));
        res.setAtomicity(a);
        res.checkConsistency(true);
        return res;
    }

    private static SubjectiveOpinion cumulativeFusion(SubjectiveOpinion x, SubjectiveOpinion y) throws OpinionArithmeticException
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }

        double totalWeight = x.getRelativeWeight() + y.getRelativeWeight();
        double weightX = x.getRelativeWeight(), weightY = y.getRelativeWeight();
        double k = x.getUncertainty() + y.getUncertainty() - x.getUncertainty() * y.getUncertainty();
        double l = x.getUncertainty() + y.getUncertainty() - 2.0D * x.getUncertainty() * y.getUncertainty();

        double resultBelief, resultDisbelief, resultUncertainty, resultAtomicity;

        if (k != 0.0D)
        {
            if (l != 0.0D)
            {
                resultBelief = (x.getBelief() * y.getUncertainty() + y.getBelief() * x.getUncertainty()) / k;
                resultDisbelief = (x.getDisbelief() * y.getUncertainty() + y.getDisbelief() * x.getUncertainty()) / k;
                resultUncertainty = x.getUncertainty() * y.getUncertainty() / k;
                resultAtomicity = (y.getAtomicity() * x.getUncertainty() + x.getAtomicity() * y.getUncertainty() - (x.getAtomicity() + y.getAtomicity()) * x.getUncertainty() * y.getUncertainty()) / l;
            }
            else if (Math.abs(x.getAtomicity() - y.getAtomicity()) <= 1.0E-010D)
            {
                resultBelief = 0.0D;
                resultDisbelief = 0.0D;
                resultUncertainty = 1.0D;
                resultAtomicity = x.getAtomicity();
            }
            else
            {
                throw new OpinionArithmeticException("Relative atomicities are not equal");
            }

        }
        else
        {
            resultBelief = (x.getBelief() * weightX + y.getBelief() * weightY)/totalWeight;
            resultDisbelief = (x.getDisbelief() * weightX + y.getDisbelief() * weightY)/totalWeight;
            resultUncertainty = 0.0D;
            resultAtomicity = (x.getAtomicity() * weightX + y.getAtomicity() * weightY)/totalWeight;
        }

        SubjectiveOpinion o = new SubjectiveOpinion(resultBelief, resultDisbelief, resultUncertainty, resultAtomicity);

        o.checkConsistency(true);

        o.lastOp = OpinionOperator.Fuse;
        o.setRelativeWeight(totalWeight); //relative weight represents how many opinions were fused in

        return o;
    }


    /**
     * This method implements cumulative belief fusion (CBF) for multiple sources, as discussed in the corrected
     * version of <a href="https://folk.uio.no/josang/papers/JWZ2017-FUSION.pdf">a FUSION 2017 paper by Jøsang et al.</a>
     *
     * As discussed in the book, cumulative fusion is useful in scenarios where opinions from multiple sources is combined, where each source is relying on independent (in the statistical sense) evidence.
     * For more details, refer to Chapter 12 of the Subjective Logic book by Jøsang, specifically Section 12.3, which defines cumulative fusion.
     *
     * @param opinions a collection of opinions from different sources.
     * @return a new SubjectiveOpinion that represents the fused evidence based on evidence accumulation.
     * @throws OpinionArithmeticException
     */
    public static SubjectiveOpinion cumulativeCollectionFuse(Collection<SubjectiveOpinion> opinions) throws OpinionArithmeticException
    {
        //handle edge cases
        if (opinions == null) {
            throw new NullPointerException();
        }
        if (opinions.isEmpty()) {
            throw new OpinionArithmeticException("Opinions must not be empty");
        }
        if (opinions.size() == 1){
            return new SubjectiveOpinion(opinions.iterator().next());
        }

        //fusion as defined by Jøsang
        double resultBelief, resultDisbelief, resultUncertainty, resultRelativeWeight = 0, resultAtomicity = -1;

        Collection<SubjectiveOpinion> dogmatic = new ArrayList<>(opinions.size());
        Iterator<SubjectiveOpinion> it = opinions.iterator();
        boolean first = true;
        while(it.hasNext()) {
            SubjectiveOpinion o = it.next();
            if(first) {
                resultAtomicity = o.getAtomicity();
                first = false;
            }
            //dogmatic iff uncertainty is zero.
            if (o.getUncertainty() == 0)
                dogmatic.add(o);
        }

        if(dogmatic.isEmpty()){
            //there are no dogmatic opinions -- case I/Eq16 of 10.23919/ICIF.2017.8009820
            double productOfUncertainties = opinions.stream().mapToDouble(o -> o.getUncertainty()).reduce(1.0D, (acc, u) -> acc * u);

            double numerator = 0.0D;
            double beliefAccumulator = 0.0D;
            double disbeliefAccumulator = 0.0D;

            //this computes the top and bottom sums in Eq16, but ignores the - (N-1) * productOfUncertainties in the numerator (see below)
            for (SubjectiveOpinion o : opinions) {
                //productWithoutO = product of uncertainties without o's uncertainty
                //this can be rewritten:
                //prod {C_j != C } u^{C_j} = (u^C)^-1 * prod{C_j} u^{C_j} = 1/(u^C) * prod{C_j} u^{C_j}
                //so instead of n-1 multiplications, we only need a division
                double productWithoutO = productOfUncertainties / o.getUncertainty();

                beliefAccumulator = beliefAccumulator + productWithoutO * o.getBelief();
                disbeliefAccumulator = disbeliefAccumulator + productWithoutO * o.getDisbelief();
                numerator = numerator + productWithoutO;
            }

            //this completes the numerator:
            numerator = numerator - (opinions.size() - 1) * productOfUncertainties;

            resultBelief = beliefAccumulator / numerator;
            resultDisbelief = disbeliefAccumulator / numerator;
            resultUncertainty = productOfUncertainties / numerator;

            resultRelativeWeight = 0;
        } else {
            //at least 1 dogmatic opinion
            //note: this computation assumes that the relative weight represents how many opinions have been fused into that opinion.
            //for a normal multi-source fusion operation, this should be 1, in which case the gamma's in Eq17 are 1/N as noted in the text (i.e., all opinions count equally)
            //however, this formulation also allows partial fusion beforehand, by "remembering" the amount of dogmatic (!) opinions in o.relativeWeight.

            double totalWeight = dogmatic.stream().mapToDouble( o -> o.getRelativeWeight()).sum();

            resultBelief = dogmatic.stream().mapToDouble(o-> o.getRelativeWeight()/totalWeight * (o).getBelief()).sum();

            resultDisbelief = dogmatic.stream().mapToDouble(o-> o.getRelativeWeight()/totalWeight * (o).getDisbelief()).sum();

            resultUncertainty = 0.0D;

            resultRelativeWeight = totalWeight;
        }

        SubjectiveOpinion result = new SubjectiveOpinion(resultBelief, resultDisbelief, resultUncertainty, resultAtomicity);
        result.setRelativeWeight(resultRelativeWeight);
        result.lastOp = OpinionOperator.Fuse;
        return result;
    }

    /**
     * This method implements weighted belief fusion (WBF) for multiple sources, as discussed in a FUSION 2018 paper by van der Heijden et al. that is currently under review.
     *
     * As discussed in the book, WBF is intended to represent the confidence-weighted averaging of evidence.
     * For more details, refer to Chapter 12 of the Subjective Logic book by Jøsang, specifically Section 12.5, which defines weighted belief fusion.
     *
     * @param opinions a collection of opinions from different sources.
     * @return a new SubjectiveOpinion that represents the fused evidence based on confidence-weighted averaging of evidence.
     * @throws OpinionArithmeticException
     */
    public static SubjectiveOpinion weightedCollectionFuse(Collection<SubjectiveOpinion> opinions) throws OpinionArithmeticException
    {
        if (opinions == null) {
            throw new NullPointerException();
        }
        if (opinions.isEmpty()) {
            throw new OpinionArithmeticException("Opinions must not be empty");
        }
        if (opinions.size() == 1) {
            return new SubjectiveOpinion(opinions.iterator().next());
        }

        double resultBelief, resultDisbelief, resultUncertainty, resultRelativeWeight = 0, resultAtomicity;

        Collection<SubjectiveOpinion> dogmatic = new ArrayList<>(opinions.size());
        Iterator<SubjectiveOpinion> it = opinions.iterator();
        while(it.hasNext()) {
            SubjectiveOpinion o = it.next();
            //dogmatic iff uncertainty is zero.
            if (o.getUncertainty() == 0)
                dogmatic.add(o);
        }

        if (dogmatic.isEmpty() && opinions.stream().anyMatch(o -> o.getCertainty() > 0)) {
            //Case 1: no dogmatic opinions, at least one non-vacuous opinion
            double productOfUncertainties = opinions.stream().mapToDouble(o -> o.getUncertainty()).reduce(1.0D, (acc, u) -> acc * u);
            double sumOfUncertainties = opinions.stream().mapToDouble(o -> o.getUncertainty()).sum();

            double numerator = 0.0D;
            double beliefAccumulator = 0.0D;
            double disbeliefAccumulator = 0.0D;
            double atomicityAccumulator = 0.0D;

            for (SubjectiveOpinion o : opinions) {
                //prod = product of uncertainties without o's uncertainty
                double prod = productOfUncertainties / o.getUncertainty();

                //recall certainty = 1 - uncertainty
                beliefAccumulator = beliefAccumulator + prod * o.getBelief() * o.getCertainty();
                disbeliefAccumulator = disbeliefAccumulator + prod * o.getDisbelief() * o.getCertainty();
                atomicityAccumulator = atomicityAccumulator + o.getAtomicity() * o.getCertainty();
                numerator = numerator + prod;
            }

            numerator = numerator - opinions.size() * productOfUncertainties;

            resultBelief = beliefAccumulator / numerator;
            resultDisbelief = disbeliefAccumulator / numerator;
            resultUncertainty = (opinions.size() - sumOfUncertainties) * productOfUncertainties / numerator;
            resultAtomicity = atomicityAccumulator / (opinions.size() - sumOfUncertainties);
        } else if (opinions.stream().allMatch(o -> o.getUncertainty() == 1)) {
            //Case 3 -- everything is vacuous
            resultBelief = 0;
            resultDisbelief = 0;
            resultUncertainty = 1;
            boolean first = true;

            //all confidences are zero, so the weight for each opinion is the same -> use a plain average for the resultAtomicity
            resultAtomicity = 0;
            for (Opinion o : opinions) {
                if (first) {
                    resultAtomicity = resultAtomicity + o.getAtomicity();
                    first = false;
                }
            }
            resultAtomicity = resultAtomicity / ((double)opinions.size());

        } else {
            //Case 2 -- dogmatic opinions are involved
            double totalWeight = dogmatic.stream().mapToDouble( o -> o.getRelativeWeight()).sum();

            resultBelief = dogmatic.stream().mapToDouble(o-> o.getRelativeWeight()/totalWeight * o.getBelief()).sum();

            resultDisbelief = dogmatic.stream().mapToDouble(o-> o.getRelativeWeight()/totalWeight * o.getDisbelief()).sum();

            resultUncertainty = 0.0D;

            resultRelativeWeight = totalWeight;

            //note: the for loop below will always set resultAtomicity correctly.
            resultAtomicity = -1;
            boolean first = true;
            for(Opinion o : opinions){
                if (first) {
                    resultAtomicity = o.getAtomicity();
                    first = false;
                }
            }
        }

        SubjectiveOpinion result = new SubjectiveOpinion(resultBelief, resultDisbelief, resultUncertainty, resultAtomicity);

        result.setRelativeWeight(resultRelativeWeight);
        result.lastOp = OpinionOperator.Fuse;
        return result;
    }

    /**
     * Dogmatic opinions are opinions with complete certainty (i.e., uncertainty = 0).
     *
     * @param expectation
     * @param atomicity a-priori probability
     * @return
     */
    public static SubjectiveOpinion createDogmaticOpinion(double expectation, double atomicity)
    {
        if ((expectation < 0.0D) || (expectation > 1.0D) || (atomicity < 0.0D) || (atomicity > 1.0D)) {
            throw new IllegalArgumentException("Expectation e, must be 0 <= e <= 1");
        }
        return new SubjectiveOpinion(expectation, 1.0D - expectation, 0.0D, atomicity);
    }

    /**
     * Vacuous opinions have an uncertainty of 1.
     * @param expectation
     * @return
     */
    public static SubjectiveOpinion createVacuousOpinion(double expectation)
    {
        if ((expectation < 0.0D) || (expectation > 1.0D)) {
            throw new IllegalArgumentException("Expectation e, must be 0 <= e <= 1");
        }
        return new SubjectiveOpinion(0.0D, 0.0D, 1.0D, expectation);
    }

    private static SubjectiveOpinion deduction(SubjectiveOpinion x, SubjectiveOpinion yTx, SubjectiveOpinion yFx)
            throws OpinionArithmeticException
    {
        if ((x == null) || (yTx == null) || (yFx == null)) {
            throw new NullPointerException();
        }
        if (Math.abs(yTx.getAtomicity() - yFx.getAtomicity()) > 1.0E-010D) {
            throw new OpinionArithmeticException("The atomicities of both sub-conditionals must be equal");
        }
        x.setDependants();

        final double IBelief, IDisbelief, IUncertainty, IAtomicity;
        IAtomicity = yTx.getAtomicity();
        IBelief = x.getBelief() * yTx.getBelief() + x.getDisbelief() * yFx.getBelief() + x.getUncertainty() * (yTx.getBelief() * x.getAtomicity() + yFx.getBelief() * (1.0D - x.getAtomicity()));
        IDisbelief = x.getBelief() * yTx.getDisbelief() + x.getDisbelief() * yFx.getDisbelief() + x.getUncertainty() * (yTx.getDisbelief() * x.getAtomicity() + yFx.getDisbelief() * (1.0D - x.getAtomicity()));
        IUncertainty = x.getBelief() * yTx.getUncertainty() + x.getDisbelief() * yFx.getUncertainty() + x.getUncertainty() * (yTx.getUncertainty() * x.getAtomicity() + yFx.getUncertainty() * (1.0D - x.getAtomicity()));

        final SubjectiveOpinion I = new SubjectiveOpinion(IBelief, IDisbelief, IUncertainty, IAtomicity);

        I.setDependants(true);
        SubjectiveOpinion y;
        if (((yTx.getBelief() >= yFx.getBelief()) && (yTx.getDisbelief() >= yFx.getDisbelief())) || ((yTx.getBelief() <= yFx.getBelief()) && (yTx.getDisbelief() <= yFx.getDisbelief())))
        {
            y = I;
        }
        else
        {
            double expec = yTx.getBelief() * x.getAtomicity() + yFx.getBelief() * (1.0D - x.getAtomicity()) + yTx.getAtomicity() * (yTx.getUncertainty() * x.getAtomicity() + yFx.getUncertainty() * (1.0D - x.getAtomicity()));

            boolean case_II = (yTx.getBelief() > yFx.getBelief()) && (yTx.getDisbelief() < yFx.getDisbelief());

            boolean case_1 = x.getExpectation() <= x.getAtomicity();
            double k;
            if (case_II)
            {
                boolean case_A = expec <= yFx.getBelief() + yTx.getAtomicity() * (1.0D - yFx.getBelief() - yTx.getDisbelief());
                if (case_A)
                {
                    if (case_1)
                    {
                        double divisor;
                        if ((divisor = x.getExpectation() * yTx.getAtomicity()) > 0.0D)
                            k = x.getAtomicity() * x.getUncertainty() * (I.getBelief() - yFx.getBelief()) / divisor;
                        else
                            k = I.getBelief() - yFx.getBelief();
                    }
                    else
                    {
                        double divisor;
                        if ((divisor = (x.getDisbelief() + (1.0D - x.getAtomicity()) * x.getUncertainty()) * yTx.getAtomicity() * (yFx.getDisbelief() - yTx.getDisbelief())) > 0.0D)
                            k = x.getAtomicity() * x.getUncertainty() * (I.getDisbelief() - yTx.getDisbelief()) * (yTx.getBelief() - yFx.getBelief()) / divisor;
                        else
                            k = (I.getDisbelief() - yTx.getDisbelief()) * (yTx.getBelief() - yFx.getBelief());
                    }
                }
                else
                {
                    if (case_1)
                    {
                        double divisor;
                        if ((divisor = x.getExpectation() * (1.0D - yTx.getAtomicity()) * (yTx.getBelief() - yFx.getBelief())) > 0.0D)
                            k = (1.0D - x.getAtomicity()) * x.getUncertainty() * (I.getBelief() - yFx.getBelief()) * (yFx.getDisbelief() - yTx.getDisbelief()) / divisor;
                        else
                            k = (I.getBelief() - yFx.getBelief()) * (yFx.getDisbelief() - yTx.getDisbelief());
                    }
                    else
                    {
                        double divisor;
                        if ((divisor = (x.getDisbelief() + (1.0D - x.getAtomicity()) * x.getUncertainty()) * (1.0D - yTx.getAtomicity())) > 0.0D)
                            k = (1.0D - x.getAtomicity()) * x.getUncertainty() * (I.getDisbelief() - yTx.getDisbelief()) / divisor;
                        else {
                            k = I.getDisbelief() - yTx.getDisbelief();
                        }
                    }
                }
            }
            else
            {
                boolean case_A = expec <= yTx.getBelief() + yTx.getAtomicity() * (1.0D - yTx.getBelief() - yFx.getDisbelief());
                if (case_A)
                {
                    if (case_1)
                    {
                        double divisor;
                        if ((divisor = x.getExpectation() * yTx.getAtomicity() * (yTx.getDisbelief() - yFx.getDisbelief())) > 0.0D)
                            k = (1.0D - x.getAtomicity()) * x.getUncertainty() * (I.getDisbelief() - yFx.getDisbelief()) * (yFx.getBelief() - yTx.getBelief()) / divisor;
                        else
                            k = (I.getDisbelief() - yFx.getDisbelief()) * (yFx.getBelief() - yTx.getBelief());
                    }
                    else
                    {
                        double divisor;
                        if ((divisor = (x.getDisbelief() + (1.0D - x.getAtomicity()) * x.getUncertainty()) * yTx.getAtomicity()) > 0.0D)
                            k = (1.0D - x.getAtomicity()) * x.getUncertainty() * (I.getBelief() - yTx.getBelief()) / divisor;
                        else
                            k = I.getBelief() - yTx.getBelief();
                    }
                }
                else
                {
                    if (case_1)
                    {
                        double divisor;
                        if ((divisor = x.getExpectation() * (1.0D - yTx.getAtomicity())) > 0.0D)
                            k = x.getAtomicity() * x.getUncertainty() * (I.getDisbelief() - yFx.getDisbelief()) / divisor;
                        else
                            k = I.getDisbelief() - yFx.getDisbelief();
                    }
                    else
                    {
                        double divisor;
                        if ((divisor = (x.getDisbelief() + (1.0D - x.getAtomicity()) * x.getUncertainty()) * (1.0D - yTx.getAtomicity()) * (yFx.getBelief() - yTx.getBelief())) > 0.0D)
                            k = x.getAtomicity() * x.getUncertainty() * (I.getBelief() - yTx.getBelief()) * (yTx.getDisbelief() - yFx.getDisbelief()) / divisor;
                        else {
                            k = (I.getBelief() - yTx.getBelief()) * (yTx.getDisbelief() - yFx.getDisbelief());
                        }
                    }
                }
            }
            double yAtomicity, yBelief, yDisbelief, yUncertainty;
            yAtomicity = yTx.getAtomicity();

            yBelief = OpinionBase.adjust(I.getBelief() - k * yAtomicity);
            yDisbelief = OpinionBase.adjust(I.getDisbelief() - k * (1.0D - yAtomicity));
            yUncertainty = OpinionBase.adjust(I.getUncertainty() + k);

            y = new SubjectiveOpinion(yAtomicity, yBelief, yDisbelief, yUncertainty);
            y.checkConsistency(true);
        }

        y.lastOp = OpinionOperator.Deduce;

        return y;
    }

    private static SubjectiveOpinion clippedOpinion(double b, double u, double a) throws OpinionArithmeticException
    {
        if ((a < 0.0D) || (a > 1.0D)) {
            throw new OpinionArithmeticException("Atomicity out of range, atomicity: 0 <= atomicity <= 1");
        }
        //SubjectiveOpinion o = new SubjectiveOpinion(a);
        double resBelief, resDisbelief, resUncertainty, resAtomicity = a;
        double e = OpinionBase.constrain(b + a * u);
        double sum = u + b;

        if (u < 0.0D)
        {
            resUncertainty = 0.0D;
            resBelief = e;
            resDisbelief = 1.0D - e;
        }
        else if (b < 0.0D)
        {
            resBelief = 0.0D;
            resUncertainty = e/a;
            resDisbelief = 1 - resUncertainty;
        }
        else if (sum > 1.0D)
        {
            if (a == 1.0D)
            {
                resDisbelief = 0.0D;
                resBelief = b/sum;
                resUncertainty = u/sum;
            }
            else
            {
                resDisbelief = 0.0D;
                if (a < 1.0D)
                    resBelief = (e - a)/ (1.0D - a);
                else
                    resBelief = e;
                resUncertainty = 1.0D - resBelief;
            }
        }
        else
        {
            resBelief = b;
            resUncertainty = u;
            resDisbelief = 1.0D - b - u;
        }

        SubjectiveOpinion o = new SubjectiveOpinion(resBelief, resDisbelief, resUncertainty, resAtomicity);
        o.adjust();

        o.checkConsistency();
        o.recalculate = true;

        return o;
    }

    private static SubjectiveOpinion division(SubjectiveOpinion x, SubjectiveOpinion y) throws OpinionArithmeticException
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }
        if (y.getAtomicity() == 0.0D) {
            throw new OpinionArithmeticException("Atomicity of divisor is zero");
        }

        x.setDependants();
        y.setDependants();

        if (y.getExpectation() - x.getExpectation() < -1.0E-010D) {
            throw new OpinionArithmeticException("Expectation of divisor cannot be less than of numerator");
        }
        try
        {
            double a = x.getAtomicity() / y.getAtomicity();
            SubjectiveOpinion o;
            if (x.getExpectation() == 0.0D)
            {
                o = new SubjectiveOpinion(0.0D, 1.0D, 0.0D, a);
            }
            else
            {
                if (a == 1.0D)
                {
                    o = new SubjectiveOpinion(1.0D, 0.0D, 0.0D, a);
                }
                else
                {
                    double e = x.getExpectation() / y.getExpectation();

                    double d = OpinionBase.constrain((x.getDisbelief() - y.getDisbelief()) / (1.0D - y.getDisbelief()));
                    double u = (1.0D - d - e) / (1.0D - a);
                    double b = 1.0D - d - u;

                    o = clippedOpinion(b, u, a);
                }
            }
            o.checkConsistency();
            o.recalculate = true;

            o.lastOp = OpinionOperator.UnAnd;

            return o;
        }
        catch (ArithmeticException ae)
        {
            throw new OpinionArithmeticException(ae.getMessage());
        }
    }

    private static SubjectiveOpinion erosion(SubjectiveOpinion x, double factor)
    {
        if (x == null) {
            throw new NullPointerException();
        }
        if ((factor < 0.0D) || (factor > 1.0D)) {
            throw new IllegalArgumentException("Erosion Factor, f must be: 0 <= f <= 1");
        }
        synchronized (x)
        {
            double f = 1.0D - factor;

            double oBelief = OpinionBase.constrain(OpinionBase.adjust(x.getBelief() * f));
            double oDisbelief = OpinionBase.constrain(OpinionBase.adjust(x.getDisbelief() * f));
            double oUncertainty = OpinionBase.constrain(OpinionBase.adjust(1.0D - oBelief - oDisbelief));
            double oAtomicity = x.getAtomicity();

            SubjectiveOpinion o = new SubjectiveOpinion(oBelief, oDisbelief, oUncertainty, oAtomicity);

            o.checkConsistency(true);

            return o;
        }
    }

    /**
     * <b>WARNING:</b> This is currently *not* implemented properly, and may produce unexpected results!
     * c.f. semi-associativity Josang 2016, p. 235:
     * @details "Semi-associativity requires that three or more
     * arguments must first be combined together in the consensus step, and then combined
     * together again in the compromise step before the merging step."
     * @param opinions Collection of opinions to be fused
     * @return Fused opinion
     * @throws OpinionArithmeticException
     */
    public static SubjectiveOpinion ccFuse(Collection<? extends Opinion> opinions) throws OpinionArithmeticException
    {
        if (opinions == null) {
            throw new NullPointerException();
        }
        if (opinions.isEmpty()) {
            throw new OpinionArithmeticException("Opinions must not be empty");
        }
        SubjectiveOpinion x = null;

        for (Opinion opinion : opinions) {
            if (opinion != null)
                x = x == null ? new SubjectiveOpinion(opinion) : x.ccFuse(opinion);
        }
        return x;
    }

    public static SubjectiveOpinion cumulativeFuse(Collection<? extends Opinion> opinions) throws OpinionArithmeticException
    {
        if (opinions == null) {
            throw new NullPointerException();
        }
        if (opinions.isEmpty()) {
            throw new OpinionArithmeticException("Opinions must not be empty");
        }
        SubjectiveOpinion x = null;

        for (Opinion opinion : opinions) {
            if (opinion != null)
                x = x == null ? new SubjectiveOpinion(opinion) : x.cumulativeFuse(opinion);
        }
        return x;
    }

    public static Conditionals reverseConditionals(Conditionals conditionals, double baseRateX)
            throws OpinionArithmeticException
    {
        if (conditionals == null) {
            throw new NullPointerException();
        }
        return reverseConditionals(conditionals.getPositive().toSubjectiveOpinion(),
                conditionals.getNegative().toSubjectiveOpinion(), baseRateX);
    }

    public static Conditionals reverseConditionals(SubjectiveOpinion yTx, SubjectiveOpinion yFx, double baseRateX)
            throws OpinionArithmeticException
    {
        if ((baseRateX < 0.0D) || (baseRateX > 1.0D)) {
            throw new IllegalArgumentException("Base Rate x, must be: 0 <= x <= 1");
        }
        if ((yTx == null) || (yFx == null)) {
            throw new NullPointerException();
        }
        SubjectiveOpinion x_br = createVacuousOpinion(baseRateX);

        double atom_y = yTx.getAtomicity();
        SubjectiveOpinion xFy;
        SubjectiveOpinion xTy;
        if (baseRateX == 0.0D)
        {
            xTy = createDogmaticOpinion(0.0D, 0.0D);
            xFy = createDogmaticOpinion(0.0D, 0.0D);
        }
        else
        {
            if (baseRateX == 1.0D)
            {
                xTy = createDogmaticOpinion(1.0D, 1.0D);
                xFy = createDogmaticOpinion(1.0D, 1.0D);
            }
            else
            {
                if ((atom_y == 0.0D) || (atom_y == 1.0D))
                {
                    xTy = new SubjectiveOpinion(0.0D, 0.0D, 1.0D, baseRateX);
                    xFy = new SubjectiveOpinion(0.0D, 0.0D, 1.0D, baseRateX);
                }
                else
                {
                    SubjectiveOpinion not_yTx = complement(yTx);
                    SubjectiveOpinion y_br = deduction(x_br, yTx, yFx);
                    SubjectiveOpinion not_y_br = complement(y_br);
                    SubjectiveOpinion y_and_x = multiply(x_br, yTx);
                    SubjectiveOpinion not_y_and_x = multiply(x_br, not_yTx);

                    xTy = division(y_and_x, y_br);
                    xFy = division(not_y_and_x, not_y_br);
                }
            }
        }
        return new Conditionals(xTy, xFy);
    }

    private static void maximizeUncertainty(SubjectiveOpinion x)
    {
        if (x == null) {
            throw new NullPointerException();
        }
        synchronized (x)
        {
            x.setDependants();
            double u;
            double d;
            double a;
            double b;
            if (x.getExpectation() <= x.getAtomicity())
            {
                b = 0.0D;
                a = x.getAtomicity();
                if (x.getAtomicity() > 0.0D)
                {
                    d = OpinionBase.adjust(1.0D - x.getUncertainty() - x.getBelief() / x.getAtomicity());
                    u = OpinionBase.adjust(x.getUncertainty() + x.getBelief() / x.getAtomicity());
                }
                else
                {
                    d = 0.0D;
                    u = OpinionBase.adjust(1.0D - b);
                }
            }
            else
            {
                d = 0.0D;
                a = x.getAtomicity();
                if (x.getAtomicity() < 1.0D)
                {
                    b = OpinionBase.adjust(1.0D - x.getUncertainty() - x.getDisbelief() / (1.0D - x.getAtomicity()));
                    u = OpinionBase.adjust(x.getUncertainty() + x.getDisbelief() / (1.0D - x.getAtomicity()));
                }
                else
                {
                    b = 0.0D;
                    u = b;
                }
            }

            x.belief = b;
            x.disbelief = d;
            x.uncertainty = u;
            x.atomicity = a;

            x.checkConsistency(true);
        }
    }

    private static SubjectiveOpinion multiply(SubjectiveOpinion x, SubjectiveOpinion y)
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }

        x.setDependants();
        y.setDependants();

        double divisor, expec;
        double r = x.getRelativeWeight(y, OpinionOperator.Or);

        double oDisbelief = x.getDisbelief() + y.getDisbelief() - x.getDisbelief() * y.getDisbelief();
        double oAtomicity = x.getAtomicity() * y.getAtomicity();
        expec = x.getExpectation() * (y.getBelief() + y.getAtomicity() * y.getUncertainty());
        divisor = 1.0D - oAtomicity;

        double oBelief, oUncertainty;
        if (divisor != 0.0D) {
            oBelief = ((oDisbelief - 1.0D) * oAtomicity + expec) / divisor;
            oUncertainty = -(oDisbelief - 1.0D + expec) / divisor;
        } else {
            oBelief = x.getBelief() * y.getBelief() + (r * x.getBelief() * y.getUncertainty() + x.getUncertainty() * y.getBelief()) / (r + 1.0D);
            oUncertainty = (x.getBelief() * y.getUncertainty() + r * y.getBelief() * x.getUncertainty()) / (r + 1.0D) + x.getUncertainty() * y.getUncertainty();
        }

        SubjectiveOpinion o = new SubjectiveOpinion(oBelief, oDisbelief, oUncertainty, oAtomicity);

        o.adjust();
        o.checkConsistency(true);

        o.lastOp = OpinionOperator.And;
        o.setRelativeWeight(x.getRelativeWeight() + y.getRelativeWeight());

        return o;
    }

    /**
     * @deprecated the current implementation doesn't make sense..
     * @param opinions
     */
    public static void normalize(Collection<? extends SubjectiveOpinion> opinions)
    {
        if (opinions == null) {
            throw new NullPointerException("Opinions must not be null");
        }
        if (opinions.isEmpty()) {
            throw new OpinionArithmeticException("Opinions must not be empty");
        }
        double sum = 0.0D;

        for (SubjectiveOpinion o : opinions) {
            sum += o.getExpectation();
        }
        for (SubjectiveOpinion o : opinions)
        {
            if (sum == 0.0D)
                o.setDisbelief(1.0D);
            else
                o.set(new SubjectiveOpinion(o.adjustExpectation(o.getExpectation() / sum)));
        }
    }

    public static SubjectiveOpinion or(Collection<? extends Opinion> opinions) throws OpinionArithmeticException
    {
        if (opinions == null) {
            throw new NullPointerException();
        }
        if (opinions.isEmpty()) {
            throw new OpinionArithmeticException("Opinions must not be empty");
        }
        SubjectiveOpinion x = null;

        for (Opinion opinion : opinions) {
            if (opinion != null)
                x = x == null ? new SubjectiveOpinion(opinion) : x.or(opinion);
        }
        return x;
    }

    //TODO change this
    private static SubjectiveOpinion simpleAnd(SubjectiveOpinion x, SubjectiveOpinion y)
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }
        SubjectiveOpinion o = new SubjectiveOpinion();

        x.setDependants();
        y.setDependants();

        double divisor;
        o.belief = x.getBelief() * y.getBelief();
        o.disbelief = x.getDisbelief() + y.getDisbelief() - x.getDisbelief() * y.getDisbelief();
        o.uncertainty = x.getBelief() * y.getUncertainty() + y.getBelief() * x.getUncertainty() + x.getUncertainty() * y.getUncertainty();
        divisor = x.getBelief() * y.getUncertainty() + y.getBelief() * x.getUncertainty() + x.getUncertainty() * y.getUncertainty();
        if (divisor != 0.0D)
        {
            o.setAtomicity(((y.getAtomicity() * x.getBelief() * y.getUncertainty() + x.getAtomicity() * y.getBelief() * x.getUncertainty() + x.getAtomicity() * y.getAtomicity() * x.getUncertainty() * y.getUncertainty()) / (x.getBelief() * y.getUncertainty() + y.getBelief() * x.getUncertainty() + x.getUncertainty() * y.getUncertainty())));
        }
        else if ((y.getUncertainty() == 0.0D) && (x.getUncertainty() == 0.0D) && (x.getDisbelief() != 1.0D) && (y.getDisbelief() != 1.0D))
        {
            o.setAtomicity(((y.getAtomicity() * x.getBelief() + x.relativeWeight * x.getAtomicity() * y.getBelief()) / (x.getBelief() + x.relativeWeight * y.getBelief())));
        }
        else if ((x.getDisbelief() == 1.0D) && (y.getUncertainty() != 0.0D))
        {
            o.setAtomicity(((y.getAtomicity() * y.getUncertainty() + x.relativeWeight * x.getAtomicity() * y.getBelief() + x.relativeWeight * x.getAtomicity() * y.getAtomicity() * y.getUncertainty()) / (
                    y.getUncertainty() + x.relativeWeight - x.relativeWeight * y.getDisbelief())));
        }
        else if ((y.getDisbelief() == 1.0D) && (x.getUncertainty() != 0.0D))
        {
            o.setAtomicity(((x.relativeWeight * y.getAtomicity() * x.getBelief() + x.getAtomicity() * x.getUncertainty() + x.relativeWeight * x.getAtomicity() * y.getAtomicity() * x.getUncertainty()) / (
                    x.relativeWeight + x.getUncertainty() - x.relativeWeight * x.getDisbelief())));
        }
        else if ((x.getDisbelief() == 1.0D) && (y.getUncertainty() == 0.0D))
        {
            o.setAtomicity(((x.relativeWeight * y.getAtomicity() + x.getAtomicity() * y.getBelief()) / (x.relativeWeight + y.getBelief())));
        }
        else if ((y.getDisbelief() == 1.0D) && (x.getUncertainty() == 0.0D))
        {
            o.setAtomicity(((y.getAtomicity() * x.getBelief() + x.relativeWeight * x.getAtomicity()) / (x.getBelief() + x.relativeWeight)));
        }
        else if ((x.getDisbelief() == 1.0D) && (y.getDisbelief() == 1.0D))
        {
            o.setAtomicity(((y.relativeWeight * y.getAtomicity() + x.relativeWeight * x.getAtomicity() + x.relativeWeight * y.relativeWeight * x.getAtomicity() * y.getAtomicity()) / (
                    y.relativeWeight + x.relativeWeight + x.relativeWeight * y.relativeWeight)));
        }
        else
        {
            o.setAtomicity(0.5D);
        }

        o.checkConsistency(true);
        o.lastOp = OpinionOperator.SimpleAnd;
        o.setRelativeWeight(x.getRelativeWeight() + y.getRelativeWeight());

        return o;
    }

    //TODO change this
    private static SubjectiveOpinion simpleCoMultiplication(SubjectiveOpinion x, SubjectiveOpinion y, double r, double s)
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }
        SubjectiveOpinion o = new SubjectiveOpinion();

        x.setDependants();
        y.setDependants();

        double divisor;
        o.belief = x.getBelief() + y.getBelief() - x.getBelief() * y.getBelief();
        o.disbelief = x.getDisbelief() * y.getDisbelief();
        o.uncertainty = x.getDisbelief() * y.getUncertainty() + y.getDisbelief() * x.getUncertainty() + x.getUncertainty() * y.getUncertainty();
        divisor = x.getUncertainty() + y.getUncertainty() - x.getBelief() * y.getUncertainty() - y.getBelief() * x.getUncertainty() - x.getUncertainty() * y.getUncertainty();

        if (divisor != 0.0D)
        {
            o.setAtomicity(((x.getUncertainty() * x.getAtomicity() + y.getUncertainty() * y.getAtomicity() - y.getAtomicity() * x.getBelief() * y.getUncertainty() - x.getAtomicity() * y.getBelief() * x.getUncertainty() - x.getAtomicity() * y.getAtomicity() * x.getUncertainty() * y.getUncertainty()) / (
                    x.getUncertainty() + y.getUncertainty() - x.getBelief() * y.getUncertainty() - y.getBelief() * x.getUncertainty() - x.getUncertainty() * y.getUncertainty())));
        }
        else if ((y.getUncertainty() == 0.0D) && (x.getUncertainty() == 0.0D) && (x.getDisbelief() != 0.0D) && (y.getDisbelief() != 0.0D))
        {
            o.setAtomicity(((r * x.getAtomicity() * y.getDisbelief() + y.getAtomicity() * x.getDisbelief()) / (r * y.getDisbelief() + x.getDisbelief())));
        }
        else if ((x.getBelief() == 1.0D) && (y.getUncertainty() != 0.0D))
        {
            o.setAtomicity(((r * x.getAtomicity() * y.getDisbelief() + r * x.getAtomicity() * y.getUncertainty() + r * y.getUncertainty() * y.getAtomicity() + y.getUncertainty() * y.getAtomicity() - r * x.getAtomicity() * y.getAtomicity() * y.getUncertainty()) / (r + y.getUncertainty() - r * y.getBelief())));
        }
        else if ((y.getBelief() == 1.0D) && (x.getUncertainty() != 0.0D))
        {
            o.setAtomicity(((r * x.getUncertainty() * x.getAtomicity() + x.getUncertainty() * x.getAtomicity() + r * y.getAtomicity() * x.getDisbelief() + r * y.getAtomicity() * x.getUncertainty() - r * x.getAtomicity() * y.getAtomicity() * x.getUncertainty()) / (x.getUncertainty() + r - r * x.getBelief())));
        }
        else if ((x.getBelief() == 1.0D) && (y.getUncertainty() == 0.0D))
        {
            o.setAtomicity(((x.getAtomicity() * y.getDisbelief() + r * y.getAtomicity()) / (y.getDisbelief() + r)));
        }
        else if ((y.getBelief() == 1.0D) && (x.getUncertainty() == 0.0D))
        {
            o.setAtomicity(((r * x.getAtomicity() + y.getAtomicity() * x.getDisbelief()) / (r + x.getDisbelief())));
        }
        else if ((x.getBelief() == 1.0D) && (y.getBelief() == 1.0D))
        {
            o.setAtomicity(((r * s * x.getAtomicity() + r * x.getAtomicity() + r * s * y.getAtomicity() + s * y.getAtomicity() - r * s * x.getAtomicity() * y.getAtomicity()) / (r + s + r * s)));
        }
        else
        {
            o.setAtomicity(0.5D);
        }

        o.checkConsistency(true);

        return o;
    }

    //TODO change this
    private static SubjectiveOpinion simpleMultiplication(SubjectiveOpinion x, SubjectiveOpinion y, double r, double s)
    {
        if (y == null) {
            throw new NullPointerException();
        }
        SubjectiveOpinion o = new SubjectiveOpinion();

        x.setDependants();
        y.setDependants();

        o.belief = x.getBelief() * y.getBelief();
        o.disbelief = x.getDisbelief() + y.getDisbelief() - x.getDisbelief() * y.getDisbelief();
        o.uncertainty = x.getBelief() * y.getUncertainty() + y.getBelief() * x.getUncertainty() + x.getUncertainty() * y.getUncertainty();

        double divisor = x.getBelief() * y.getUncertainty() + y.getBelief() * x.getUncertainty() + x.getUncertainty() * y.getUncertainty();

        if (divisor != 0.0D)
        {
            o.setAtomicity(((y.getAtomicity() * x.getBelief() * y.getUncertainty() + x.getAtomicity() * y.getBelief() * x.getUncertainty() + x.getAtomicity() * y.getAtomicity() * x.getUncertainty() * y.getUncertainty()) / (x.getBelief() * y.getUncertainty() + y.getBelief() * x.getUncertainty() + x.getUncertainty() * y.getUncertainty())));
        }
        else if ((y.getUncertainty() == 0.0D) && (x.getUncertainty() == 0.0D) && (x.getDisbelief() != 1.0D) && (y.getDisbelief() != 1.0D))
        {
            o.setAtomicity(((y.getAtomicity() * x.getBelief() + r * x.getAtomicity() * y.getBelief()) / (x.getBelief() + r * y.getBelief())));
        }
        else if ((x.getDisbelief() == 1.0D) && (y.getUncertainty() != 0.0D))
        {
            o.setAtomicity(((y.getAtomicity() * y.getUncertainty() + r * x.getAtomicity() * y.getBelief() + r * x.getAtomicity() * y.getAtomicity() * y.getUncertainty()) / (y.getUncertainty() + r - r * y.getDisbelief())));
        }
        else if ((y.getDisbelief() == 1.0D) && (x.getUncertainty() != 0.0D))
        {
            o.setAtomicity(((r * y.getAtomicity() * x.getBelief() + x.getAtomicity() * x.getUncertainty() + r * x.getAtomicity() * y.getAtomicity() * x.getUncertainty()) / (r + x.getUncertainty() - r * x.getDisbelief())));
        }
        else if ((x.getDisbelief() == 1.0D) && (y.getUncertainty() == 0.0D))
        {
            o.setAtomicity(((r * y.getAtomicity() + x.getAtomicity() * y.getBelief()) / (r + y.getBelief())));
        }
        else if ((y.getDisbelief() == 1.0D) && (x.getUncertainty() == 0.0D))
        {
            o.setAtomicity(((y.getAtomicity() * x.getBelief() + r * x.getAtomicity()) / (x.getBelief() + r)));
        }
        else if ((x.getDisbelief() == 1.0D) && (y.getDisbelief() == 1.0D))
        {
            o.setAtomicity(((s * y.getAtomicity() + r * x.getAtomicity() + r * s * x.getAtomicity() * y.getAtomicity()) / (s + r + r * s)));
        }
        else
        {
            o.setAtomicity(0.5D);
        }

        o.checkConsistency(true);

        return o;
    }

    //TODO change this
    private static SubjectiveOpinion simpleOr(SubjectiveOpinion x, SubjectiveOpinion y)
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }
        SubjectiveOpinion o = new SubjectiveOpinion();

        x.setDependants();
        y.setDependants();

        double divisor;
        o.belief = x.getBelief() + y.getBelief() - x.getBelief() * y.getBelief();
        o.disbelief = x.getDisbelief() * y.getDisbelief();
        o.uncertainty = x.getDisbelief() * y.getUncertainty() + y.getDisbelief() * x.getUncertainty() + x.getUncertainty() * y.getUncertainty();
        divisor = x.getUncertainty() + y.getUncertainty() - x.getBelief() * y.getUncertainty() - y.getBelief() * x.getUncertainty() - x.getUncertainty() * y.getUncertainty();
        if (divisor != 0.0D)
        {
            o.setAtomicity(((x.getUncertainty() * x.getAtomicity() + y.getUncertainty() * y.getAtomicity() - y.getAtomicity() * x.getBelief() * y.getUncertainty() - x.getAtomicity() * y.getBelief() * x.getUncertainty() - x.getAtomicity() * y.getAtomicity() * x.getUncertainty() * y.getUncertainty()) / (
                    x.getUncertainty() + y.getUncertainty() - x.getBelief() * y.getUncertainty() - y.getBelief() * x.getUncertainty() - x.getUncertainty() * y.getUncertainty())));
        }
        else if ((y.getUncertainty() == 0.0D) && (x.getUncertainty() == 0.0D) && (x.getDisbelief() != 0.0D) && (y.getDisbelief() != 0.0D))
        {
            o.setAtomicity(((x.relativeWeight * x.getAtomicity() * y.getDisbelief() + y.getAtomicity() * x.getDisbelief()) / (x.relativeWeight * y.getDisbelief() + x.getDisbelief())));
        }
        else if ((x.getBelief() == 1.0D) && (y.getUncertainty() != 0.0D))
        {
            o.setAtomicity(((x.relativeWeight * x.getAtomicity() * y.getDisbelief() + x.relativeWeight * x.getAtomicity() * y.getUncertainty() + x.relativeWeight * y.getUncertainty() * y.getAtomicity() + y.getUncertainty() * y.getAtomicity() - x.relativeWeight *
                    x.getAtomicity() * y.getAtomicity() * y.getUncertainty()) / (
                    x.relativeWeight + y.getUncertainty() - x.relativeWeight * y.getBelief())));
        }
        else if ((y.getBelief() == 1.0D) && (x.getUncertainty() != 0.0D))
        {
            o.setAtomicity(((x.relativeWeight * x.getUncertainty() * x.getAtomicity() + x.getUncertainty() * x.getAtomicity() + x.relativeWeight * y.getAtomicity() * x.getDisbelief() + x.relativeWeight * y.getAtomicity() * x.getUncertainty() - x.relativeWeight *
                    x.getAtomicity() * y.getAtomicity() * x.getUncertainty()) / (
                    x.getUncertainty() + x.relativeWeight - x.relativeWeight * x.getBelief())));
        }
        else if ((x.getBelief() == 1.0D) && (y.getUncertainty() == 0.0D))
        {
            o.setAtomicity(((x.getAtomicity() * y.getDisbelief() + x.relativeWeight * y.getAtomicity()) / (y.getDisbelief() + x.relativeWeight)));
        }
        else if ((y.getBelief() == 1.0D) && (x.getUncertainty() == 0.0D))
        {
            o.setAtomicity(((x.relativeWeight * x.getAtomicity() + y.getAtomicity() * x.getDisbelief()) / (x.relativeWeight + x.getDisbelief())));
        }
        else if ((x.getBelief() == 1.0D) && (y.getBelief() == 1.0D))
        {
            o.setAtomicity(((x.relativeWeight * y.relativeWeight * x.getAtomicity() + x.relativeWeight * x.getAtomicity() + x.relativeWeight * y.relativeWeight * y.getAtomicity() +
                    y.relativeWeight * y.getAtomicity() - x.relativeWeight * y.relativeWeight * x.getAtomicity() * y.getAtomicity()) / (
                    x.relativeWeight + y.relativeWeight + x.relativeWeight * y.relativeWeight)));
        }
        else
        {
            o.setAtomicity(0.5D);
        }

        o.checkConsistency(true);
        o.lastOp = OpinionOperator.SimpleOr;
        o.setRelativeWeight(x.getRelativeWeight() + y.getRelativeWeight());

        return o;
    }

    protected static SubjectiveOpinion smoothAverage(Collection<? extends Opinion> opinions)
    {
        if (opinions == null) {
            throw new NullPointerException();
        }
        if (opinions.isEmpty()) {
            throw new IllegalArgumentException("Opinions must not be empty");
        }

        int count = 0;

        double b = 0.0D; double a = 0.0D; double e = 0.0D;

        for (Opinion opinion : opinions) {
            if (opinion != null)
            {
                SubjectiveOpinion x = new SubjectiveOpinion(opinion);

                count++;
                b += x.getBelief();
                a += x.getAtomicity();
                e += x.getBelief() + x.getAtomicity() * x.getUncertainty();
            }
        }
        if (count == 0) {
            throw new IllegalArgumentException("Opinions must not be empty");
        }
        double oBelief = b / count;
        double oAtomicity = a / count;
        double oUncertainty = (e / count - oBelief) / oAtomicity;
        double oDisbelief = 1.0D - oBelief - oUncertainty;

        SubjectiveOpinion o = new SubjectiveOpinion(oBelief, oDisbelief, oUncertainty, oAtomicity);

        o.adjust();
        o.checkConsistency(true);

        return o;
    }

    private static SubjectiveOpinion subtraction(SubjectiveOpinion x, SubjectiveOpinion y)
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }
        if (x.getAtomicity() - y.getAtomicity() < 0.0D) {
            throw new OpinionArithmeticException("Illegal operation, Difference of atomicities is less than 0.0");
        }
        double b = x.getBelief() - y.getBelief();
        double a = OpinionBase.constrain(x.getAtomicity() - y.getAtomicity());
        double u = x.getAtomicity() * x.getUncertainty() - y.getAtomicity() * y.getUncertainty();

        if (a != 0.0D) {
            u /= a;
        }
        SubjectiveOpinion o = clippedOpinion(b, u, a);

        o.lastOp = OpinionOperator.Subtract;

        return o;
    }

    private static SubjectiveOpinion sum(Collection<? extends Opinion> opinions) throws OpinionArithmeticException
    {
        if (opinions == null) {
            throw new NullPointerException();
        }
        if (opinions.isEmpty()) {
            throw new OpinionArithmeticException("Opinions must not be empty");
        }
        double b = 0.0D;
        double u = 0.0D;
        double a = 0.0D;

        for (Opinion o : opinions)
        {
            SubjectiveOpinion so = o.toSubjectiveOpinion();

            b += so.getBelief();
            u += so.getUncertainty() * so.getAtomicity();
            a += so.getAtomicity();
        }

        if (a > 1.0D) {
            throw new OpinionArithmeticException("Illegal operation, Sum of atomicities is greater than 1.0");
        }
        if (a > 0.0D) {
            u /= a;
        }
        SubjectiveOpinion o = clippedOpinion(b, u, a);

        o.lastOp = OpinionOperator.Add;

        return o;
    }

    private static SubjectiveOpinion sum(SubjectiveOpinion x, SubjectiveOpinion y) throws OpinionArithmeticException
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }
        if (y.getAtomicity() + x.getAtomicity() > 1.0D) {
            throw new OpinionArithmeticException("Illegal operation, Sum of atomicities is greater than 1.0");
        }
        double b = x.getBelief() + y.getBelief();
        double a = x.getAtomicity() + y.getAtomicity();
        double u = x.getAtomicity() * x.getUncertainty() + y.getAtomicity() * y.getUncertainty();

        if (a > 0.0D) {
            u /= a;
        }
        SubjectiveOpinion o = clippedOpinion(b, u, a);

        o.lastOp = OpinionOperator.Add;

        return o;
    }

    private static SubjectiveOpinion transitivity(SubjectiveOpinion x, SubjectiveOpinion y)
    {
        if ((x == null) || (y == null)) {
            throw new NullPointerException();
        }
        //TODO submit this code upstream: the old code does not correspond to the book's description of transitivity.
        double e = x.getExpectation();
        double newBelief = e * y.getBelief();
        double newDisbelief = e * y.getDisbelief();
        double newUncertainty = 1-e*(y.getDisbelief() + y.getBelief());
        double newAtomicity = y.getAtomicity();

        SubjectiveOpinion o = new SubjectiveOpinion(newBelief, newDisbelief, newUncertainty, newAtomicity);

        o.checkConsistency(true);

        o.lastOp = OpinionOperator.Discount;

        return o;
    }

    public SubjectiveOpinion()
    {
    }

    public SubjectiveOpinion(double atomicity)
    {
        this.atomicity = atomicity;
    }

    public SubjectiveOpinion(double belief, boolean dogmatic)
    {
        setBelief(belief, dogmatic);
    }

    public SubjectiveOpinion(double belief, boolean dogmatic, double atomicity)
    {
        setBelief(belief, dogmatic);
        setAtomicity(atomicity);
    }

    public SubjectiveOpinion(double belief, double uncertainty)
    {
        setBelief(belief, uncertainty);
    }

    public SubjectiveOpinion(double belief, double disbelief, double uncertainty)
    {
        set(belief, disbelief, uncertainty);
    }

    public SubjectiveOpinion(double belief, double disbelief, double uncertainty, double atomicity)
    {
        this(belief, disbelief, uncertainty);
        setAtomicity(atomicity);
    }

    public SubjectiveOpinion(Opinion o)
    {
        if (o == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        SubjectiveOpinion x = o.toSubjectiveOpinion();

        this.belief = x.getBelief();
        this.disbelief = x.getDisbelief();
        this.uncertainty = x.getUncertainty();
        this.atomicity = x.getAtomicity();
        this.cachedExpectation = x.getExpectation();
        this.relativeWeight = x.relativeWeight;
        this.lastOp = x.lastOp;
        this.recalculate = x.recalculate;
    }

    public SubjectiveOpinion(SubjectiveOpinion o)
    {
        if (o == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        synchronized (o)
        {
            o.setDependants();

            this.belief = o.getBelief();
            this.disbelief = o.getDisbelief();
            this.uncertainty = o.getUncertainty();
            this.atomicity = o.getAtomicity();
            this.cachedExpectation = o.getExpectation();
            this.relativeWeight = o.relativeWeight;
            this.lastOp = o.lastOp;
            this.recalculate = o.recalculate;
        }
    }

    public final SubjectiveOpinion abduce(Conditionals conditionals, double baseRateX) throws OpinionArithmeticException
    {
        if (conditionals == null) {
            throw new NullPointerException();
        }
        return abduction(new SubjectiveOpinion(this), new SubjectiveOpinion(conditionals.getPositive()), new SubjectiveOpinion(
                conditionals.getNegative()), baseRateX);
    }

    public final SubjectiveOpinion abduce(Opinion xTy, Opinion xFy, double baseRateX) throws OpinionArithmeticException
    {
        if ((xTy == null) || (xFy == null)) {
            throw new NullPointerException();
        }
        return abduction(new SubjectiveOpinion(this), new SubjectiveOpinion(xTy), new SubjectiveOpinion(xFy), baseRateX);
    }

    public final SubjectiveOpinion add(Opinion opinion)
            throws OpinionArithmeticException
    {
        if (opinion == null) {
            throw new NullPointerException("The Opinion must not be null");
        }
        return sum(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion));
    }

    private void adjust()
    {
        this.belief = OpinionBase.constrain(OpinionBase.adjust(this.getBelief()));
        this.disbelief = OpinionBase.constrain(OpinionBase.adjust(this.getDisbelief()));
        this.uncertainty = OpinionBase.constrain(OpinionBase.adjust(this.getUncertainty()));
    }

    public SubjectiveOpinion adjustExpectation(double expectation)
    {
        SubjectiveOpinion o = new SubjectiveOpinion(this);
        adjustExpectation(o, expectation);
        return o;
    }

    public SubjectiveOpinion adjustExpectation(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        return adjustExpectation(opinion.getExpectation());
    }

    public final SubjectiveOpinion and(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        return multiply(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion));
    }

    public final SubjectiveOpinion average(Opinion opinion)
    {
        Collection<Opinion> opinions = new ArrayList<>();

        opinions.add(new SubjectiveOpinion(this));
        opinions.add(new SubjectiveOpinion(opinion));

        return smoothAverage(opinions).toSubjectiveOpinion();
    }

    private void checkConsistency() throws OpinionArithmeticException
    {
        checkConsistency(false);
    }

    private void checkConsistency(boolean recalculate) throws OpinionArithmeticException
    {
        synchronized (this)
        {
            if ((this.getAtomicity() < 0.0D) || (this.getAtomicity() > 1.0D)) {
                throw new OpinionArithmeticException("Atomicity out of range, atomicity: 0 <= atomicity <= 1");
            }
            if (recalculate)
            {
                this.belief = OpinionBase.constrain(OpinionBase.adjust(this.getBelief()));
                this.disbelief = OpinionBase.constrain(OpinionBase.adjust(this.getDisbelief()));
                this.uncertainty = OpinionBase.constrain(OpinionBase.adjust(this.getUncertainty()));

                if (Math.abs(this.getBelief() + this.getDisbelief() + this.getUncertainty() - 1.0D) > 1.0E-010D)
                {
                    double bdu = this.getBelief() + this.getDisbelief() + this.getUncertainty();
                    this.belief = OpinionBase.constrain(OpinionBase.adjust(this.getBelief() / bdu));
                    this.uncertainty = OpinionBase.constrain(OpinionBase.adjust(this.getUncertainty() / bdu));
                    this.disbelief = 1.0D - (this.getBelief() + this.getUncertainty());
                }

                this.recalculate = true;
            }
            else
            {
                if ((this.getBelief() < 0.0D) || (this.getBelief() > 1.0D)) {
                    throw new OpinionArithmeticException("Belief out of range, belief: 0 <= belief <= 1");
                }
                if ((this.getDisbelief() < 0.0D) || (this.getDisbelief() > 1.0D)) {
                    throw new OpinionArithmeticException("Disbelief out of range, disbelief: 0 <= disbelief <= 1");
                }
                if ((this.getUncertainty() < 0.0D) || (this.getUncertainty() > 1.0D)) {
                    throw new OpinionArithmeticException("Uncertainty out of range, uncertainty: 0 <= uncertainty <= 1");
                }
                if (Math.abs(this.getBelief() + this.getDisbelief() + this.getUncertainty() - 1.0D) > 1.0E-010D)
                    throw new OpinionArithmeticException("Belief, disbelief and uncertainty do not add up to 1: belief + disbelief + uncertainty != 1");
            }
        }
    }

    public final SubjectiveOpinion decay(double halfLife, double time)
    {
        return erosion(this, OpinionBase.erosionFactorFromHalfLife(halfLife, time));
    }

    public final SubjectiveOpinion deduce(Conditionals conditionals) throws OpinionArithmeticException
    {
        if (conditionals == null) {
            throw new NullPointerException("The conditionals must not be null");
        }
        return deduction(new SubjectiveOpinion(this), new SubjectiveOpinion(conditionals.getPositive()), new SubjectiveOpinion(
                conditionals.getNegative()));
    }

    public final SubjectiveOpinion deduce(Opinion yTx, Opinion yFx)
            throws OpinionArithmeticException
    {
        if ((yTx == null) || (yFx == null)) {
            throw new NullPointerException("The conditionals must not be null");
        }
        return deduction(new SubjectiveOpinion(this), new SubjectiveOpinion(yTx), new SubjectiveOpinion(yFx));
    }

    /** @deprecated */
    public final SubjectiveOpinion discount(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        return transitivity(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion));
    }

    public final SubjectiveOpinion discountBy(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        return transitivity(new SubjectiveOpinion(opinion), new SubjectiveOpinion(this));
    }

    public final SubjectiveOpinion dogmaticOpinion()
    {
        SubjectiveOpinion o = new SubjectiveOpinion(this);

        o.setBelief(o.getExpectation(), true);

        return o;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null) {
            return false;
        }
        if ((obj instanceof SubjectiveOpinion))
        {
            SubjectiveOpinion o = (SubjectiveOpinion)obj;

            return (Math.abs(o.getBelief() - this.getBelief()) < 1.0E-010D) && (Math.abs(o.getDisbelief() - this.getDisbelief()) < 1.0E-010D) && (Math.abs(o.getUncertainty() - this.getUncertainty()) < 1.0E-010D) &&
                    (Math.abs(o.getAtomicity() -
                            this.getAtomicity()) < 1.0E-010D);
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + (int) (Double.doubleToLongBits(this.getAtomicity()) ^ (Double.doubleToLongBits(this.getAtomicity()) >>> 32));
        hash = 71 * hash + (int) (Double.doubleToLongBits(this.getBelief()) ^ (Double.doubleToLongBits(this.getBelief()) >>> 32));
        hash = 71 * hash + (int) (Double.doubleToLongBits(this.getDisbelief()) ^ (Double.doubleToLongBits(this.getDisbelief()) >>> 32));
        hash = 71 * hash + (int) (Double.doubleToLongBits(this.getUncertainty()) ^ (Double.doubleToLongBits(this.getUncertainty()) >>> 32));
        return hash;
    }

    public final SubjectiveOpinion erode(double factor)
    {
        return erosion(this, factor);
    }

    public final SubjectiveOpinion cumulativeFuse(Opinion opinion)
            throws OpinionArithmeticException
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        SubjectiveOpinion copy1 = new SubjectiveOpinion(this);
        SubjectiveOpinion copy2 = new SubjectiveOpinion(opinion);
        return cumulativeFusion(copy1, copy2);
    }

    public final SubjectiveOpinion ccFuse(Opinion opinion)
            throws OpinionArithmeticException
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        return ccFusion(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion));
    }

    public final SubjectiveOpinion wbFuse(Opinion opinion)
            throws OpinionArithmeticException
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        return wbFusion(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion));
    }

    @Override
    public final double getAtomicity()
    {
        return this.atomicity;
    }

    public final double getBelief()
    {
        return this.belief;
    }

    public final double getCertainty()
    {
        if (this.getUncertainty() == (0.0D / 0.0D)) {
            return (0.0D / 0.0D);
        }
        return OpinionBase.adjust(1.0D - this.getUncertainty());
    }

    public final double getDisbelief()
    {
        return this.disbelief;
    }

    @Override
    public final double getExpectation()
    {
        synchronized (this)
        {
            setDependants();
            return this.cachedExpectation;
        }
    }

    public double getRelativeWeight()
    {
        if(this.isDogmatic())
            return this.relativeWeight;
        else
            return 0.0D;
    }

    private double getRelativeWeight(SubjectiveOpinion opinion, OpinionOperator operator)
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        if ((operator != null) && (this.lastOp == operator) && (operator.isAssociative())) {
            return this.relativeWeight / opinion.relativeWeight;
        }
        return 1.0D;
    }

    public final double getUncertainty()
    {
        return this.uncertainty;
    }

    public SubjectiveOpinion increasedUncertainty()
    {
        synchronized (this)
        {
            double sqrt_u = OpinionBase.adjust(Math.sqrt(this.getUncertainty()));
            double k = 1.0D - (sqrt_u - this.getUncertainty()) / (this.getBelief() + this.getDisbelief());

            double brBelief = OpinionBase.adjust(this.getBelief() * k);
            double brUncertainty = sqrt_u;
            double brDisbelief = OpinionBase.adjust(this.getDisbelief() * k);

            SubjectiveOpinion br = new SubjectiveOpinion(brBelief, brDisbelief, brUncertainty);

            return br;
        }
    }

    public boolean isAbsolute()
    {
        return (this.getBelief() == 1.0D) || (this.getDisbelief() == 1.0D);
    }

    public boolean isVacuous()
    {
        return this.getUncertainty() == 1.0D;
    }

    public boolean isCertain(double threshold)
    {
        return !isUncertain(threshold);
    }

    public boolean isConsistent()
    {
        try
        {
            checkConsistency();
            return true;
        }
        catch (OpinionArithmeticException ex) {
            return false;
        }
    }

    public boolean isDogmatic()
    {
        return this.getUncertainty() == 0.0D;
    }

    public boolean isMaximizedUncertainty()
    {
        return (this.getDisbelief() == 0.0D) || (this.getBelief() == 0.0D);
    }

    public boolean isUncertain(double threshold)
    {
        return 1.0D - this.getUncertainty() < threshold;
    }

    public SubjectiveOpinion uncertainOpinion()
    {
        SubjectiveOpinion o = new SubjectiveOpinion(this);
        maximizeUncertainty(o);
        return o;
    }

    public final SubjectiveOpinion not()
    {
        return complement(this);
    }

    public final SubjectiveOpinion or(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException();
        }
        return coMultiplication(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion));
    }

    public final void set(double belief, double disbelief, double uncertainty)
    {
        if ((this.getBelief() < 0.0D) || (this.getDisbelief() < 0.0D) || (this.getUncertainty() < 0.0D)) {
            throw new IllegalArgumentException("Belief, Disbelief and Uncertainty, x,  must be: 0 <= x");
        }
        double bdu = belief + disbelief + uncertainty;
        setBelief(belief / bdu, uncertainty / bdu);
    }

    public synchronized void set(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException("Opinion must not be null");
        }
        if (opinion.equals(this)) {
            return;
        }
        Opinion old = new SubjectiveOpinion(this);

        SubjectiveOpinion o = opinion.toSubjectiveOpinion();

        synchronized (o)
        {
            this.belief = o.getBelief();
            this.disbelief = o.getDisbelief();
            this.uncertainty = o.getUncertainty();
            this.atomicity =o.getAtomicity();
            this.cachedExpectation = o.getExpectation();
            this.recalculate = o.recalculate;
            this.lastOp = o.lastOp;
            this.relativeWeight = o.relativeWeight;
        }

        this.changeSupport.firePropertyChange("opinion", old, this);
    }

    public final void setAtomicity(double atomicity)
    {
        if ((atomicity < 0.0D) || (atomicity > 1.0D)) {
            throw new IllegalArgumentException("Atomicity, x, must be: 0 <= x <= 1");
        }
        if (atomicity == this.atomicity) {
            return;
        }
        double old = this.atomicity;

        synchronized (this)
        {
            this.atomicity = atomicity;
            this.recalculate = true;
        }

        this.changeSupport.firePropertyChange("atomicity", old, this.atomicity);
    }

    private void setBelief(double belief)
    {
        setBelief(belief, false);
    }

    private void setBelief(double belief, boolean dogmatic)
    {
        if (dogmatic)
            setBelief(belief, 0.0D);
        else
            setBelief(belief, 1.0D - belief);
    }

    protected void setBelief(double belief, double uncertainty)
    {
        if ((belief < 0.0D) || (belief > 1.0D) || (uncertainty < 0.0D) || (uncertainty > 1.0D)) {
            throw new IllegalArgumentException("Belief x or Uncertainty x, must be: 0 <= x <= 1");
        }
        if (belief + uncertainty - 1.0D > 1.0E-010D) {
            throw new IllegalArgumentException("Belief belief, Uncertainty uncertainty, must be: (belief + uncertainty) <= 1");
        }
        if ((belief == this.getBelief()) && (uncertainty == this.getUncertainty())) {
            return;
        }
        Opinion old = new SubjectiveOpinion(this);

        synchronized (this)
        {
            this.belief = belief;
            this.uncertainty = uncertainty;
            this.disbelief = (1.0D - (belief + uncertainty));
            this.recalculate = true;
        }

        this.changeSupport.firePropertyChange("opinion", old, this);
    }

    private synchronized void setDependants()
    {
        if (this.recalculate)
        {
            this.belief = OpinionBase.constrain(OpinionBase.adjust(this.getBelief()));
            this.disbelief = OpinionBase.constrain(OpinionBase.adjust(this.getDisbelief()));
            this.uncertainty = OpinionBase.constrain(OpinionBase.adjust(this.getUncertainty()));
            this.atomicity = OpinionBase.constrain(OpinionBase.adjust(this.getAtomicity()));
            this.cachedExpectation = OpinionBase.constrain(OpinionBase.adjust(this.getBelief() + this.getAtomicity() * this.getUncertainty()));
            this.recalculate = false;
        }
    }

    private synchronized void setDependants(boolean force)
    {
        if (force) {
            this.recalculate = true;
        }
        setDependants();
    }

    private void setDisbelief(double disbelief)
    {
        setDisbelief(disbelief, false);
    }

    private void setDisbelief(double disbelief, boolean dogmatic)
    {
        if (dogmatic)
            setDisbelief(disbelief, 0.0D);
        else
            setDisbelief(disbelief, 1.0D - disbelief);
    }

    private void setDisbelief(double disbelief, double uncertainty)
    {
        if ((disbelief < 0.0D) || (disbelief > 1.0D) || (uncertainty < 0.0D) || (uncertainty > 1.0D)) {
            throw new IllegalArgumentException("Disbelief, x, must be: 0 <= 1");
        }
        if (disbelief + uncertainty - 1.0D > 1.0E-010D) {
            throw new IllegalArgumentException("Disbelief disbelief, Uncertainty uncertainty, must be: (disbelief + uncertainty) <= 1");
        }
        if ((disbelief == this.getDisbelief()) && (uncertainty == this.getUncertainty())) {
            return;
        }
        Opinion old = new SubjectiveOpinion(this);

        synchronized (this)
        {
            this.disbelief = disbelief;
            this.uncertainty = uncertainty;
            this.belief = 1.0D - (disbelief + uncertainty);
            this.recalculate = true;
        }

        this.changeSupport.firePropertyChange("opinion", old, this);
    }

    private void setRelativeWeight(double weight)
    {
        if (weight == this.relativeWeight) {
            return;
        }
        Double old = this.relativeWeight;

        this.relativeWeight = weight;

        this.changeSupport.firePropertyChange("relativeWeight", old, this.relativeWeight);
    }

    public final SubjectiveOpinion subtract(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException();
        }
        return subtraction(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion));
    }

    public DiscreteBayesian toDiscreteBayesian(int size)
    {
        if (size < 2) {
            throw new IllegalArgumentException("Conversion not possible");
        }
        return toPureBayesian().toDiscreteBayesian(size);
    }

    @Override
    public PureBayesian toPureBayesian()
    {
        PureBayesian bayesian = new PureBayesian();

        synchronized (this)
        {
            if (this.getUncertainty() == 0.0D)
            {
                bayesian.setPositive(1.797693134862316E+297D);
                bayesian.setNegative(1.797693134862316E+297D);
            }
            else
            {
                double r = 2.0D * this.getBelief() / this.getUncertainty();
                double s = 2.0D * this.getDisbelief() / this.getUncertainty();

                bayesian.setPositive(Double.isInfinite(r) ? 1.797693134862316E+297D : r);
                bayesian.setNegative(Double.isInfinite(s) ? 1.797693134862316E+297D : s);
            }

            bayesian.setAtomicity(this.getAtomicity());
        }

        return bayesian;
    }

    @Override
    public String toString()
    {
        return String.format(TO_STRING_FORMAT, this.getBelief(), this.getDisbelief(), this.getUncertainty(), this.getAtomicity(), getExpectation(), this.relativeWeight);
    }

    @Override
    public SubjectiveOpinion toSubjectiveOpinion()
    {
        return this;
    }

    public final SubjectiveOpinion unAnd(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException();
        }
        return division(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion));
    }

    public final SubjectiveOpinion unOr(Opinion opinion)
    {
        if (opinion == null) {
            throw new NullPointerException();
        }
        return coDivision(new SubjectiveOpinion(this), new SubjectiveOpinion(opinion), 0.0D);
    }

    public void setUncertainty(double u) {
        this.uncertainty = u;
    }
}