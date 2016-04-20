package Evaluators;

import Framework.Position;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Random;

public class CerberusV1 extends Evaluator {

    public static final int DEV = 1;
    public static final int PRODUCTION = 2;
    public static final int DEBUG = 3;
    public int STATE = PRODUCTION;

    public static final int BLACK = 0;
    public static final int WHITE = 1;
    public static final int KING = 1;
    public static final int QUEEN = 2;
    public static final int ROOK = 3;
    public static final int KNIGHT = 5;
    public static final int PAWN = 6;

    public static final double HOSTILE_PIN_BONUS = 30;
    public static final double IMMOBILIZED_KING_PENALTY = 10;
    public static final double CAPTURITY_BONUS = 0.02;
    public static final double PASSED_PAWN_BONUS = 50;
    public static final double BLOCKED_PAWN_PENALTY = 25;
    public static final double ISOLATED_PAWN_PENALTY = 25; /* Per pawn */
    public static final double DOUBLED_PAWNS_PENALTY = 50; /* Per lane */

    public double COST_OF_TEMPO = 60; // eg. check
    public double PROTECTION_BONUS = 1000;
    public double MOBILITY_BONUS = 10;

    public double PVqueen = 900;
    public double PVking = 20000;
    public double[] PVpawnsTotal;
    public double[] PVpawn;
    public double[] PVknight;
    public double[] PVrook;

    HashMap<Long, Double> evaluatedPositions;
    Random rng;

    // Position specific
    int[][] b;
    double[] score;
    int sideToMove;
    int sideWaiting;
    int pieceCount;
    int[] pawnCount;
    int[][] pawnLanes;
    int[][][] pawnCoordinates;
    int[] pawnPointer;
    int[] blockedPawns;
    int[][][] pinned;
    double[][][] sqControlLowVal;
    int[][][] sqControlCount;
    int[] kingUnderCheck; /** Number of checking units. */
    int[][] checkingUnit;
    int[] mobilityCount;
    double[][] bestCapValue;
    double[][] bestCapCoordinates;
    double[] capAlt;
    int[][] kingLives;
    int[] pawns;

    // Set once
    double[][][][][] pst;
    double[][] enemyControlNearKingPenalty;
    boolean[][] isEnemyRookOrQueen;
    boolean[][] isFriendly;
    boolean[][] isEnemy;
    int[][][] neighboringSquares;
    int[][][] knightMoveLists;

    // Debugging
    ScoreAnalyzer[] scoreAnalyzer;

    public CerberusV1() {
        evaluatedPositions = new HashMap<>();
        rng = new Random();
        generateTrivialLookupTables();
        generateEnemyControlNearKingPenalty();
        generateNeighboringSquaresLists();
        generateKnightMoveLists();
        generatePieceSquareTables();
        reWeightMaterialScores(1.5); /* Material value re-weighted relative to other goals (1.5 found by experimenting) */
        reWeightPST(1.2); /* 1.2 found by experimenting */
        reWeightKQscores(5.0); /* Queen boosted 5x. Not entirely sure why this improves results. */
    }

    public double eval(Position p) {
        double v = ev(p);
        if (STATE == DEBUG) analyzeScores();
        if (STATE == PRODUCTION) v += 10 - rng.nextInt(20);
        return v;
    }

    public double ev(Position p) {
        b = p.board;

        if (STATE == DEBUG) {
            scoreAnalyzer = new ScoreAnalyzer[2];
            scoreAnalyzer[WHITE] = new ScoreAnalyzer();
            scoreAnalyzer[BLACK] = new ScoreAnalyzer();
        }


        /** Have we seen this position before? */
        long hash = hashPosition(p);
        if (STATE != DEBUG) {
            Double v = evaluatedPositions.get(hash);
            //if (v != null) return v;
        /* Hashing disabled due to some weird problems (not hash collisions) */
        /* I would enable hashing if there was some penalty for using time */
        }


        /** Find kings, calculate pieceCount, calculate pawnCounts */
        pieceCount = 0;
        pawnCount = new int[2];
        pawnCount[WHITE] = 0;
        pawnCount[BLACK] = 0;
        kingLives = new int[2][3];
        for (int y=0; y<6; y++) {
            for (int x=0; x<6; x++) {
                switch (b[x][y]) {
                    case Position.Empty:
                        continue; /* we are about to increment pieceCount */
                    case Position.WKing:
                        markKingLocation(WHITE, x, y);
                        break;
                    case Position.BKing:
                        markKingLocation(BLACK, x, y);
                        break;
                    case Position.WPawn:
                        pawnCount[WHITE]++;
                        break;
                    case Position.BPawn:
                        pawnCount[BLACK]++;
                        break;
                    default:
                        break;
                }
                pieceCount++;
            }
        }

    /* checkmate? */
        if (kingLives[WHITE][0] == 0) return -1e9;
        if (kingLives[BLACK][0] == 0) return 1e9;

    /* initialize more position specific variables */
        pawnLanes = new int[2][7];
        blockedPawns = new int[2];
        pawnCoordinates = new int[2][6][2];
        pawnPointer = new int[2];
        pawnPointer[WHITE] = 0;
        pawnPointer[BLACK] = 0;
        score = new double[2];
        initializeSqControl();
        kingUnderCheck = new int[2];
        checkingUnit = new int[2][2];
        pinned = new int[6][6][3];
        mobilityCount = new int[2];
        bestCapValue = new double[2][2];
        bestCapCoordinates = new double[2][2];
        capAlt = new double[2];
        pawns = new int[2];
        sideToMove = (p.whiteToMove ? WHITE : BLACK);
        sideWaiting = (sideToMove+1)%2;


        /** * * MAGIC * * */
        preProcessSquares();
        /** * * MAGIC * * */


    /* checkmate in 1 ply? */
        if (kingUnderCheck[sideWaiting] > 0) {
            if (sideWaiting == WHITE) return -1e9;
            else                      return 1e9;
        }


        /** * * MAGIC * * */
        postProcessSquares();
        /** * * MAGIC * * */

        double v = score[WHITE] - score[BLACK];
        double ylivoimaKerroin = 1 + Math.abs(v) / (score[WHITE] + score[BLACK]);
        v *= ylivoimaKerroin; //TODO: Mieti onko tää paras mahdollinen tapa toteuttaa tää juttu?
        // esim laske et tää toimii jollain vaihdolla ylivoimatilantees
        if (STATE == DEBUG) {
            System.out.println("Original diff: " + (score[WHITE]-score[BLACK]) + ", modified diff: " + v);
        }

        if (STATE != DEBUG) {
            //evaluatedPositions.put(hash, v);
        }
        return v;
    }

    public long hashPosition(Position p) {
        long hash = (p.whiteToMove ? 2 : 1);
        long A = 31L;
        for (int y=0; y<6; y++) {
            for (int x=0; x<6; x++) {
                hash *= A; /* modulates by long overflow */
                hash += (b[x][y]+1);
            }
        }
        hash *= A;
        return hash;
    }

    /* First kings (checks and pins), then pawns (reducing other units' mobility), then other units */
    public void preProcessSquares() {
        preProcessKing(WHITE, kingLives[WHITE][1], kingLives[WHITE][2]);
        preProcessKing(BLACK, kingLives[BLACK][1], kingLives[BLACK][2]);
        for (int x = 0; x < b.length; x++) {
            for (int y = 0; y < b[x].length; y++) {
            /* TODO: Faster to locate pawns in pre-preprocessing */
                if (Position.WPawn == b[x][y]) preProcessPawn(WHITE, x, y);
                if (Position.BPawn == b[x][y]) preProcessPawn(BLACK, x, y);
            }
        }
        for (int x = 0; x < b.length; x++) {
            for (int y = 0; y < b[x].length; y++) {
                switch (b[x][y]) {
                    case Position.Empty: continue;
                    case Position.WKing:    /*preProcessKing(WHITE, x, y);*/break; /* preProcessed earlier */
                    case Position.WQueen:   preProcessQueen(WHITE, x, y);break;
                    case Position.WKnight:  preProcessKnight(WHITE, x, y);break;
                    case Position.WRook:    preProcessRook(WHITE, x, y);break;
                    case Position.WPawn:    /*preProcessPawn(WHITE, x, y);*/break; /* preProcessed earlier */

                    case Position.BKing:    /*preProcessKing(BLACK, x, y);*/break; /* preProcessed earlier */
                    case Position.BQueen:   preProcessQueen(BLACK, x, y);break;
                    case Position.BKnight:  preProcessKnight(BLACK, x, y);break;
                    case Position.BRook:    preProcessRook(BLACK, x, y);break;
                    case Position.BPawn:    /*preProcessPawn(BLACK, x, y)*/;break; /* preProcessed earlier */
                    default:                ; break;
                }
            }
        }
    }

    public void postProcessSquares() {
        postProcessKing(WHITE, Position.WKing);
        postProcessKing(BLACK, Position.BKing);
        postProcessPawns(WHITE);
        postProcessPawns(BLACK);
        analyzePotentialCapturesEtc();
        scoreMobility();
        scoreCaptures();
    }

    public void analyzePotentialCapturesEtc() {
        double captureValue = -1; /* placeholder */
        int defendColor = -1;     /* placeholder */
        int attackColor = -1;     /* placeholder */
        int n = -1;               /* placeholder */
        for (int x=0; x<6; x++) {
            for (int y=0; y<6; y++) {
                switch (b[x][y]) {
                    case Position.Empty: continue;

                    /* Kings are a special case, because capturing a king ends the game.
                     * Therefore, it doesn't matter which piece we sacrifice to get the king.
                     * CHECK_BONUS is always added to score. If n units are checking,
                     * the bonus is multiplied n^2 times. */
                    case Position.WKing:
                        defendColor = WHITE;
                        attackColor = BLACK;
                        n = sqControlCount[attackColor][x][y];
                        if (n > 0) {
                            double valve = COST_OF_TEMPO * n * n;
                            score[attackColor] += valve;
                            if (STATE == DEBUG) {
                                scoreAnalyzer[attackColor].add("Check bonus", "Number of checking units = " + n, valve);
                            }
                        }
                        continue;
                    case Position.BKing:
                        defendColor = BLACK;
                        attackColor = WHITE;
                        n = sqControlCount[attackColor][x][y];
                        if (n > 0) {
                            double valve = COST_OF_TEMPO * n * n;
                            score[attackColor] += valve;
                            if (STATE == DEBUG) {
                                scoreAnalyzer[attackColor].add("Check bonus", "Number of checking units = " + n, valve);
                            }
                        }
                        continue;


                    case Position.WQueen:
                        captureValue = PVqueen;
                        defendColor = WHITE;
                        attackColor = BLACK;
                        break;
                    case Position.WKnight:
                        defendColor = WHITE;
                        attackColor = BLACK;
                        captureValue = PVknight[pawnCount[attackColor]];
                        break;
                    case Position.WRook:
                        defendColor = WHITE;
                        attackColor = BLACK;
                        captureValue = PVrook[pawnCount[attackColor]];
                        break;
                    case Position.WPawn:
                        defendColor = WHITE;
                        attackColor = BLACK;
                        captureValue = PVpawn[pawnCount[defendColor]];
                        break;

                    case Position.BQueen:
                        captureValue = PVqueen;
                        defendColor = BLACK;
                        attackColor = WHITE;
                        break;
                    case Position.BKnight:
                        defendColor = BLACK;
                        attackColor = WHITE;
                        captureValue = PVknight[pawnCount[attackColor]];
                        break;
                    case Position.BRook:
                        defendColor = BLACK;
                        attackColor = WHITE;
                        captureValue = PVrook[pawnCount[attackColor]];
                        break;
                    case Position.BPawn:
                        defendColor = BLACK;
                        attackColor = WHITE;
                        captureValue = PVpawn[pawnCount[defendColor]];
                        break;

                    default:break;
                }

                int defendedCount = sqControlCount[defendColor][x][y];
                int attackedCount = sqControlCount[attackColor][x][y];

            /* Bonus for protection of this unit */
                if (defendedCount > 0) {
                /* When multiple units protect a square,
                 * don't give score for unneccessary protection.
                 * For example, if attacker has 4 and we have 7, give score for 5 (should it be 4 instead?) */
                    int multiplier = defendedCount;
                    if (defendedCount - attackedCount > 1) multiplier -= (defendedCount-attackedCount-1);

                /* Protecting low value units is more important than protecting high value units,
                 * because controlling a friendly square is essentially about re-capture. */
                    double limitedCaptureValue = Math.min(captureValue, PVrook[pawnCount[attackColor]] * 1.3);
                /* About limitedCaptureValue: because queen's value is boosted 5x to fix promotion
                 * related incentives, we'll cap it here to 1.3 times rook value. Otherwise we would
                 * get practically no bonus for controlling a friendly queen square. */
                    double prot = PROTECTION_BONUS * multiplier * 1.0 / limitedCaptureValue;
                    score[defendColor] += prot;
                    if (STATE == DEBUG) {
                        scoreAnalyzer[defendColor].add("Protection", "protected unit at " + x+","+y, prot);
                    }
                }

            /* Best ways to capture this unit */
                if (attackedCount > 0) {
                    double naiveValue = captureValue;

                /* Is it defended? */
                    if (defendedCount > 0) {
                    /* Naively assume it would be a single trade if we attacked with our weakest unit */
                        naiveValue -= sqControlLowVal[attackColor][x][y];
                    } else {
                    /* Undefended piece. We can capture it, but we lose tempo. */
                        // naiveValue -= COST_OF_TEMPO;
                    /* EXPERIMENT HERE */
                    /* Commented out because capturing undefended pieces in practice
                     * typicly lead to great score increases for the capturing side.
                     * We would prefer score to remain the same, since that is an expected move. */
                    }
                    /** Remember top 2 capture target values, and the top 1 coordinates */
                    if (naiveValue > bestCapValue[attackColor][1]) {
                        if (naiveValue > bestCapValue[attackColor][0]) {
                            bestCapValue[attackColor][1] = bestCapValue[attackColor][0];
                            bestCapValue[attackColor][0] = naiveValue;
                            bestCapCoordinates[attackColor][0] = x;
                            bestCapCoordinates[attackColor][1] = y;
                        } else {
                            bestCapValue[attackColor][1] = naiveValue;
                        }
                    }

                /*      count this opportunity even if naiveValue < 0         */
                    capAlt[attackColor] += attackedCount * captureValue;
                }
            }
        }
    }

    public void scoreMobility() {
        score[WHITE] += MOBILITY_BONUS * mobilityCount[WHITE];
        score[BLACK] += MOBILITY_BONUS * mobilityCount[BLACK];
    }

    /* Because of the earlier O(6x6) preprocessing, we are sometimes able to
     * predict captures 1-2 plies into the future in this O(1) method */
    public void scoreCaptures() {
        if (kingUnderCheck[sideToMove] == 0) {
        /* Award sideToMove the best naive capture value
         * This has to be a legal move, because:
         *      A. We are not under check
         *      B. We pruned out pins earlier */
            score[sideToMove] += bestCapValue[sideToMove][0];
            if (STATE == DEBUG) {
                scoreAnalyzer[sideToMove].add("Naive value for best IMMEDIATE capture", "--", bestCapValue[sideToMove][0]);
            }
        /* Award sideWaiting the 2nd best naive capturevalue
         * (some of these moves may be illegal, but looking
         *  ahead 2 plies this is a fine approximation) */
            score[sideWaiting] += bestCapValue[sideWaiting][1];
            if (STATE == DEBUG) {
                scoreAnalyzer[sideWaiting].add("Naive value for 2nd best capture", "--", bestCapValue[sideWaiting][1]);
            }
        /* Sidenote: if sideToMove has en pris capture of sideWaiting's unit
        *       X and X is threatening 2 valuable units of sideToMove, then
        *       sideWaiting will incorrectly gain score for his 2nd most
        *       valuable potential capture, which will never be realized. */
        } else {
        /* sideToMove is in check (and has been penalized with COST_OF_TEMPO earlier)
        /* Can we capture the checking piece and does it naively look like the best capture option? */
            if (bestCapCoordinates[sideToMove][0] == checkingUnit[sideToMove][0]
                    && bestCapCoordinates[sideToMove][1] == checkingUnit[sideToMove][1]) {
                double valueForSideToMove = bestCapValue[sideToMove][0];
            /* In that case sideWaiting might be able to realize its 2nd best capture */
                double valueForSideWaiting = bestCapValue[sideToMove][1];
                score[sideToMove] += valueForSideToMove;
                score[sideWaiting] += valueForSideWaiting;
                if (STATE == DEBUG) {
                    scoreAnalyzer[sideToMove].add("Naive value for best IMMEDIATE capture", "--", valueForSideToMove);
                    scoreAnalyzer[sideWaiting].add("Naive value for 2nd best capture", "--", valueForSideWaiting);
                }
            } else {
            /* If sideToMove cannot end the check by capturing the checking piece,
             * then sideWaiting should be able to realize its best potential capture
             * and sideToMove might be able to realize its 2nd best potential capture. */
                double valueForSideToMove = bestCapValue[sideToMove][1];
                double valueForSideWaiting = bestCapValue[sideWaiting][0];
                score[sideToMove] += valueForSideToMove;
                score[sideWaiting] += valueForSideWaiting;
                if (STATE == DEBUG) {
                    scoreAnalyzer[sideWaiting].add("Naive value for best IMMEDIATE capture", "--",valueForSideWaiting);
                    scoreAnalyzer[sideToMove].add("Naive value for 2nd best capture", "--", valueForSideToMove);
                }
            }

        }

    /* capAlt gives some score for, essentially, the sum of capture opportunities  */
    /* capAlt has the sum of [unit values multiplied by number of threats to those units] */
        score[WHITE] += CAPTURITY_BONUS * capAlt[WHITE];
        score[BLACK] += CAPTURITY_BONUS * capAlt[BLACK];
        if (STATE == DEBUG) {
            scoreAnalyzer[WHITE].add("Capturity bonus", "capAlt " + capAlt[WHITE], CAPTURITY_BONUS * capAlt[WHITE]);
            scoreAnalyzer[BLACK].add("Capturity bonus", "capAlt " + capAlt[BLACK], CAPTURITY_BONUS * capAlt[BLACK]);
        }

    }

    /* These tables are used everywhere.
     * sqControlCount answers how many units of a certain color are threatening/protecting a square
     * ..of those, sqControlLowVal gives you the material value of the lowest value unit */
    public void addControl(int color, double unitValue, int x, int y) {
        sqControlCount[color][x][y]++;
        sqControlLowVal[color][x][y] = Math.min(unitValue, sqControlLowVal[color][x][y]);
    }

    // <editor-fold defaultstate="collapsed" desc="king related">

    // <editor-fold defaultstate="collapsed" desc="generators (king related)">

    private void generateNeighboringSquaresLists() {
        neighboringSquares = new int[6][6][17];
        for (int x=0; x<6; x++) {
            for (int y=0; y<6; y++) {
                int pointer = 1;
                if (x-1 >= 0) {
                    neighboringSquares[x][y][pointer++] = x-1;
                    neighboringSquares[x][y][pointer++] = y;
                    if (y-1 >= 0) {
                        neighboringSquares[x][y][pointer++] = x-1;
                        neighboringSquares[x][y][pointer++] = y-1;
                    }
                    if (y+1 <= 5) {
                        neighboringSquares[x][y][pointer++] = x-1;
                        neighboringSquares[x][y][pointer++] = y+1;
                    }
                }
                if (x+1 <= 5) {
                    neighboringSquares[x][y][pointer++] = x+1;
                    neighboringSquares[x][y][pointer++] = y;
                    if (y-1 >= 0) {
                        neighboringSquares[x][y][pointer++] = x+1;
                        neighboringSquares[x][y][pointer++] = y-1;
                    }
                    if (y+1 <= 5) {
                        neighboringSquares[x][y][pointer++] = x+1;
                        neighboringSquares[x][y][pointer++] = y+1;
                    }
                }
                if (y-1 >= 0) {
                    neighboringSquares[x][y][pointer++] = x;
                    neighboringSquares[x][y][pointer++] = y-1;
                }
                if (y+1 <= 5) {
                    neighboringSquares[x][y][pointer++] = x;
                    neighboringSquares[x][y][pointer++] = y+1;
                }
                neighboringSquares[x][y][0] = pointer;
            }
        }
    }

    /** first array by multiplier (number of threatening units)
     *  second array by piececount; more penalty in early game, no penalty in end game */
    private void generateEnemyControlNearKingPenalty() {
        enemyControlNearKingPenalty = new double[13][25];
        double penalty = 0.3;
        for (int i=7; i<25; i++) {
            enemyControlNearKingPenalty[1][i] = penalty;
            enemyControlNearKingPenalty[2][i] = 3*penalty;
            enemyControlNearKingPenalty[3][i] = 5*penalty;
            enemyControlNearKingPenalty[4][i] = 7*penalty;
            for (int j=5; j<=12; j++) {
                enemyControlNearKingPenalty[j][i] = (j+3) * penalty;
            }
            penalty += 0.5;
        }
    }

    // </editor-fold>

    public void markKingLocation(int color, int x, int y) {
        kingLives[color][0] = 1337; // [x] king lives
        kingLives[color][1] = x;
        kingLives[color][2] = y;
    }

    /* Coordinates are for the attacking unit. */
    public void markCheck(int color, int x, int y) {
        kingUnderCheck[color]++;
    /* If multiple units are checking, overwrites coordinates */
        checkingUnit[color][0] = x;
        checkingUnit[color][1] = y;
    }

    /** Discover checks and pins (excluding diagonal pins from a queen) */
    public void preProcessKing(int color, int ax, int ay) {
        double pstValue = pst[color][KING][ax][ay][pieceCount];
        score[color] += pstValue;
        if (STATE == DEBUG) scoreAnalyzer[color].add("PST", "KING", pstValue);
        int enemyColor = (color+1)%2;

    /* Mark which units are pinned
     *     Friendly unit is pinned -> It has fewer moves
     *     Hostile unit is pinned -> bonus for enemy
     * Also mark if king is being checked */

    /* Is a pawn checking us? */
        switch (color) {
            case WHITE: {
                int y = ay + 1;
                if (y <= 5) {
                    int x = ax - 1;
                    if (x >= 0 && b[x][y] == Position.BPawn) markCheck(color,x,y);
                    x = ax + 1;
                    if (x <= 5 && b[x][y] == Position.BPawn) markCheck(color,x,y);
                }
                break;
            }
            case BLACK: {
                int y = ay - 1;
                if (y >= 0) {
                    int x = ax - 1;
                    if (x >= 0 && b[x][y] == Position.WPawn) markCheck(color,x,y);
                    x = ax + 1;
                    if (x <= 5 && b[x][y] == Position.WPawn) markCheck(color,x,y);
                }
                break;
            }
            default:
                break;
        }

    /* Is a knight checking us? */
        int nmyKnight = (color == WHITE ? Position.BKnight : Position.WKnight);
        for (int i=1; i<knightMoveLists[ax][ay][0];) {
            int x = knightMoveLists[ax][ay][i++];
            int y = knightMoveLists[ax][ay][i++];
            if (b[x][y] == nmyKnight) markCheck(color,x,y);
        }

    /* Scan to the left for rooks/queens */
        int sx = -1;
        int sy = -1;
        int x = ax-1;
        int y = ay;
        for (; x>=0; x--) {
            if (b[x][y] == Position.Empty) continue;
            int unit = b[x][y];
            if (sx < 0) {
                /** First unit in this direction (later find out if it's pinned) */
                sx = x;
                sy = y;
                if (isEnemyRookOrQueen[color][unit]) markCheck(color,x,y);
            } else {
            /* Second unit in this direction */
                if (!isEnemyRookOrQueen[color][unit]) break;
                int pinnedUnit = b[sx][sy]; /* First unit must be pinned */
                if (isFriendly[color][pinnedUnit]) {
                    pinned[sx][sy][0] = 1337; // Unit at sx,sy is pinned
                    pinned[sx][sy][1] = x;    // x of threat
                    pinned[sx][sy][2] = y;    // y of threat
                } else {
                    score[enemyColor] += HOSTILE_PIN_BONUS;
                    if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("Hostile PIN", "Unit at " + sx+","+sy, HOSTILE_PIN_BONUS);
                }
                break;
            }
        }

    /* Scan to the right for rooks/queens */
        sx = -1;
        sy = -1;
        x = ax + 1;
        y = ay;
        for (; x<=5; x++) {
            /** COPYPASTED FROM THE 1ST BLOCK FOR EFFICIENCY REASONS */
            if (b[x][y] == Position.Empty) continue;
            int unit = b[x][y];
            if (sx < 0) {
                sx = x;
                sy = y;
                if (isEnemyRookOrQueen[color][unit]) markCheck(color,x,y);
            } else {
                if (!isEnemyRookOrQueen[color][unit]) break;
                int pinnedUnit = b[sx][sy];
                if (isFriendly[color][pinnedUnit]) {
                    pinned[sx][sy][0] = 1337;
                    pinned[sx][sy][1] = x;
                    pinned[sx][sy][2] = y;
                } else {
                    score[enemyColor] += HOSTILE_PIN_BONUS;
                    if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("Hostile PIN", "Unit at " + sx+","+sy, HOSTILE_PIN_BONUS);
                }
                break;
            }
        }

        sx = -1;
        sy = -1;
        x = ax;
        y = ay - 1;
        for (; y>=0; y--) {
            /** COPYPASTED FROM THE 1ST BLOCK FOR EFFICIENCY REASONS */
            if (b[x][y] == Position.Empty) continue;
            int unit = b[x][y];
            if (sx < 0) {
                sx = x;
                sy = y;
                if (isEnemyRookOrQueen[color][unit]) markCheck(color,x,y);
            } else {
                if (!isEnemyRookOrQueen[color][unit]) break;
                int pinnedUnit = b[sx][sy];
                if (isFriendly[color][pinnedUnit]) {
                    pinned[sx][sy][0] = 1337;
                    pinned[sx][sy][1] = x;
                    pinned[sx][sy][2] = y;
                } else {
                    score[enemyColor] += HOSTILE_PIN_BONUS;
                    if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("Hostile PIN", "Unit at " + sx+","+sy, HOSTILE_PIN_BONUS);
                }
                break;
            }
        }

        sx = -1;
        sy = -1;
        x = ax;
        y = ay + 1;
        for (; y<=5; y++) {
            /** COPYPASTED FROM THE 1ST BLOCK FOR EFFICIENCY REASONS */
            if (b[x][y] == Position.Empty) continue;
            int unit = b[x][y];
            if (sx < 0) {
                sx = x;
                sy = y;
                if (isEnemyRookOrQueen[color][unit]) markCheck(color,x,y);
            } else {
                if (!isEnemyRookOrQueen[color][unit]) break;
                int pinnedUnit = b[sx][sy];
                if (isFriendly[color][pinnedUnit]) {
                    pinned[sx][sy][0] = 1337;
                    pinned[sx][sy][1] = x;
                    pinned[sx][sy][2] = y;
                } else {
                    score[enemyColor] += HOSTILE_PIN_BONUS;
                    if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("Hostile PIN", "Unit at " + sx+","+sy, HOSTILE_PIN_BONUS);
                }
                break;
            }
        }
    }

    /** King's control, mobility, enemy control in king adjacent squares */
    public void postProcessKing(int color, int unitId) {
        int enemyColor = (color+1)%2;
        int ax = kingLives[color][1];
        int ay = kingLives[color][2];

        int availableMoves = 0;
        for (int i=1; i<neighboringSquares[ax][ay][0];) {
            int x = neighboringSquares[ax][ay][i++];
            int y = neighboringSquares[ax][ay][i++];
            int sq = b[x][y];
            int nmyControl = sqControlCount[enemyColor][x][y];
            int ourControl = sqControlCount[color][x][y];

            /** King offers some protection/threat to neighboring squares
             * if square is not overpowered by enemy's control */
            if (nmyControl - ourControl <= 1) addControl(color, PVking, x, y);

            /** Penalty for enemy control in king adjacent square */
            if (nmyControl > 0) {
                double penalty = enemyControlNearKingPenalty[nmyControl][pieceCount];
                score[enemyColor] += penalty;
                if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("Near enemy king", "Control over sq " + x+","+y, penalty);
                continue; /* King is unable to move into this sq */
            }
        /* Possible move if sq is empty or undefended enemy piece */
            if (!isFriendly[color][sq]) availableMoves++;
        }

        if (availableMoves == 0) {
            score[color] -= IMMOBILIZED_KING_PENALTY;
            if (STATE == DEBUG) scoreAnalyzer[color].add("Mobility", "Immobilized king penalty", -IMMOBILIZED_KING_PENALTY);
        } else {
            mobilityCount[color] += availableMoves;
            if (STATE == DEBUG) scoreAnalyzer[color].add("Mobility", "By king at " + ax+","+ay, MOBILITY_BONUS * availableMoves);
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="rook/queen">

    private void preProcessRook(int color, int ax, int ay) {
        int enemyColor = (color+1)%2;
        double materialValue = PVrook[pawnCount[enemyColor]];
        score[color] += materialValue;
        score[color] += pst[color][ROOK][ax][ay][pieceCount];
        if (STATE == DEBUG) {
            scoreAnalyzer[color].add("Material", "ROOK", materialValue);
            scoreAnalyzer[color].add("PST", "ROOK", pst[color][ROOK][ax][ay][pieceCount]);
        }

        if (pinned[ax][ay][0] != 0) {
            int x = pinned[ax][ay][1];
            int y = pinned[ax][ay][2];
        /* Pinned between enemy at [x,y] and king. Can we capture it? */
            if (x == ax || y == ay) {
                mobilityCount[color]++;
                if (STATE == DEBUG) scoreAnalyzer[color].add("Mobility", "By unit at " + ax+","+ay + " to square " + x+","+y, MOBILITY_BONUS);
                addControl(color, materialValue, x, y);
            }
            return; /* Note: additional allowed moves may exist... */
        }
        addHorizontalVerticalControlsAndMobility(color, materialValue, ax, ay);
    }

    private void preProcessQueen(int color, int ax, int ay) {
        score[color] += PVqueen;
        score[color] += pst[color][QUEEN][ax][ay][pieceCount];
        if (STATE == DEBUG) {
            scoreAnalyzer[color].add("Material", "QUEEN", PVqueen);
            scoreAnalyzer[color].add("PST", "QUEEN", pst[color][QUEEN][ax][ay][pieceCount]);
        }

        if (pinned[ax][ay][0] != 0) {
            int x = pinned[ax][ay][1];
            int y = pinned[ax][ay][2];
        /* Pinned between enemy at [x,y] and king. Queen can capture it. */
            addControl(color, PVqueen, x, y);
            mobilityCount[color]++;
            if (STATE == DEBUG) scoreAnalyzer[color].add("Mobility", "By unit at " + ax+","+ay + " to square " + x+","+y, MOBILITY_BONUS);
        /* TODO: Can we check with this capture? */

            return; /* Note: additional allowed moves may exist... */
        }
        addHorizontalVerticalControlsAndMobility(color, PVqueen, ax, ay);
        diagonalControlsAndMobilityAndChecks(color, PVqueen, ax, ay);
    }

    /** If target sq is not threatened by enemy pawn, counts towards mobility. */
    private void addMobility(int color, int ax, int ay, int x, int y) {
        int nmyColor = (color+1)%2;
        if (sqControlLowVal[nmyColor][x][y] == PVpawn[pawnCount[nmyColor]]) {
            return;
        }
        mobilityCount[color]++;
        if (STATE == DEBUG) scoreAnalyzer[color].add("Mobility", "By unit at " + ax+","+ay + " to square " + x+","+y, MOBILITY_BONUS);
    }

    private void addHorizontalVerticalControlsAndMobility(int color, double unitValue, int ax, int ay) {
        int x = ax-1;
        int y = ay;
        for (; x>=0; x--) {
            int sq = b[x][y];
            addControl(color, unitValue, x, y);
            if (isFriendly[color][sq]) break;
            addMobility(color, ax, ay, x, y);
            if (sq != Position.Empty) break;
        }
        x = ax+1;
        y = ay;
        for (; x<=5; x++) {
            int sq = b[x][y];
            addControl(color, unitValue, x, y);
            if (isFriendly[color][sq]) break;
            addMobility(color, ax, ay, x, y);
            if (sq != Position.Empty) break;
        }
        x = ax;
        y = ay - 1;
        for (; y>=0; y--) {
            int sq = b[x][y];
            addControl(color, unitValue, x, y);
            if (isFriendly[color][sq]) break;
            addMobility(color, ax, ay, x, y);
            if (sq != Position.Empty) break;
        }
        x = ax;
        y = ay + 1;
        for (; y<=5; y++) {
            int sq = b[x][y];
            addControl(color, unitValue, x, y);
            if (isFriendly[color][sq]) break;
            addMobility(color, ax, ay, x, y);
            if (sq != Position.Empty) break;
        }
    }

    /* TODO: Units pinned by this queen */
    private void diagonalControlsAndMobilityAndChecks(int color, double unitValue, int ax, int ay) {
        int enemyKing = (color == WHITE ? Position.BKing : Position.WKing);
        int enemyColor = (color+1)%2;
        int x = ax+1;
        int y = ay+1;
        while (x <= 5 && y <= 5) {
            int sq = b[x][y];
            addControl(color, unitValue, x, y);
            if (isFriendly[color][sq]) break;
            addMobility(color, ax, ay, x, y);
            if (isEnemy[color][sq]) {
                if (sq == enemyKing) markCheck(enemyColor,x,y);
                break;
            }
            x++;
            y++;
        }
        x = ax-1;
        y = ay-1;
        while (x >= 0 && y >= 0) {
            int sq = b[x][y];
            addControl(color, unitValue, x, y);
            if (isFriendly[color][sq]) break;
            addMobility(color, ax, ay, x, y);
            if (isEnemy[color][sq]) {
                if (sq == enemyKing) markCheck(enemyColor,x,y);
                break;
            }
            x--;
            y--;
        }
        x = ax+1;
        y = ay-1;
        while (x <= 5 && y >= 0) {
            int sq = b[x][y];
            addControl(color, unitValue, x, y);
            if (isFriendly[color][sq]) break;
            addMobility(color, ax, ay, x, y);
            if (isEnemy[color][sq]) {
                if (sq == enemyKing) markCheck(enemyColor,x,y);
                break;
            }
            x++;
            y--;
        }
        x = ax-1;
        y = ay+1;
        while (x >= 0 && y <= 5) {
            int sq = b[x][y];
            addControl(color, unitValue, x, y);
            if (isFriendly[color][sq]) break;
            addMobility(color, ax, ay, x, y);
            if (isEnemy[color][sq]) {
                if (sq == enemyKing) markCheck(enemyColor,x,y);
                break;
            }
            x--;
            y++;
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="knight">

    private void preProcessKnight(int color, int ax, int ay) {
        int enemyColor = (color+1)%2;
        double materialValue = PVknight[pawnCount[enemyColor]];
        score[color] += materialValue;
        score[color] += pst[color][KNIGHT][ax][ay][pieceCount];
        if (STATE == DEBUG) {
            scoreAnalyzer[color].add("Material", "KNIGHT", materialValue);
            scoreAnalyzer[color].add("PST", "KNIGHT", pst[color][KNIGHT][ax][ay][pieceCount]);
        }

        if (pinned[ax][ay][0] != 0) return; /* Assume no eligible moves when pinned */
        for (int i=1; i<knightMoveLists[ax][ay][0];) {
            int x = knightMoveLists[ax][ay][i++];
            int y = knightMoveLists[ax][ay][i++];
            addControl(color, materialValue, x, y);
            if (!isFriendly[color][b[x][y]]) {
                addMobility(color, ax, ay, x, y);
            }
        }
    }

    private void generateKnightMoveLists() {
        knightMoveLists = new int[6][6][8*2+1];
        for (int x=0; x<6; x++) {
            for (int y=0; y<6; y++) {
                int pointer = 1;
                if (x-1 >= 0) {
                    if (y-2 >= 0) {
                        knightMoveLists[x][y][pointer++] = x-1;
                        knightMoveLists[x][y][pointer++] = y-2;
                    }
                    if (y+2 <= 5) {
                        knightMoveLists[x][y][pointer++] = x-1;
                        knightMoveLists[x][y][pointer++] = y+2;
                    }
                    if (x-2 >= 0) {
                        if (y-1 >= 0) {
                            knightMoveLists[x][y][pointer++] = x-2;
                            knightMoveLists[x][y][pointer++] = y-1;
                        }
                        if (y+1 <= 5) {
                            knightMoveLists[x][y][pointer++] = x-2;
                            knightMoveLists[x][y][pointer++] = y+1;
                        }
                    }
                }
                if (x+1 <= 5) {
                    if (y-2 >= 0) {
                        knightMoveLists[x][y][pointer++] = x+1;
                        knightMoveLists[x][y][pointer++] = y-2;
                    }
                    if (y+2 <= 5) {
                        knightMoveLists[x][y][pointer++] = x+1;
                        knightMoveLists[x][y][pointer++] = y+2;
                    }
                    if (x+2 <= 5) {
                        if (y-1 >= 0) {
                            knightMoveLists[x][y][pointer++] = x+2;
                            knightMoveLists[x][y][pointer++] = y-1;
                        }
                        if (y+1 <= 5) {
                            knightMoveLists[x][y][pointer++] = x+2;
                            knightMoveLists[x][y][pointer++] = y+1;
                        }
                    }
                }
                knightMoveLists[x][y][0] = pointer;
            }
        }
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="pawn">

    private void preProcessPawn(int color, int ax, int ay) {
        pawnCoordinates[color][pawnPointer[color]][0] = ax;
        pawnCoordinates[color][pawnPointer[color]][1] = ay;
        pawnPointer[color]++;

        pawnLanes[color][ax]++; /* +1 pawn this lane */
        score[color] += pst[color][PAWN][ax][ay][pieceCount];
        if (STATE == DEBUG) scoreAnalyzer[color].add("PST", "PAWN", pst[color][PAWN][ax][ay][pieceCount]);

        if (pinned[ax][ay][0] != 0) {
            /** TODO: eligible moves when pinned */
            blockedPawns[color]++;
            if (STATE == DEBUG) scoreAnalyzer[color].add("Pawns", "Pinned pawn at " + ax+","+ay, -BLOCKED_PAWN_PENALTY);
            return;
        }

        int ahead = (color == BLACK ? -1 : 1);
        /** Move ahead possible? */
        if (b[ax][ay+ahead] == Position.Empty) {
            mobilityCount[color]++;
            if (STATE == DEBUG) scoreAnalyzer[color].add("Mobility", "By pawn at " + ax+","+ay + " to square " + ax+","+(ay+ahead), MOBILITY_BONUS);
        } else {
            blockedPawns[color]++;
            if (STATE == DEBUG) scoreAnalyzer[color].add("Pawns", "Blocked pawn at " + ax+","+ay, -BLOCKED_PAWN_PENALTY);
        }

        /** Squares threatened by this pawn */
        /** Lowest denominated unit, so we can just overwrite lowVal */
        int y = ay + ahead;

        int x = ax - 1;
        if (x >= 0) {
            sqControlCount[color][x][y]++;
            sqControlLowVal[color][x][y] = PVpawn[pawnCount[color]];
            if (isEnemy[color][b[x][y]]) {
                mobilityCount[color]++;
                if (STATE == DEBUG) scoreAnalyzer[color].add("Mobility", "By pawn at " + ax+","+ay + " to HOSTILE square " + x+","+y, MOBILITY_BONUS);
            }
        }
        x = ax + 1;
        if (x <= 5) {
            sqControlCount[color][x][y]++;
            sqControlLowVal[color][x][y] = PVpawn[pawnCount[color]];
            if (isEnemy[color][b[x][y]]) {
                if (STATE == DEBUG) scoreAnalyzer[color].add("Mobility", "By pawn at " + ax+","+ay + " to HOSTILE square " + x+","+y, MOBILITY_BONUS);
                mobilityCount[color]++;
            }
        }
    /* TODO: Somehow award pawns for threatening empty squares
     * (other units already get mobility bonus for all squares they control) */
    /* ---> Remove officers' mobility bonus from EMPTY squares threatened by pawns? */
    }

    /** Pawns: score passed, isolated, doubled, blocked */
    private void postProcessPawns(int color) {
        double pieceValues = PVpawnsTotal[pawnCount[color]];
        score[color] += pieceValues;
        if (STATE == DEBUG) scoreAnalyzer[color].add("Material", "All pawns", pieceValues);

        boolean prevLaneHadPawns = false;
        for (int lane=0; lane<=5; lane++) {
            boolean thisLaneHasPawns = (pawnLanes[color][lane] > 0);
            if (thisLaneHasPawns) {
            /* Isolated? */
                if (!prevLaneHadPawns && pawnLanes[color][lane+1] == 0) {
                    score[color] -= ISOLATED_PAWN_PENALTY;
                    if (STATE == DEBUG) scoreAnalyzer[color].add("Pawns", "Isolated pawn penalty", -ISOLATED_PAWN_PENALTY);
                }
            /* Doubled? */
                if (pawnLanes[color][lane] > 1) {
                    score[color] -= DOUBLED_PAWNS_PENALTY;
                    if (STATE == DEBUG) scoreAnalyzer[color].add("Pawns", "Doubled pawns penalty", -DOUBLED_PAWNS_PENALTY);
                }
            }
            prevLaneHadPawns = thisLaneHasPawns;
        }
    /* Passed? */
        int passedCount = 0;
        int ahead = (color == WHITE ? +1 : -1);
        for (int i=0; i<pawnCount[color]; i++) {
            int x = pawnCoordinates[color][i][0];
            int y = pawnCoordinates[color][i][1];
            if (passedPawn(color,x,y,ahead)) passedCount++;
        }
        score[color] += PASSED_PAWN_BONUS * passedCount;

    /* Blocked? */
        score[color] -= BLOCKED_PAWN_PENALTY * blockedPawns[color];
    }

    /** Returns true if squares ahead of pawn are not threatened or occupied by enemy. */
    private boolean passedPawn(int color, int x, int startY, int ahead) {
        int enemyColor = (color+1)%2;
        int y = startY+ahead;
        while (y >= 0 && y < 6) {
            int sq = b[x][y];
            if (isEnemy[color][sq]) return false;
            if (sqControlCount[enemyColor][x][y] > 0) return false;
            y += ahead;
        }
        if (STATE == DEBUG) scoreAnalyzer[color].add("Pawns", "Passed pawn bonus from " +x+","+startY, PASSED_PAWN_BONUS);
        return true;
    }

    // </editor-fold>

    /** Reference: [COLOR][UNIT][x][y][pieceCount]. */
    private void generatePieceSquareTables() {
        pst = new double[2][7][6][6][25];

    /* PAWNS, first row: */
        pst[WHITE][PAWN][0][1][24] = 5;
        pst[WHITE][PAWN][1][1][24] = 10;
        pst[WHITE][PAWN][2][1][24] = 0; /* central paawns: 0->20 incentive */
        pst[WHITE][PAWN][3][1][24] = 0;
        pst[WHITE][PAWN][4][1][24] = 10;
        pst[WHITE][PAWN][5][1][24] = 5; /* corner pawns: 5->8 incentive */
    /* PAWNS, second row */
        pst[WHITE][PAWN][0][2][24] = 8;
        pst[WHITE][PAWN][1][2][24] = 15;
        pst[WHITE][PAWN][2][2][24] = 20;
        pst[WHITE][PAWN][3][2][24] = 20;
        pst[WHITE][PAWN][4][2][24] = 15;
        pst[WHITE][PAWN][5][2][24] = 8;
    /* PAWNS, third row */
        pst[WHITE][PAWN][0][3][24] = 15;
        pst[WHITE][PAWN][1][3][24] = 30;
        pst[WHITE][PAWN][2][3][24] = 60;
        pst[WHITE][PAWN][3][3][24] = 60;
        pst[WHITE][PAWN][4][3][24] = 30;
        pst[WHITE][PAWN][5][3][24] = 15;
    /* PAWNS, 1 step from promotion (at 24 pieceCount, which is not
     * possible, but these values are used as reference for other pieceCounts */
        pst[WHITE][PAWN][0][4][24] = 40;
        pst[WHITE][PAWN][1][4][24] = 80;
        pst[WHITE][PAWN][2][4][24] = 130;
        pst[WHITE][PAWN][3][4][24] = 130;
        pst[WHITE][PAWN][4][4][24] = 80;
        pst[WHITE][PAWN][5][4][24] = 40;

    /* PAWNS: pieceCounts 23 -> 0 */
        for (int pc=23; pc>=0; pc--) {
            for (int y=0; y<6; y++) {
            /* Advantage from central pawns should dissipate towards the endgame */
            /* Multipliers result in approximately 340 PST value per endgame pawn near promotion,
            /* about the same for all lanes */
                pst[WHITE][PAWN][0][y][pc] = pst[WHITE][PAWN][0][y][pc+1] * 1.11;
                pst[WHITE][PAWN][1][y][pc] = pst[WHITE][PAWN][1][y][pc+1] * 1.08;
                pst[WHITE][PAWN][2][y][pc] = pst[WHITE][PAWN][2][y][pc+1] * 1.047;
                pst[WHITE][PAWN][3][y][pc] = pst[WHITE][PAWN][3][y][pc+1] * 1.047;
                pst[WHITE][PAWN][4][y][pc] = pst[WHITE][PAWN][4][y][pc+1] * 1.08;
                pst[WHITE][PAWN][5][y][pc] = pst[WHITE][PAWN][5][y][pc+1] * 1.11;
            }
        }

    /* KNIGHTS, 0-24pc: */
        int pc=24;
    /* KNIGHTS: Discourage corners */
        pst[WHITE][KNIGHT][0][0][pc] = -50;
        pst[WHITE][KNIGHT][1][1][pc] = -20;
        pst[WHITE][KNIGHT][5][5][pc] = -50;
        pst[WHITE][KNIGHT][4][4][pc] = -20;
        pst[WHITE][KNIGHT][0][5][pc] = -50;
        pst[WHITE][KNIGHT][1][4][pc] = -20;
        pst[WHITE][KNIGHT][5][0][pc] = -50;
        pst[WHITE][KNIGHT][4][1][pc] = -20;
    /* KNIGHTS: Discourage LEFT SIDE EDGE */
        pst[WHITE][KNIGHT][0][1][pc] = -40;
        pst[WHITE][KNIGHT][0][2][pc] = -30;
        pst[WHITE][KNIGHT][0][3][pc] = -30;
        pst[WHITE][KNIGHT][0][4][pc] = -40;
    /* KNIGHTS: Discourage RIGHT SIDE EDGE */
        pst[WHITE][KNIGHT][5][1][pc] = -40;
        pst[WHITE][KNIGHT][5][2][pc] = -30;
        pst[WHITE][KNIGHT][5][3][pc] = -30;
        pst[WHITE][KNIGHT][5][4][pc] = -40;
    /* KNIGHTS: Discourage OPPONENT'S EDGE */
        pst[WHITE][KNIGHT][1][5][pc] = -40;
        pst[WHITE][KNIGHT][2][5][pc] = -30;
        pst[WHITE][KNIGHT][3][5][pc] = -30;
        pst[WHITE][KNIGHT][4][5][pc] = -40;
    /* KNIGHTS: Discourage own edge (except knight starting positions) */
        pst[WHITE][KNIGHT][2][0][pc] = -30;
        pst[WHITE][KNIGHT][3][0][pc] = -30;
    /* KNIGHTS: Encourage 2nd row center */
        pst[WHITE][KNIGHT][3][1][pc] = 10;
        pst[WHITE][KNIGHT][2][1][pc] = 10;
    /* KNIGHTS, copy 24pc setup to 23->0pc */
        for (int y=0; y<6; y++) {
            for (int x=0; x<6; x++) {
                for (pc=23; pc>=0; pc--) {
                    pst[WHITE][KNIGHT][x][y][pc] = pst[WHITE][KNIGHT][x][y][24];
                }
            }
        }

    /* KNIGHTS, early game 21-24pc overwrite: */
        for (pc=24; pc>=21; pc--) {
        /* Stay back to avoid whipsawing */
            pst[WHITE][KNIGHT][1][0][pc] = 10;
            pst[WHITE][KNIGHT][4][0][pc] = 10;
        /* Increased bonus for 2nd row center */
            pst[WHITE][KNIGHT][2][1][pc] = 20;
            pst[WHITE][KNIGHT][3][1][pc] = 20;
        }
    /* KNIGHTS, 20pc->0pc no more stayback-bonuses */

    /* KNIGHTS, 19pc down: Smooth transition into penalties for staying put */
        pst[WHITE][KNIGHT][1][0][19] = -10;
        pst[WHITE][KNIGHT][4][0][19] = -10;
        pst[WHITE][KNIGHT][1][0][18] = -20;
        pst[WHITE][KNIGHT][4][0][18] = -20;
        pst[WHITE][KNIGHT][1][0][17] = -30;
        pst[WHITE][KNIGHT][4][0][17] = -30;
        for (pc=16; pc>=0; pc--) {
            pst[WHITE][KNIGHT][1][0][pc] = -40;
            pst[WHITE][KNIGHT][4][0][pc] = -40;
        }
    /* KNIGHTS, 19pc down: Smooth transition into larger center bonus */
        pst[WHITE][KNIGHT][2][3][19] = 10;
        pst[WHITE][KNIGHT][2][2][19] = 10;
        pst[WHITE][KNIGHT][3][3][19] = 10;
        pst[WHITE][KNIGHT][3][2][19] = 10;
        pst[WHITE][KNIGHT][2][3][18] = 15;
        pst[WHITE][KNIGHT][2][2][18] = 15;
        pst[WHITE][KNIGHT][3][3][18] = 15;
        pst[WHITE][KNIGHT][3][2][18] = 15;
        for (pc=17; pc>=0; pc--) {
            pst[WHITE][KNIGHT][2][3][pc] = 20;
            pst[WHITE][KNIGHT][2][2][pc] = 20;
            pst[WHITE][KNIGHT][3][3][pc] = 20;
            pst[WHITE][KNIGHT][3][2][pc] = 20;
        }

    /* QUEEN: bonus for staying back in early game (smooth transition) */
        for (int x=0; x<6; x++) {
            pst[WHITE][QUEEN][x][1][24] = 20;
            pst[WHITE][QUEEN][x][0][24] = 20;
            pst[WHITE][QUEEN][x][1][23] = 16;
            pst[WHITE][QUEEN][x][0][23] = 16;
            pst[WHITE][QUEEN][x][1][22] = 12;
            pst[WHITE][QUEEN][x][0][22] = 12;
            pst[WHITE][QUEEN][x][1][21] = 8;
            pst[WHITE][QUEEN][x][0][21] = 8;
            pst[WHITE][QUEEN][x][1][20] = 6;
            pst[WHITE][QUEEN][x][0][20] = 6;
            pst[WHITE][QUEEN][x][1][19] = 4;
            pst[WHITE][QUEEN][x][0][19] = 4;
            pst[WHITE][QUEEN][x][1][18] = 2;
            pst[WHITE][QUEEN][x][0][18] = 2;
        }

    /* QUEEN & ROOK: bonuses for occupying enemy ranks */
        for (pc=24; pc>=0; pc--) {
            for (int x=0; x<6; x++) {
                pst[WHITE][QUEEN][x][4][pc] = 20; /* "7th rank" > "8th" */
                pst[WHITE][ROOK][x][4][pc] = 20;
                pst[WHITE][QUEEN][x][5][pc] = 10;
                pst[WHITE][ROOK][x][5][pc] = 10;
            }
        }

    /* KING: bonus for staying back until endgame */
        for (pc=24; pc>=10; pc--) {
            for (int x=0; x<6; x++) {
                pst[WHITE][KING][x][0][pc] = 20;
                pst[WHITE][KING][x][1][pc] = 7;
            }
        }
    /* KING: smooth transition into endgame */
        for (int x=0; x<6; x++) {
            pst[WHITE][KING][x][0][9] = 12;
            pst[WHITE][KING][x][1][9] = 4;
            pst[WHITE][KING][x][0][8] = 6;
            pst[WHITE][KING][x][1][8] = 2;
        }

    /* TODO: King's endgame */


    /* BLACK: replicate all from white, flip y's */
        for (pc=24; pc>=0; pc--) {
            for (int y=0; y<6; y++) {
                for (int x=0; x<6; x++) {
                    for (int unit=0; unit<7; unit++) {
                        pst[BLACK][unit][x][y][pc] = pst[WHITE][unit][x][5-y][pc];
                    }
                }
            }
        }


    /* Draw some PST's */
        if (true) return;
        int color = WHITE;
        int unit = PAWN;
        for (pc=24; pc>=0; pc--) {
            double[][] copy = new double[6][6];
            for (int y=0; y<6; y++) {
                for (int x=0; x<6; x++) {
                    copy[5-y][x] = pst[color][unit][x][y][pc];
                }
            }
            System.out.println("*********************");
            System.out.println("   At piececount " + pc);
            draw(copy);
        }

    }

    private void generateTrivialLookupTables() {
        isEnemyRookOrQueen = new boolean[2][13];
        isEnemyRookOrQueen[WHITE][8] = true;
        isEnemyRookOrQueen[WHITE][9] = true;
        isEnemyRookOrQueen[BLACK][2] = true;
        isEnemyRookOrQueen[BLACK][3] = true;

        isFriendly = new boolean[2][13];
        for (int i=1; i<=6; i++) isFriendly[WHITE][i] = true;
        for (int i=7; i<=12; i++) isFriendly[BLACK][i] = true;

        isEnemy = new boolean[2][13];
        for (int i=1; i<=6; i++) isEnemy[BLACK][i] = true;
        for (int i=7; i<=12; i++) isEnemy[WHITE][i] = true;

        PVpawn = new double[7];
        PVpawn[6] = 94;
        PVpawn[5] = 96;
        PVpawn[4] = 98;
        PVpawn[3] = 101;
        PVpawn[2] = 107;
        PVpawn[1] = 120;

    /* Knight has more value when opponent has more pawns */
        PVknight = new double[7];
        PVknight[6] = 315;
        PVknight[5] = 310;
        PVknight[4] = 305;
        PVknight[3] = 300;
        PVknight[2] = 295;
        PVknight[1] = 290;
        PVknight[0] = 280;

    /* Rook has more value when opponent has less pawns */
        PVrook = new double[7];
        PVrook[6] = 500;
        PVrook[5] = 505;
        PVrook[4] = 512;
        PVrook[3] = 522;
        PVrook[2] = 536;
        PVrook[1] = 550;
        PVrook[0] = 570;


        PVpawnsTotal = new double[7];
        for (int i=1; i<=6; i++) PVpawnsTotal[i] = PVpawnsTotal[i-1] + PVpawn[i];
    }

    public void reWeightMaterialScores(double weight) {
        PVqueen *= weight;
        PVking *= weight;
        for (int i=0; i<PVknight.length; i++) PVknight[i] *= weight;
        for (int i=0; i<PVpawn.length; i++) PVpawn[i] *= weight;
        for (int i=0; i<PVrook.length; i++) PVrook[i] *= weight;
    }

    public void reWeightKQscores(double weight) {
        PVqueen *= weight;
        PVking *= weight;
    }

    public void reWeightPST(double weight) {
        for (int pc=24; pc>=0; pc--) {
            for (int y=0; y<6; y++) {
                for (int x=0; x<6; x++) {
                    for (int unit=0; unit<7; unit++) {
                        for (int color=0; color<=1; color++) {
                            pst[color][unit][x][y][pc] = pst[color][unit][x][y][pc] * weight;
                        }
                    }
                }
            }
        }
    }

    private void initializeSqControl() {
        sqControlCount = new int[2][6][6];
        sqControlLowVal = new double[2][6][6];
        for (int color=0; color<=1; color++) {
            for (int x=0; x<6; x++) {
                for (int y=0; y<6; y++) {
                    sqControlLowVal[color][x][y] = Double.POSITIVE_INFINITY;
                }
            }
        }
    }








/* Debugging helper methods below */

    void analyzeScores() {
        System.out.println("Side to move: " + sideToMove);
        System.out.println("*************************");
        System.out.println("      WHITE SCORE");
        if (kingUnderCheck[WHITE] > 0) {
            int x = checkingUnit[WHITE][0];
            int y = checkingUnit[WHITE][1];
            System.out.println("White king under check by unit in " + x + ","+ y);
        }
        draw(sqControlCount[WHITE]);
        System.out.println("*************************");
        System.out.println(scoreAnalyzer[1].toString());
        System.out.println("*************************");
        System.out.println("      BLACK SCORE");
        if (kingUnderCheck[BLACK] > 0) {
            int x = checkingUnit[BLACK][0];
            int y = checkingUnit[BLACK][1];
            System.out.println("Black king under check by unit in " + x + ","+ y);
        }
        draw(sqControlCount[BLACK]);
        System.out.println("*************************");
        System.out.println(scoreAnalyzer[0].toString());
        System.out.println(scoreAnalyzer[0].overview());

        if (Math.abs(score[WHITE] - scoreAnalyzer[WHITE].totalScore) > 0.001) {
            System.out.println("!!!!! ERROR !!!!!! White score actually " + score[WHITE] + ", but analyzer has " + scoreAnalyzer[WHITE].totalScore);
        }
        if (Math.abs(score[BLACK] - scoreAnalyzer[BLACK].totalScore) > 0.001) {
            System.out.println("!!!!! ERROR !!!!!! Black score actually " + score[BLACK] + ", but analyzer has " + scoreAnalyzer[BLACK].totalScore);
        }
        if (kingUnderCheck[BLACK] != sqControlCount[WHITE][kingLives[BLACK][1]][kingLives[BLACK][2]]) {
            System.out.println("!!!!! ERROR !!!!!! Checking mismatch black");
            System.out.println("kingUnderCheck[BLACK] = " + kingUnderCheck[BLACK]);
            System.out.println("sqControl... = " + sqControlCount[BLACK][kingLives[BLACK][1]][kingLives[BLACK][2]]);
        }
        if (kingUnderCheck[WHITE] != sqControlCount[BLACK][kingLives[WHITE][1]][kingLives[WHITE][2]]) {
            System.out.println("!!!!! ERROR !!!!!! Checking mismatch white");
        }
    }



    class ScoreAnalyzer {

        LinkedHashMap<String, LinkedHashMap<String, Double>> elements;
        double totalScore;

        public ScoreAnalyzer() {
            elements = new LinkedHashMap<>();
            totalScore = 0;
        }

        void add(String category, String subcategory, Double value) {
        /* Create category if it doesn't exist */
            if (!elements.containsKey(category)) {
                elements.put(category, new LinkedHashMap<String,Double>());
            }
        /* Create subcategory if it doesn't exist */
            if (!elements.get(category).containsKey(subcategory)) {
                elements.get(category).put(subcategory, 0.0);
            }
        /* Modify value */
            totalScore += value;
            double combinedValue = value + elements.get(category).get(subcategory);
            elements.get(category).put(subcategory, combinedValue);
        }

        double get(String category) {
            double v = 0;

            if (category.equals("Bestcap")) {
                v += get("Naive value for best IMMEDIATE capture");
                v += get("Naive value for 2nd best capture");
                return v;
            }

            if (!elements.containsKey(category)) return 0;
            for (String sub : elements.get(category).keySet()) {
                v += elements.get(category).get(sub);
            }
            return v;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (String category : elements.keySet()) {
                sb.append("- " + category + "\n");
                double categoryValueSum = 0;
                for (Entry<String,Double> entry : elements.get(category).entrySet()) {
                    int pct = (int) (entry.getValue() * 100 / totalScore);
                    sb.append("  - " + entry.getKey() + " : " + entry.getValue() + " (" + pct + "%)\n");
                    categoryValueSum += entry.getValue();
                }
                int pct = (int) (categoryValueSum * 100 / totalScore);
                sb.append("  = " + categoryValueSum + " score from " + category + " in total (" + pct + "%)\n");
            }
            sb.append("= " + totalScore + " total score\n");
            return sb.toString();
        }

        public String overview() {
            StringBuilder sb = new StringBuilder();
            sb.append("***************************************************\n");
            sb.append("                      OVERVIEW\n");
            sb.append("***************************************************\n");
            sb.append(String.format("%0$19s", ""));
            if (sideToMove == WHITE) sb.append("White < Black\n");
            else                     sb.append("White > Black\n");
            String pad = String.format("%0$-20.20s", "TOTAL");
            String ws = String.format("%0$4.0f", scoreAnalyzer[WHITE].totalScore);
            String bs = String.format("%0$4.0f", scoreAnalyzer[BLACK].totalScore);
            String diff = String.format("%0$.0f", scoreAnalyzer[WHITE].totalScore - scoreAnalyzer[BLACK].totalScore);
            sb.append(pad + ws + " # " + bs + " ---> " + diff + "\n");
            appendWithFormattedData(sb, "Material");
            appendWithFormattedData(sb, "PST");
            appendWithFormattedData(sb, "Mobility");
            appendWithFormattedData(sb, "Hostile PIN");
            appendWithFormattedData(sb, "Check bonus");
            appendWithFormattedData(sb, "King+pawns");
            appendWithFormattedData(sb, "Near enemy king");
            appendWithFormattedData(sb, "Bestcap");
            appendWithFormattedData(sb, "Capturity bonus");
            appendWithFormattedData(sb, "Protection");
            appendWithFormattedData(sb, "Pawns");
            return sb.toString();
        }

        public void appendWithFormattedData(StringBuilder sb, String category) {
            String cat = String.format("%0$-20.20s", category); // pad right also
            String ws = String.format("%0$4.0f", scoreAnalyzer[WHITE].get(category));
            String mid = " # ";
            String bs = String.format("%0$.0f", scoreAnalyzer[BLACK].get(category));
            sb.append(cat + ws + mid + bs + "\n");
        }





















    }
    public static void draw(int[][] d) {
        int max = 1;
        for (int y=0; y<d.length; y++) {
            for (int x=0; x<d[0].length; x++) {
                max = Math.max(max, ("" + d[y][x]).length());
            }
        }
        System.out.print("  ");
        String format = "%" + (max+2) + "s";
        for (int x=0; x<d[0].length; x++) {
            System.out.print(String.format(format, x) + " ");
        }
        format = "%" + (max) + "s";
        System.out.println("");
        for (int y=0; y<d.length; y++) {
            System.out.print(y + " ");
            for (int x=0; x<d[0].length; x++) {
                System.out.print(" [" + String.format(format, (d[y][x])) + "]");
            }
            System.out.println("");
        }
    }
    public static void draw(double[][] d) {
        int max = 1;
        for (int y=0; y<d.length; y++) {
            for (int x=0; x<d[0].length; x++) {
                max = Math.max(max, ("" + formatDouble(d[y][x])).length());
            }
        }
        System.out.print("  ");
        String format = "%" + (max+2) + "s";
        for (int x=0; x<d[0].length; x++) {
            System.out.print(String.format(format, x) + " ");
        }
        format = "%" + (max) + "s";
        System.out.println("");
        for (int y=0; y<d.length; y++) {
            System.out.print(y + " ");
            for (int x=0; x<d[0].length; x++) {
                System.out.print(" [" + String.format(format, formatDouble(d[y][x])) + "]");
            }
            System.out.println("");
        }
    }

    public static String formatDouble(Double d) {
        return String.format("%.0f", d);
    }

    public void clearHash() {
        evaluatedPositions.clear();
    }
}