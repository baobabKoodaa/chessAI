
package Evaluators;

/**
 *
 * @author Adreno
 */
public class LivelockDetector {
    
}

//        /* Hack to discover our true color */
//        if (!weKnowOurTrueColor) {
//            if (p.whiteToMove) seenWhiteCount++;
//            else seenBlackCount++;
//            if (seenWhiteCount + seenBlackCount > 50000) {
//                weKnowOurTrueColor = true; /* Guess, to be exact */
//                double ratio = seenBlackCount * 1.0 / (seenWhiteCount+seenBlackCount);
//                ourTrueColor = (ratio > 0.3 ? BLACK : WHITE);
//                
//                System.out.print("We have discovered we are " + (ourTrueColor == WHITE ? "WHITE" : "BLACK"));
//                System.out.println("  by " + seenBlackCount + " to " + seenWhiteCount + " = " + ratio);
//            }
//        }


//        if (STATE != DEBUG) {
//            Double v = evaluatedPositions.get(hash);
//            if (v != null) {
//                livelockDetector++;
//                if (livelockDetector > 700) {
//                    /** Try to detect livelocks (moving same pieces back & forth)
//                      * We won't be able to decipher if we are in the lead (up the tree)
//                      * but assume we are better than our opponent & it is in our
//                      * interest to take risks to win rather than take the tie */
//                    int affordToLose = 100;
//                    v += affordToLose - rng.nextInt(2 * affordToLose);
//                }
//                //return v;
//            }
//            livelockDetector = 0;
//        }


