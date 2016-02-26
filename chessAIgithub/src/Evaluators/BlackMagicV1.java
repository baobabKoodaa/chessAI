
package Evaluators;


import Framework.Position;
import Evaluators.Evaluator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import org.omg.CORBA.INTERNAL;

public class BlackMagicV1 extends Evaluator {
    
    public static final int DEV = 1;
    public static final int PRODUCTION = 2;
    public static final int DEBUG = 3;
    public int STATE = DEV; // MUUTA STATIC FINAL INTIKS PRODUCTIONIIN KÄÄNTÄJÄOPTIMOINTIA VARTEN
    
    public static final int BLACK = 0;
    public static final int WHITE = 1;
    public static final int KING = 1;
    public static final int QUEEN = 2;
    public static final int ROOK = 3;
    public static final int KNIGHT = 5;
    public static final int PAWN = 6;
    
    
    public static final double PVrook = 500;
    public static final double PVknight = 320;
    public static final double PVqueen = 900;
    public static final double PVpawn = 100;
    public static final double PVking = 20000;
    
    public static final double CHECK_BONUS = 30;
    public static final double COST_OF_TEMPO = 30;
    public static final double HOSTILE_PIN_BONUS = 20;
    public static final double MOBILITY_BONUS = 10;
    public static final double CAPTURITY_BONUS = 0.01;
    
    HashMap<Long, Double> evaluatedPositions;
    Random rng;

    // Position specific
    int[][] b;
    double[] score;
    int sideToMove;
    int sideWaiting;
    int pieceCount;
    int[][][] pinned;
    double[][][] sqControlLowVal;
    int[][][] sqControlCount;
    int[] mobilityCount;
    double[][] capBest;
    double[] capAlt;
    int[][] kingLives;
    int[] pawns;
    
    // Set once
    double[] material;
    double[][][][][] pst;
    double[][] kingMobilityBonus;
    double[][] kingPawnShieldBonus;
    double[][] enemyControlNearKingPenalty;
    double[][] pawnUpgradePotentialBonus;
    boolean[][] isEnemyRookOrQueen;
    boolean[][] isFriendly;
    Double[][] pieceValue;
    int[][][] neighboringSquares;
    List<Square>[][] knightMoveLists;
    
    // Debugging
    ScoreAnalyzer[] scoreAnalyzer;
    
    public BlackMagicV1() {
        evaluatedPositions = new HashMap<>();
        rng = new Random();
        generateTrivialLookupTables();
        generateKingMobilityBonus();
        generateEnemyControlNearKingPenalty();
        generateKingPawnShieldBonus();
        generatePawnUpgradePotentialBonuses();
        generateNeighboringSquaresLists();
        generateKnightMoveLists();
        generatePieceSquareTables();
    }
    
    public double eval(Position p) {
        double v = ev(p);
        if (STATE == DEBUG) analyzeScores();
        if (STATE == PRODUCTION) v += rng.nextInt(10);
        return v;
    }
    
    public double ev(Position p) {
        b = p.board;
        
        if (STATE == DEBUG) {
            scoreAnalyzer = new ScoreAnalyzer[2];
            scoreAnalyzer[WHITE] = new ScoreAnalyzer();
            scoreAnalyzer[BLACK] = new ScoreAnalyzer();
        }
        
        pieceCount = 0;
        kingLives = new int[2][3];
        long hash = hashPositionEtc(p); /** Hash position, find kings, calculate pieceCount */
        if (STATE != DEBUG && evaluatedPositions.containsKey(hash)) return evaluatedPositions.get(hash);
        
        /* checkmate? */
        if (kingLives[WHITE][0] == 0) return -1e9;
        if (kingLives[BLACK][0] == 0) return 1e9;
        
        /* initialize more position specific static variables */
        score = new double[2];
        initializeSqControl();
        pinned = new int[6][6][3];
        mobilityCount = new int[2];
        capBest = new double[2][2];
        capAlt = new double[2];
        pawns = new int[2];
        sideToMove = (p.whiteToMove ? WHITE : BLACK);
        sideWaiting = (sideToMove+1)%2;
        

        /** * * MAGIC * * */
        preProcessSquares();
        /** * * MAGIC * * */
        
        
        /* checkmate in 1 ply? */
        if (kingIsInCheck(sideWaiting)) {
            if (sideWaiting == WHITE) return -1e9;
            else                      return 1e9;
        }
        
        
        /** * * MAGIC * * */
        postProcessKing(WHITE, Position.WKing);
        postProcessKing(BLACK, Position.BKing);
        postProcessPawns(WHITE);
        postProcessPawns(BLACK);
        scoreMobilityAndCaptures();
        /** * * MAGIC * * */
        
        double v = score[WHITE] - score[BLACK];
        double ylivoimaKerroin = 1 + Math.abs(v) / (score[WHITE] + score[BLACK]);
        v *= ylivoimaKerroin; //TODO: Mieti onko tää paras mahdollinen tapa toteuttaa tää juttu?
                              // esim laske et tää toimii jollain vaihdolla ylivoimatilantees
        
        if (STATE == DEBUG) {
            System.out.println("Original diff: " + (score[WHITE]-score[BLACK]) + ", modified diff: " + v);
        }
        
        if (STATE != DEBUG) {
            evaluatedPositions.put(hash, v);
        }
        return v;
    }
    
    /** Hash position, find kings, calculate pieceCount. */
    public long hashPositionEtc(Position p) {
        long hash = (p.whiteToMove ? 2 : 1);
        long A = 31L;
        for (int y=0; y<6; y++) {
            for (int x=0; x<6; x++) {
                hash *= A; /* modulates by long overflow */
                hash += (b[x][y]+1);
                switch (b[x][y]) {
                    case Position.Empty:
                        continue; /* we are about to increment pieceCount */
                    case Position.WKing:
                        markKingLocation(WHITE, x, y);
                        break;
                    case Position.BKing:
                        markKingLocation(BLACK, x, y);
                        break;
                    default:
                        break;  
                }
                pieceCount++;
            }
        }
        hash *= A;
        return hash;
    }
    
    public void preProcessSquares() {
        preProcessKing(WHITE, kingLives[WHITE][1], kingLives[WHITE][2]);
        preProcessKing(BLACK, kingLives[BLACK][1], kingLives[BLACK][2]);
        for (int x = 0; x < b.length; x++) {
            for (int y = 0; y < b[x].length; y++) {
                switch (b[x][y]) {
                    case Position.Empty: continue;
                    case Position.WKing:    /*preProcessKing(WHITE, x, y)*/;break; /* preProcessed earlier */
                    case Position.WQueen:   preProcessQueen(WHITE, x, y);break;
                    case Position.WKnight:  preProcessKnight(WHITE, x, y);break;
                    case Position.WRook:    preProcessRook(WHITE, x, y);break;
                    case Position.WPawn:    preProcessPawn(WHITE, x, y);break;

                    case Position.BKing:    /*preProcessKing(BLACK, x, y)*/;break; /* preProcessed earlier */
                    case Position.BQueen:   preProcessQueen(BLACK, x, y);break; 
                    case Position.BKnight:  preProcessKnight(BLACK, x, y);break;    
                    case Position.BRook:    preProcessRook(BLACK, x, y);break;
                    case Position.BPawn:    preProcessPawn(BLACK, x, y);break;    
                    default:                ; break;
                }
            }
        }
    }
    
    public void scoreMobilityAndCaptures() {
        score[WHITE] += MOBILITY_BONUS * mobilityCount[WHITE];
        score[BLACK] += MOBILITY_BONUS * mobilityCount[BLACK];
        if (STATE == DEBUG) {
            scoreAnalyzer[WHITE].add("Mobility bonus", "Number of possible moves " + mobilityCount[WHITE], MOBILITY_BONUS * mobilityCount[WHITE]);
            scoreAnalyzer[BLACK].add("Mobility bonus", "Number of possible moves " + mobilityCount[BLACK], MOBILITY_BONUS * mobilityCount[BLACK]);
        }
        
        
        /** * * MAGIC * * */
        analyzePotentialCaptures();
        /** * * MAGIC * * */
        
        
        if (!kingIsInCheck(sideToMove)) { 
            /** Award sideToMove the best naive capture value
             *  Since we are not under check, this is never an illegal move
             * (pins were pruned out earlier) */
            score[sideToMove] += capBest[sideToMove][0];
            if (STATE == DEBUG) {
                scoreAnalyzer[sideToMove].add("Naive value for best IMMEDIATE capture", "--", capBest[sideToMove][0]);
            }
        } else {
            /* sideToMove is in check, so penalize with COST_OF_TEMPO and
            *   award the 2nd best naive capture value rather than the best */
            double v = capBest[sideToMove][1] - COST_OF_TEMPO;
            score[sideToMove] += v;
            if (STATE == DEBUG) {
                scoreAnalyzer[sideToMove].add("Naive value for 2nd best capture", "--", v);
            }
        }
        
        /* sideWaiting: gets score for 2nd best naive captureValue,
         *              (some of these moves may be illegal, but looking
         *               ahead 2 plies this is a fine approximation) */
        score[sideWaiting] += capBest[sideWaiting][1];
        if (STATE == DEBUG) {
            scoreAnalyzer[sideWaiting].add("Naive value for 2nd best capture", "--", capBest[sideWaiting][1]);
        }
        /* Note: if sideToMove has en pris capture of sideWaiting's unit X
         *       and X is threatening 2 valuable units of sideToMove, then 
         *       sideWaiting will incorrectly gain score for his 2nd most
         *       valuable potential capture, which will never be realized. */
        
        
        
        /** capAlt has the sum of [unit values multiplied by number of threats to those units] */
        score[WHITE] += CAPTURITY_BONUS * capAlt[WHITE];
        score[BLACK] += CAPTURITY_BONUS * capAlt[BLACK];
        if (STATE == DEBUG) {
            scoreAnalyzer[WHITE].add("Capturity bonus", "capAlt " + capAlt[WHITE], CAPTURITY_BONUS * capAlt[WHITE]);
            scoreAnalyzer[BLACK].add("Capturity bonus", "capAlt " + capAlt[BLACK], CAPTURITY_BONUS * capAlt[BLACK]);
        }

    }
   
    public void analyzePotentialCaptures() {
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
                            score[attackColor] += CHECK_BONUS * n * n;
                            if (STATE == DEBUG) {
                                scoreAnalyzer[attackColor].add("Check bonus", "Number of checking units = " + n, CHECK_BONUS * n * n);
                            }
                        }
                        continue;
                    case Position.BKing:    
                        defendColor = BLACK;
                        attackColor = WHITE;
                        n = sqControlCount[attackColor][x][y];
                        if (n > 0) {
                            score[attackColor] += CHECK_BONUS * n * n;
                            if (STATE == DEBUG) {
                                scoreAnalyzer[attackColor].add("Check bonus", "Number of checking units = " + n, CHECK_BONUS * n * n);
                            }
                        }
                        continue;
                        
                        
                    case Position.WQueen:
                        captureValue = PVqueen;
                        defendColor = WHITE;
                        attackColor = BLACK;
                        break;
                    case Position.WKnight:
                        captureValue = PVknight;
                        defendColor = WHITE;
                        attackColor = BLACK;
                        break;
                    case Position.WRook:
                        captureValue = PVrook;
                        defendColor = WHITE;
                        attackColor = BLACK;
                        break;
                    case Position.WPawn:
                        captureValue = PVpawn;
                        defendColor = WHITE;
                        attackColor = BLACK;
                        break;
                        
                    case Position.BQueen:
                        captureValue = PVqueen;
                        defendColor = BLACK;
                        attackColor = WHITE;
                        break;
                    case Position.BKnight:
                        captureValue = PVknight;
                        defendColor = BLACK;
                        attackColor = WHITE;
                        break;
                    case Position.BRook:
                        captureValue = PVrook;
                        defendColor = BLACK;
                        attackColor = WHITE;
                        break;
                    case Position.BPawn:
                        captureValue = PVpawn;
                        defendColor = BLACK;
                        attackColor = WHITE;
                        break;

                    default:break;
                }
                /* Is this square threatened? */
                if (sqControlCount[attackColor][x][y] == 0) continue;
                double naiveValue = captureValue;
                
                /* Is it defended? */
                if (sqControlCount[defendColor][x][y] > 0) {
                    /* Naively assume it would be a single trade if we attacked with our weakest unit */
                    naiveValue -= sqControlLowVal[attackColor][x][y];
                } else {
                    /* Undefended piece. We can capture it, but we lose tempo. */
                    naiveValue -= COST_OF_TEMPO;
                }
                /** Remember top 2 capture targets */
                if (naiveValue > capBest[attackColor][1]) {
                    if (naiveValue > capBest[attackColor][0]) {
                        capBest[attackColor][1] = capBest[attackColor][0];
                        capBest[attackColor][0] = naiveValue;
                    } else {
                        capBest[attackColor][1] = naiveValue;
                    }
                }
                
                /*      count this opportunity even if naiveValue < 0         */
                capAlt[attackColor] += sqControlCount[attackColor][x][y] * captureValue;
                
            }
        }
    }
    
    public void addControlAndMobility(int color, double unitValue, int x, int y) {
        sqControlCount[color][x][y]++;
        sqControlLowVal[color][x][y] = Math.min(unitValue, sqControlLowVal[color][x][y]);
        mobilityCount[color]++;
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
    

    private void generateKingMobilityBonus() {
        kingMobilityBonus = new double[9][25];
        kingMobilityBonus[0][1] = 0;
        kingMobilityBonus[1][1] = 100;
        kingMobilityBonus[2][1] = 110;
        kingMobilityBonus[3][1] = 115;
        for (int i=4; i<=8; i++) kingMobilityBonus[i][1] = 120;
        for (int i=0; i<=8; i++) {
            for (int j=2; j<=24; j++) {
                /** Less mobility bonus towards earlygame */
                kingMobilityBonus[i][j] = kingMobilityBonus[i][j-1] * 0.95;
            }
        }
        /* near-zero penalty for early game king immobility */
        kingMobilityBonus[2][24] = kingMobilityBonus[3][24] * 0.99;
        kingMobilityBonus[1][24] = kingMobilityBonus[2][24] * 0.99;
        kingMobilityBonus[0][24] = kingMobilityBonus[1][24] * 0.99; 
        
//        for (int i=0; i<kingMobilityBonus.length; i++) {
//            System.out.println(Arrays.toString(kingMobilityBonus[i]));
//        }
    }
    
    /** 2 pawns is ideal, 1 pawn is ok, no pawns considerably worse
     *  Less bonus towards endgame.
     */
    private void generateKingPawnShieldBonus() {
        kingPawnShieldBonus = new double[9][25]; // [numberOfAdjacentPawns][pieceCount]
        kingPawnShieldBonus[1][8] = 2;
        kingPawnShieldBonus[2][8] = 3;
        for (int j=3; j<=8; j++) kingPawnShieldBonus[j][8] = 0.3;
        for (int i=9; i<=24; i++) {
            for (int j=1; j<=8; j++) {
                kingPawnShieldBonus[j][i] = kingPawnShieldBonus[j][i-1] * 1.1;
            }
        }
        for (int i=1; i<8; i++) {
            kingPawnShieldBonus[i][7] = 1;
            kingPawnShieldBonus[i][6] = 1;
            kingPawnShieldBonus[i][5] = 1;
            kingPawnShieldBonus[i][4] = 1;
            kingPawnShieldBonus[i][3] = 1;
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
    
    /** Called during hashing */
    public void markKingLocation(int color, int x, int y) {
        kingLives[color][0] = 1337; // [x] king lives
        kingLives[color][1] = x;
        kingLives[color][2] = y;
    }

    public void preProcessKing(int color, int ax, int ay) {
        double pstValue = pst[color][KING][ax][ay][pieceCount];
        score[color] += pstValue;
        if (STATE == DEBUG) scoreAnalyzer[color].add("pst", "KING", pstValue);
        int enemyColor = (color+1)%2;
        
        /** King offers some protection/threat to neighboring squares */
        int i=1; 
        while (i<neighboringSquares[ax][ay][0]) {
            int x = neighboringSquares[ax][ay][i++];
            int y = neighboringSquares[ax][ay][i++];
            sqControlCount[color][x][y]++;
            sqControlLowVal[color][x][y] = Math.min(PVking, sqControlLowVal[color][x][y]);
        }
        
        /** Mark which units are pinned
          *     Friendly unit is pinned -> It has fewer moves
          *     Hostile unit is pinned -> bonus for enemy       */
        
        int sx = -1;
        int sy = -1;
        int x = ax-1;
        int y = ay;
        for (; x>=0; x--) {
            if (b[x][y] == Position.Empty) continue;
            int unit = b[x][y];
            if (sx < 0) {
                /** Later find out if this unit is pinned */
                sx = x;
                sy = y;
            } else {
                if (!isEnemyRookOrQueen[color][unit]) break;
                int pinnedUnit = b[sx][sy]; /* Now we know */
                if (isFriendly[color][pinnedUnit]) {
                    pinned[sx][sy][0] = 1337; // [x] pinned
                    pinned[sx][sy][1] = x; // x of threat
                    pinned[sx][sy][2] = y; // y of threat
                } else {
                    score[enemyColor] += HOSTILE_PIN_BONUS;
                    if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("HOSTILE_PIN_BONUS", "Unit at " + sx+","+sy, HOSTILE_PIN_BONUS);
                }
                break;
            }
        }
        
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
            } else {
                if (!isEnemyRookOrQueen[color][unit]) break;
                int pinnedUnit = b[sx][sy];
                if (isFriendly[color][pinnedUnit]) {
                    pinned[sx][sy][0] = 1337;
                    pinned[sx][sy][1] = x;
                    pinned[sx][sy][2] = y;
                } else {
                    score[enemyColor] += HOSTILE_PIN_BONUS;
                    if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("HOSTILE_PIN_BONUS", "Unit at " + sx+","+sy, HOSTILE_PIN_BONUS);
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
            } else {
                if (!isEnemyRookOrQueen[color][unit]) break;
                int pinnedUnit = b[sx][sy];
                if (isFriendly[color][pinnedUnit]) {
                    pinned[sx][sy][0] = 1337;
                    pinned[sx][sy][1] = x;
                    pinned[sx][sy][2] = y;
                } else {
                    score[enemyColor] += HOSTILE_PIN_BONUS;
                    if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("HOSTILE_PIN_BONUS", "Unit at " + sx+","+sy, HOSTILE_PIN_BONUS);
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
            } else {
                if (!isEnemyRookOrQueen[color][unit]) break;
                int pinnedUnit = b[sx][sy];
                if (isFriendly[color][pinnedUnit]) {
                    pinned[sx][sy][0] = 1337;
                    pinned[sx][sy][1] = x;
                    pinned[sx][sy][2] = y;
                } else {
                    score[enemyColor] += HOSTILE_PIN_BONUS;
                    if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("HOSTILE_PIN_BONUS", "Unit at " + sx+","+sy, HOSTILE_PIN_BONUS);
                }
                break;
            }
        }
        
        /** TODO: Diagonal pin checks */
    }
        
    /** This method doesn't check if king is alive
     *  It may be checking an empty [0,0] square. */
    public boolean kingIsInCheck(int color) {
        int enemyColor = (color+1)%2;
        int x = kingLives[color][1];
        int y = kingLives[color][2];
        return (sqControlCount[enemyColor][x][y] > 0);
    }
    
    /** TODO: document this */
    public void postProcessKing(int color, int unitId) {
        int enemyColor = (color+1)%2;
        int friendlyPawn = (color == WHITE ? Position.WPawn : Position.BPawn);
        int ax = kingLives[color][1];
        int ay = kingLives[color][2];
        
        int availableMoves = 0;
        int pawnCount = 0;
        for (int i=1; i<neighboringSquares[ax][ay][0];) {
            int x = neighboringSquares[ax][ay][i++];
            int y = neighboringSquares[ax][ay][i++];
            int unit = b[x][y];
            
            if (unit == friendlyPawn) pawnCount++;
            if (sqControlCount[enemyColor][x][y] > 0) {
                /** Penalty for enemy control in king adjacent square */
                int multiplier = sqControlCount[enemyColor][x][y];
                double penalty = enemyControlNearKingPenalty[multiplier][pieceCount];
                score[enemyColor] += penalty;
                if (STATE == DEBUG) scoreAnalyzer[enemyColor].add("Near enemy king", "Control over sq " + x+","+y, penalty);
                continue; /* King is unable to move into this sq */
            }
            if (unit == Position.Empty) {
                availableMoves++;
            } else if (!isFriendly[color][unit]) {
                /** King can capture an undefended piece, will be scored later in scoreMobilityAndCaptures */
                availableMoves++;
            }
            /** No mobility to square occupied by friendly */
        }

        mobilityCount[color] += availableMoves;
        score[color] += kingMobilityBonus[availableMoves][pieceCount];
        score[color] += kingPawnShieldBonus[pawnCount][pieceCount];
        if (STATE == DEBUG) {
            scoreAnalyzer[color].add("King mobility", availableMoves + " moves available", kingMobilityBonus[availableMoves][pieceCount]);
            scoreAnalyzer[color].add("King+pawns", pawnCount + " adjacent firendly pawns", kingPawnShieldBonus[pawnCount][pieceCount]);
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="rook/queen">
    
    private void preProcessRook(int color, int ax, int ay) {
        score[color] += PVrook;
        score[color] += pst[color][ROOK][ax][ay][pieceCount];
        if (STATE == DEBUG) {
            scoreAnalyzer[color].add("Material", "ROOK", PVrook);
            scoreAnalyzer[color].add("pst", "ROOK", pst[color][ROOK][ax][ay][pieceCount]);
        }
        
        if (pinned[ax][ay][0] != 0) {
            int x = pinned[ax][ay][1];
            int y = pinned[ax][ay][2];
            /* Pinned between enemy at [x,y] and king. Can we capture it? */
            if (x == ax || y == ay) addControlAndMobility(color, PVrook, x, y);
            return; /* Note: additional allowed moves may exist... */
        }
        addControlsHorizontalVertical(color, PVrook, ax, ay);
    }
    
    private void preProcessQueen(int color, int ax, int ay) {
        score[color] += PVqueen;
        score[color] += pst[color][QUEEN][ax][ay][pieceCount];
        if (STATE == DEBUG) {
            scoreAnalyzer[color].add("Material", "QUEEN", PVqueen);
            scoreAnalyzer[color].add("pst", "QUEEN", pst[color][QUEEN][ax][ay][pieceCount]);
        }
        
        if (pinned[ax][ay][0] != 0) {
            int x = pinned[ax][ay][1];
            int y = pinned[ax][ay][2];
            /* Pinned between enemy at [x,y] and king. Queen can capture it. */
            addControlAndMobility(color, PVqueen, x, y);
            return; /* Note: additional allowed moves may exist... */
        }
        addControlsHorizontalVertical(color, PVqueen, ax, ay);
        addControlsDiagonal(color, PVqueen, ax, ay);
    }
    
    private void addControlsHorizontalVertical(int color, double unitValue, int ax, int ay) {
        for (int x=ax-1; x>=0; x--) {
            addControlAndMobility(color, unitValue, x, ay);
            if (b[x][ay] != Position.Empty) break;
        }
        for (int x=ax+1; x<=5; x++) {
            addControlAndMobility(color, unitValue, x, ay);
            if (b[x][ay] != Position.Empty) break;
        }
        for (int y=ay-1; y>=0; y--) {
            addControlAndMobility(color, unitValue, ax, y);
            if (b[ax][y] != Position.Empty) break;
        }
        for (int y=ay+1; y<=5; y++) {
            addControlAndMobility(color, unitValue, ax, y);
            if (b[ax][y] != Position.Empty) break;
        }
    }
    
    private void addControlsDiagonal(int color, double unitValue, int ax, int ay) {
        int x = ax+1;
        int y = ay+1;
        while (x <= 5 && y <= 5) {
            addControlAndMobility(color, unitValue, x, y);
            if (b[x][y] != Position.Empty) break;
            x++;
            y++;
        }
        x = ax-1;
        y = ay-1;
        while (x >= 0 && y >= 0) {
            addControlAndMobility(color, unitValue, x, y);
            if (b[x][y] != Position.Empty) break;
            x--;
            y--;
        }
        x = ax+1;
        y = ay-1;
        while (x <= 5 && y >= 0) {
            addControlAndMobility(color, unitValue, x, y);
            if (b[x][y] != Position.Empty) break;
            x++;
            y--;
        }
        x = ax-1;
        y = ay+1;
        while (x >= 0 && y <= 5) {
            addControlAndMobility(color, unitValue, x, y);
            if (b[x][y] != Position.Empty) break;
            x--;
            y++;
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="knight">
    
    private void preProcessKnight(int color, int ax, int ay) {
        score[color] += PVknight;
        score[color] += pst[color][KNIGHT][ax][ay][pieceCount];
        if (STATE == DEBUG) {
            scoreAnalyzer[color].add("Material", "KNIGHT", PVknight);
            scoreAnalyzer[color].add("pst", "KNIGHT", pst[color][KNIGHT][ax][ay][pieceCount]);
        }
        
        if (pinned[ax][ay][0] != 0) return; /* Assume no eligible moves when pinned */
        for (Square move : knightMoveLists[ax][ay]) {
            int x = move.x;
            int y = move.y;
            addControlAndMobility(color, PVknight, x, y);
        }
    }
    
    private void generateKnightMoveLists() {
        knightMoveLists = new ArrayList[6][6];
        for (int x=0; x<6; x++) {
            for (int y=0; y<6; y++) {
                ArrayList<Square> list = new ArrayList<>(8);
                if (x-1 >= 0) {
                    if (y-2 >= 0) list.add(new Square(x-1, y-2));
                    if (y+2 <= 5) list.add(new Square(x-1, y+2));
                    if (x-2 >= 0) {
                        if (y-1 >= 0) list.add(new Square(x-2, y-1));
                        if (y+1 <= 5) list.add(new Square(x-2, y+1));
                    }
                }
                if (x+1 <= 5) {
                    if (y-2 >= 0) list.add(new Square(x+1, y-2));
                    if (y+2 <= 5) list.add(new Square(x+1, y+2));
                    if (x+2 <= 5) {
                        if (y-1 >= 0) list.add(new Square(x+2, y-1));
                        if (y+1 <= 5) list.add(new Square(x+2, y+1));
                    }
                }
                knightMoveLists[x][y] = list;
            }
        }
    }
        
    // </editor-fold>

    private void preProcessPawn(int color, int ax, int ay) {
        score[color] += PVpawn;
        score[color] += pst[color][PAWN][ax][ay][pieceCount];
        if (STATE == DEBUG) {
            scoreAnalyzer[color].add("Material", "PAWN", PVpawn);
            scoreAnalyzer[color].add("pst", "PAWN", pst[color][PAWN][ax][ay][pieceCount]);
        }
        
        /** TODO: PAWN HASH */

        if (pinned[ax][ay][0] != 0) return; /** TODO: eligible moves when pinned */
        
        int ahead = (color == BLACK ? -1 : 1);
        /** Move ahead possible? */
        if (b[ax][ay+ahead] == Position.Empty) {
            mobilityCount[color]++;
        }
        /** Squares threatened by this pawn */
        /** Lowest denominated unit, so we can just overwrite lowVal */
        if (ax-1 >= 0) {
            sqControlCount[color][ax-1][ay+ahead]++;
            sqControlLowVal[color][ax-1][ay+ahead] = PVpawn;
        }
        if (ax+1 <= 5) {
            sqControlCount[color][ax+1][ay+ahead]++;
            sqControlLowVal[color][ax+1][ay+ahead] = PVpawn;
        }

    }
    
    /** Endgame incentive to upgrade pawns + penalty for same lane pawns. */
    private void postProcessPawns(int color) {
        
        //endgame; korvaa pieceSquareTableilla sovitettuna pieceCountilla
//            for (Square pawn : pawns[color]) {
//                v += pawnUpgradePotentialBonus[color][pawn.y];
//            }
//            
//        boolean[] laneAlreadyHasAPawn = new boolean[6];
//        for (Square pawn : pawns[color]) {
//            if (laneAlreadyHasAPawn[pawn.x]) v -= 30;
//            laneAlreadyHasAPawn[pawn.x] = true;
//        }
    }
    
    /** Reference: [COLOR][UNIT][x][y][pieceCount]. */
    private void generatePieceSquareTables() {
        pst = new double[2][7][6][6][25];
    }
    
    private void generateControlBonus() {
//        controlBonus = new double[2][6][6];
//        // Vihunpuolella-bonukset
//        for (int x=0; x<=5; x++) controlBonus[WHITE][x][0] = 1;
//        for (int x=0; x<=5; x++) controlBonus[WHITE][x][1] = 1.05;
//        for (int x=0; x<=5; x++) controlBonus[WHITE][x][2] = 1.1;
//        for (int x=0; x<=5; x++) controlBonus[WHITE][x][3] = 1.2;
//        for (int x=0; x<=5; x++) controlBonus[WHITE][x][4] = 1.25;
//        for (int x=0; x<=5; x++) controlBonus[WHITE][x][5] = 1.15;
//        for (int x=0; x<=5; x++) controlBonus[BLACK][x][5] = 1;
//        for (int x=0; x<=5; x++) controlBonus[BLACK][x][4] = 1.05;
//        for (int x=0; x<=5; x++) controlBonus[BLACK][x][3] = 1.1;
//        for (int x=0; x<=5; x++) controlBonus[BLACK][x][2] = 1.2;
//        for (int x=0; x<=5; x++) controlBonus[BLACK][x][1] = 1.25;
//        for (int x=0; x<=5; x++) controlBonus[BLACK][x][0] = 1.15;
//        // Keskustabonukset
//        controlBonus[WHITE][2][2] += 0.2;
//        controlBonus[WHITE][3][2] += 0.2;
//        controlBonus[WHITE][3][3] += 0.2;
//        controlBonus[WHITE][2][3] += 0.2;
//        controlBonus[BLACK][2][2] += 0.2;
//        controlBonus[BLACK][3][2] += 0.2;
//        controlBonus[BLACK][3][3] += 0.2;
//        controlBonus[BLACK][2][3] += 0.2;
//        // Suburb bonukset
//        controlBonus[WHITE][1][1] += 0.1;
//        controlBonus[WHITE][1][2] += 0.1;
//        controlBonus[WHITE][1][3] += 0.1;
//        controlBonus[WHITE][1][4] += 0.1;
//        controlBonus[WHITE][2][4] += 0.1;
//        controlBonus[WHITE][3][4] += 0.1;
//        controlBonus[WHITE][4][4] += 0.1;
//        controlBonus[WHITE][4][3] += 0.1;
//        controlBonus[WHITE][4][2] += 0.1;
//        controlBonus[WHITE][4][1] += 0.1;
//        controlBonus[WHITE][3][1] += 0.1;
//        controlBonus[WHITE][2][1] += 0.1;
//        controlBonus[BLACK][1][1] += 0.1;
//        controlBonus[BLACK][1][2] += 0.1;
//        controlBonus[BLACK][1][3] += 0.1;
//        controlBonus[BLACK][1][4] += 0.1;
//        controlBonus[BLACK][2][4] += 0.1;
//        controlBonus[BLACK][3][4] += 0.1;
//        controlBonus[BLACK][4][4] += 0.1;
//        controlBonus[BLACK][4][3] += 0.1;
//        controlBonus[BLACK][4][2] += 0.1;
//        controlBonus[BLACK][4][1] += 0.1;
//        controlBonus[BLACK][3][1] += 0.1;
//        controlBonus[BLACK][2][1] += 0.1;
    }

    private void generatePawnUpgradePotentialBonuses() {
        pawnUpgradePotentialBonus = new double[2][6];
        pawnUpgradePotentialBonus[WHITE][2] = 10;
        pawnUpgradePotentialBonus[WHITE][3] = 40;
        pawnUpgradePotentialBonus[WHITE][4] = 100;
        
        pawnUpgradePotentialBonus[BLACK][3] = 10;
        pawnUpgradePotentialBonus[BLACK][2] = 40;
        pawnUpgradePotentialBonus[BLACK][1] = 100;
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
        
        material = new double[13];
        material[Position.WKing] = PVking;
        material[Position.WQueen] = PVqueen;
        material[Position.WKnight] = PVknight;
        material[Position.WRook] = PVrook;
        material[Position.WPawn] = PVpawn;
        material[Position.BKing] = PVking;
        material[Position.BQueen] = PVqueen;
        material[Position.BKnight] = PVknight;
        material[Position.BRook] = PVrook;
        material[Position.BPawn] = PVpawn;
    }


    
    
    
    
    

    public static void draw(double[][] d) {
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
    
    void analyzeScores() {
        if (score[WHITE] != scoreAnalyzer[WHITE].totalScore) {
            System.out.println("!!!!! ERROR !!!!!! White score actually " + score[WHITE] + ", but analyzer has " + scoreAnalyzer[WHITE].totalScore);
        }
        if (score[BLACK] != scoreAnalyzer[BLACK].totalScore) {
            System.out.println("!!!!! ERROR !!!!!! Black score actually " + score[BLACK] + ", but analyzer has " + scoreAnalyzer[BLACK].totalScore);
        }
        System.out.println("Side to move: " + sideToMove);
        System.out.println("*************************");
        System.out.println("      WHITE SCORE");
        System.out.println("*************************");
        System.out.println(scoreAnalyzer[1].toString());
        System.out.println("*************************");
        System.out.println("      BLACK SCORE");
        System.out.println("*************************");
        System.out.println(scoreAnalyzer[0].toString());
        System.out.println(scoreAnalyzer[0].overview());
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
            sb.append("White # Black\n");
            String pad = String.format("%0$-20.20s", "TOTAL");
            String ws = String.format("%0$4.0f", scoreAnalyzer[WHITE].totalScore);
            String bs = String.format("%0$4.0f", scoreAnalyzer[BLACK].totalScore);
            String diff = String.format("%0$.0f", scoreAnalyzer[WHITE].totalScore - scoreAnalyzer[BLACK].totalScore);
            sb.append(pad + ws + " # " + bs + " ---> " + diff + "\n");
            appendWithFormattedData(sb, "Material");
            appendWithFormattedData(sb, "Mobility bonus");
            appendWithFormattedData(sb, "King mobility");
            appendWithFormattedData(sb, "King+pawns");
            appendWithFormattedData(sb, "Near enemy king");
            appendWithFormattedData(sb, "Bestcap");
            appendWithFormattedData(sb, "Capturity bonus");

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

    private class Square {
        int x;
        int y;

        public Square(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "      x = " + x + " , y = " + y;
        }
    }
}